package ITS.Enhancements

import com.navis.argo.ContextHelper
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

class ITSUpdateDwellPTDAndStatusLibrary extends GroovyApi {

    void updateExtendedDwellStatusAndPTD(EEntityView entity, Boolean isUpdatePtd) {
        LOG.setLevel(Level.DEBUG)
        LOG.debug("ITSUpdateDwellPTDAndStatusLibrary starts");
        Serializable invoiceGkey = (Serializable) entity.getField(BillingField.INVOICE_GKEY)

        Invoice invoice = Invoice.hydrate(invoiceGkey)
        Set<InvoiceItem> invoiceItems = invoice.getInvoiceInvoiceItems();

        Set<Serializable> unitDwellEventCUEGkey = new HashSet<>()


        for (InvoiceItem invItem : invoiceItems) {
            if (dwell_event.equals(invItem.getItemEventTypeId())) {
                unitDwellEventCUEGkey.add(invItem.getItemServiceExtractGkey());

            }
        }

        LOG.debug("unitDwellEventCUEGkey : " + unitDwellEventCUEGkey);
        if (CollectionUtils.isEmpty(unitDwellEventCUEGkey)) {
            LOG.debug("No item for this event!")
            return;
        }

        Map<Serializable, Date> dwellPTDMap = createDwellPTDMap(unitDwellEventCUEGkey, invoice);

        updateDwellPTDAndStatus(dwellPTDMap, isUpdatePtd)
        LOG.debug("updateExtractPTDAndStatus end")

    }


    Map<Serializable, Date> createDwellPTDMap(Set<Serializable> dwellGkeys, Invoice inInvoice) {
        Map<Serializable, Date> dwellPTDMap = new HashMap<Serializable, Date>();

        for (Serializable eventExtractGkey : dwellGkeys) {
            Date lastDwellPTD = findLastDwellPTD(eventExtractGkey, inInvoice);
            if (lastDwellPTD != null) {
                dwellPTDMap.put(eventExtractGkey, lastDwellPTD);
            }
        }
//LOG.debug("dwellPTDMap"+dwellPTDMap.toMapString())
        return dwellPTDMap;
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
                    LOG.debug("bexuGkey Lib" + bexuGkey)
                    dwellCUE = (ChargeableUnitEvent) extractSession?.load(ChargeableUnitEvent.class, bexuGkey);

                    if (dwellCUE != null) {
                        Date ptd = (Date) dwellPTDMap.get(bexuGkey);
                        String bexuStatus = getDwellStatusToUpdate(dwellCUE, ptd, isUpdatePtd)
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

                if (extractSession != null) {
                    extractSession.getTransaction().commit();
                    //  ExtractHibernateApi.getInstance().endExtractSession(extractSession);
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
        LOG.debug("invoiceDQ: " + invoiceDQ);

        DomainQuery invoiceItemDQ = QueryUtils.createDomainQuery(BillingEntity.INVOICE_ITEM)
                .addDqPredicate(PredicateFactory.eq(BillingField.ITEM_SERVICE_EXTRACT_GKEY, inEventExtractGkey))
                .addDqPredicate(PredicateFactory.eq(BillingField.ITEM_EVENT_TYPE_ID, dwell_event))
                .addDqPredicate(PredicateFactory.subQueryIn(invoiceDQ, BillingField.ITEM_INVOICE))
                .addDqOrdering(Ordering.desc(BillingField.ITEM_TO_DATE));

        LOG.debug("invoiceItemDQ: " + invoiceItemDQ);
        Serializable[] invoiceItemGkeys = (Serializable[]) Roastery.getHibernateApi().findPrimaryKeysByDomainQuery(invoiceItemDQ);

        if (invoiceItemGkeys.length > 0) {
            Serializable itemGkey = invoiceItemGkeys[0];
            InvoiceItem ptdForLastItem = InvoiceItem.hydrate(itemGkey)
            return ptdForLastItem.getItemToDate();
        }
        return null;
    }


    private static final Logger LOG = Logger.getLogger(this.class)
    private static final String dwell_event = "UNIT_EXTENDED_DWELL"
}
