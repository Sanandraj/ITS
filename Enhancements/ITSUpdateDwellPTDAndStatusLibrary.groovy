    /*
     * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
     *
     */

    import com.navis.argo.ContextHelper
    import com.navis.argo.business.api.ArgoUtils
    import com.navis.argo.business.api.GroovyApi
    import com.navis.argo.business.extract.ChargeableUnitEvent
    import com.navis.argo.business.services.IServiceExtract
    import com.navis.billing.BillingEntity
    import com.navis.billing.BillingField
    import com.navis.billing.business.atoms.InvoiceStatusEnum
    import com.navis.billing.business.model.ExtractHibernateApi
    import com.navis.billing.business.model.Invoice
    import com.navis.billing.business.model.InvoiceItem
    import com.navis.external.framework.entity.EEntityView
    import com.navis.framework.business.Roastery
    import com.navis.framework.portal.Ordering
    import com.navis.framework.portal.QueryUtils
    import com.navis.framework.portal.query.Disjunction
    import com.navis.framework.portal.query.DomainQuery
    import com.navis.framework.portal.query.Junction
    import com.navis.framework.portal.query.PredicateFactory
    import org.apache.commons.collections.CollectionUtils
    import org.apache.log4j.Level
    import org.apache.log4j.Logger
    import org.hibernate.classic.Session

    /**
     * @Author: mailto:annalakshmig@weservetech.com, AnnaLakshmi G; Date: 12/DEC/2022
     *
     * Requirements : IP-407,7-10 Requirements for Waiver or Guarantee Extended Dwell Fee.
     *                IP-14,3-6 Extended Dwell Fee.  Details in section 3.6.
     *
     * @Inclusion Location : Incorporated as a code extension of the type LIBRARY
     *
     *  Load Code Extension to N4:
     *  1. Go to Administration --> System --> Code Extensions
     *  2. Click Add (+)
     *  3. Enter the values as below:
     *     Code Extension Name:ITSUpdateDwellPTDAndStatusLibrary
     *     Code Extension Type:LIBRARY
     *     Groovy Code: Copy and paste the contents of groovy code.
     *  4. Click Save button
     *
     *
     *  S.No    Modified Date        Modified By       Jira      Description
     */

    class ITSUpdateDwellPTDAndStatusLibrary extends GroovyApi {

        void updateExtendedDwellStatusAndPTD(EEntityView entity, Boolean isUpdatePtd,String finalInvNbr) {
            //LOG.setLevel(Level.DEBUG)
            LOG.debug("ITSUpdateDwellPTDAndStatusLibrary starts");
            Serializable invoiceGkey = (Serializable) entity.getField(BillingField.INVOICE_GKEY)

            Invoice invoice = Invoice.hydrate(invoiceGkey)
            Set<InvoiceItem> invoiceItems = invoice.getInvoiceInvoiceItems();

            Set<Serializable> unitDwellEventCUEGkey = new HashSet<>()
            Set<Serializable> vacisAndTailGateExamsCUEGkey = new HashSet<>()
            Set<Serializable> unitCUEGkey = new HashSet<>()


            for (InvoiceItem invItem : invoiceItems) {
                if (dwell_event.equals(invItem.getItemEventTypeId())) {
                    unitDwellEventCUEGkey.add(invItem.getItemServiceExtractGkey());
                }else if (vacisInspectionRequired.equals(invItem.getItemEventTypeId()) || tailGateExam.equals(invItem.getItemEventTypeId())) {
                    vacisAndTailGateExamsCUEGkey.add(invItem.getItemServiceExtractGkey())
                }
                if(invItem != null) {
                    unitCUEGkey.add(invItem?.getItemServiceExtractGkey())
                }
            }

            LOG.debug("unitDwellEventCUEGkey : " + unitDwellEventCUEGkey);
            if (!CollectionUtils.isEmpty(unitDwellEventCUEGkey)) {
                Map<Serializable, Date> dwellPTDMap = createDwellPTDMap(unitDwellEventCUEGkey, invoice);

                updateDwellPTDAndStatus(dwellPTDMap, isUpdatePtd)
            }
            if (!CollectionUtils.isEmpty(vacisAndTailGateExamsCUEGkey)) {
                updateVacisAndTailGateExamPTD(vacisAndTailGateExamsCUEGkey)
            }
            if (!CollectionUtils.isEmpty(unitCUEGkey)) {
                updateSoCUEStatus(unitCUEGkey,finalInvNbr)
            }

            LOG.debug("updateExtractPTDAndStatus end")

        }


        Map<Serializable, Date> createDwellPTDMap(Set<Serializable> dwellGkeys, Invoice inInvoice) {
            Map<Serializable, Date> dwellPTDMap = new HashMap<Serializable, Date>();

            for (Serializable eventExtractGkey : dwellGkeys) {
                Date lastDwellPTD = findLastDwellPTD(eventExtractGkey, inInvoice);
                if (lastDwellPTD != null) {
                    dwellPTDMap.put(eventExtractGkey, lastDwellPTD);
                } else {
                    dwellPTDMap.put(eventExtractGkey, null);
                }
            }

            return dwellPTDMap;
        }

        void updateVacisAndTailGateExamPTD(Set<Serializable> examsCueGkeySet) {
            if (examsCueGkeySet != null && examsCueGkeySet.size() > 0) {

                Session extractSession = null;
                ChargeableUnitEvent vacisOrTailGateExamCUE = null
                try {
                    LOG.debug("begin session try")
                    extractSession = ExtractHibernateApi.getInstance().beginExtractSession();
                    LOG.debug("begin session")
                    for (Serializable bexuGkey : examsCueGkeySet) {

                        vacisOrTailGateExamCUE = (ChargeableUnitEvent) extractSession?.load(ChargeableUnitEvent.class, bexuGkey);

                        if (vacisOrTailGateExamCUE != null && IServiceExtract.INVOICED.equals(vacisOrTailGateExamCUE.getBexuStatus())) {
                            vacisOrTailGateExamCUE.setBexuPaidThruDay(ArgoUtils.timeNow());
                        }
                    }

                    if (extractSession != null) {
                        extractSession.getTransaction().commit();

                    }
                } catch (Exception ex) {
                    LOG.debug("Exception" + ex)
                    if (extractSession != null) {
                        ExtractHibernateApi.getInstance().rollbackTransaction(extractSession, ex);
                    }
                } finally {
                    LOG.debug("finally in LIBRARY")
                    if (extractSession != null) {
                        ExtractHibernateApi.getInstance().endExtractSession(extractSession)
                    }
                }
            }
        }

        void updateDwellPTDAndStatus(Map<Serializable, Date> dwellPTDMap, Boolean isUpdatePtd) {

            if (dwellPTDMap != null && dwellPTDMap.size() > 0) {
                Set<Serializable> bexuGkeys = dwellPTDMap.keySet();
                Session extractSession = null;
                ChargeableUnitEvent dwellCUE = null
                try {
                    LOG.debug("begin session try")
                    extractSession = ExtractHibernateApi.getInstance().beginExtractSession();
                    LOG.debug("begin session")
                    for (Serializable bexuGkey : bexuGkeys) {

                        dwellCUE = (ChargeableUnitEvent) extractSession?.load(ChargeableUnitEvent.class, bexuGkey);

                        if (dwellCUE != null) {
                                Date ptd =  dwellPTDMap.get(bexuGkey) != null ? (Date) dwellPTDMap.get(bexuGkey) : null;
                                String bexuStatus = ptd != null ? getDwellStatusToUpdate(dwellCUE, ptd, isUpdatePtd) : null;
                                if(bexuStatus != null){
                                    if ("PARTIAL".equals(bexuStatus)) {
                                        dwellCUE.setBexuStatus("QUEUED");
                                    } else {
                                        dwellCUE.setBexuStatus(bexuStatus)
                                    }
                                    dwellCUE.setBexuFlexString03(bexuStatus)
                                    if (!isUpdatePtd) {
                                        dwellCUE.setBexuPaidThruDay(ptd);
                                    }
                            }
                        }
                    }

                    if (extractSession != null) {
                        extractSession.getTransaction().commit();

                    }
                } catch (Exception ex) {
                    LOG.debug("Exception" + ex)
                    if (extractSession != null) {
                        ExtractHibernateApi.getInstance().rollbackTransaction(extractSession, ex);
                    }
                } finally {
                    LOG.debug("finally in LIBRARY")
                    if (extractSession != null) {
                        ExtractHibernateApi.getInstance().endExtractSession(extractSession)
                    }
                }
            }
        }

        void updateSoCUEStatus(Set<Serializable> cueGkeySet, String finalInvNbr) {
            if (cueGkeySet != null && cueGkeySet.size() > 0) {

                Session extractSession = null;
                ChargeableUnitEvent soUnitCUE = null
                try {
                    LOG.debug("begin session try")
                    extractSession = ExtractHibernateApi.getInstance().beginExtractSession();
                    LOG.debug("begin session")
                    for (Serializable bexuGkey : cueGkeySet) {
                        soUnitCUE = (ChargeableUnitEvent) extractSession?.load(ChargeableUnitEvent.class, bexuGkey);
                        if(!"Regenerate".equalsIgnoreCase(finalInvNbr)){
                            soUnitCUE.setBexuFlexString04(finalInvNbr)
                        } else{
                            soUnitCUE.setBexuFlexString04(null)
                        }
                    }

                    if (extractSession != null) {
                        extractSession.getTransaction().commit();

                    }
                } catch (Exception ex) {
                    LOG.debug("Exception" + ex)
                    if (extractSession != null) {
                        ExtractHibernateApi.getInstance().rollbackTransaction(extractSession, ex);
                    }
                } finally {
                    LOG.debug("finally in LIBRARY")
                    if (extractSession != null) {
                        ExtractHibernateApi.getInstance().endExtractSession(extractSession)
                    }
                }
            }
        }

        String getDwellStatusToUpdate(ChargeableUnitEvent cue, Date ptdDate, Boolean isUpdatePtd) {
            Calendar calendar = Calendar.getInstance(ContextHelper.getThreadUserTimezone());
            calendar.setTime(ptdDate);
            ptdDate = calendar.getTime();

            Date unitTimeOut = cue.getBexuUfvTimeOut();

            if (isUpdatePtd) {
                if (cue.getBexuPaidThruDay() == null) {
                    return IServiceExtract.QUEUED
                } else {
                    return IServiceExtract.PARTIAL;
                }
            } else {
                if (unitTimeOut != null) {

                    calendar.setTime(unitTimeOut);
                    unitTimeOut = calendar.getTime();
                    if (ptdDate.before(unitTimeOut)) {
                        return IServiceExtract.PARTIAL;
                    } else {
                        return IServiceExtract.INVOICED;
                    }
                } else {

                    return IServiceExtract.PARTIAL;
                }
            }
        }

        Date findLastDwellPTD(Serializable inEventExtractGkey, Invoice inInvoice) {
            Junction finalInvItem = PredicateFactory.conjunction()
            finalInvItem.add(PredicateFactory.eq(BillingField.INVOICE_STATUS, InvoiceStatusEnum.FINAL))

            Junction draftInvItem = PredicateFactory.conjunction()
            draftInvItem.add(PredicateFactory.eq(BillingField.INVOICE_STATUS, InvoiceStatusEnum.DRAFT))
                    .add(PredicateFactory.eq(BillingField.INVOICE_GKEY, inInvoice.getInvoiceGkey()));

            Disjunction disjunction = new Disjunction()
            disjunction.add(finalInvItem).add(draftInvItem)

            DomainQuery invoiceDQ = QueryUtils.createDomainQuery(BillingEntity.INVOICE)
                    .addDqPredicate(disjunction);


            DomainQuery invoiceItemDQ = QueryUtils.createDomainQuery(BillingEntity.INVOICE_ITEM)
                    .addDqPredicate(PredicateFactory.eq(BillingField.ITEM_SERVICE_EXTRACT_GKEY, inEventExtractGkey))
                    .addDqPredicate(PredicateFactory.eq(BillingField.ITEM_EVENT_TYPE_ID, dwell_event))
                    .addDqPredicate(PredicateFactory.subQueryIn(invoiceDQ, BillingField.ITEM_INVOICE))
                    .addDqOrdering(Ordering.desc(BillingField.ITEM_TO_DATE));


            Serializable[] invoiceItemGkeys = (Serializable[]) Roastery.getHibernateApi().findPrimaryKeysByDomainQuery(invoiceItemDQ);

            if (invoiceItemGkeys.length > 0) {
                Serializable itemGkey = invoiceItemGkeys[0];
                InvoiceItem ptdForLastItem = InvoiceItem.hydrate(itemGkey)
                return ptdForLastItem.getItemToDate();
            }
            return null;
        }


        private static Logger LOG = Logger.getLogger(ITSUpdateDwellPTDAndStatusLibrary.class)
        private static final String dwell_event = "UNIT_EXTENDED_DWELL"
        private static final String tailGateExam = "TAILGATE_EXAM_REQUIRED"
        private static final String vacisInspectionRequired = "VACIS_INSPECTION_REQUIRED"

    }