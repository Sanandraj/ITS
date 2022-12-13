/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.ArgoExtractEntity
import com.navis.argo.ArgoExtractField
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.CarrierVisitReadyToBillEnum
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.external.framework.util.EFieldChange
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.ExtensionUtils
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

/**
 * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 12/Nov/2022
 *
 *  Requirements : This groovy is used to validate the vessel visit set to ready to invoice.
 *
 * @Inclusion Location	: Incorporated as a code extension of the type TRANSACTED_BUSINESS_FUNCTION
 *
 *  Load Code Extension to N4:
 *   1. Go to Administration --> System -->  Code Extension
 *   2. Click Add (+)
 *   3. Enter the values as below:
 Code Extension Name:  ITSVesselVisitBeforeSubmitCallback
 Code Extension Type:  TRANSACTED_BUSINESS_FUNCTION
 Groovy Code: Copy and paste the contents of groovy code.
 *   4. Click Save button
 *
 * @Set up :- Calling as transaction business function from ITSVesselVisitFormSubmission and execute it.
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 */

class ITSVesselVisitBeforeSubmitCallback extends AbstractExtensionPersistenceCallback {

    @Override
    void execute(@Nullable Map inParms, @Nullable Map inOutResults) {
        PersistenceTemplate pt = new PersistenceTemplate(getUserContext())
        pt.invoke(new CarinaPersistenceCallback() {
            protected void doInTransaction() {
                try {
                    EFieldChanges inOutFieldChanges = inParms.get("FIELD_CHANGES")
                    List<Long> gkeys = inParms.get("GKEYS")

                    Serializable vvdGkey = null
                    if (gkeys == null || inOutFieldChanges == null) {
                        return
                    }

                    if (gkeys != null) {
                        boolean isValid = false
                        Iterator<Serializable> gkeyIterator = gkeys.iterator()
                        while (gkeyIterator.hasNext()) {
                            vvdGkey = gkeyIterator.next()
                            VesselVisitDetails vesselVisitDetails = vvdGkey != null ? VesselVisitDetails.hydrate(vvdGkey) : null
                            if (vesselVisitDetails != null) {

                                EFieldChange eFieldChange_cargo = inOutFieldChanges.findFieldChange(CARGO_CUT_OFF)
                                EFieldChange eFieldChange_Edi = inOutFieldChanges.findFieldChange(CARGO_EDI_OFF)
                                if (eFieldChange_cargo != null) {
                                    Date cargoCut = eFieldChange_cargo.getNewValue() as Date
                                    if (vesselVisitDetails.getVvFlexDate02() == null && cargoCut != null && eFieldChange_Edi == null) {
                                        isValid = true;
                                    } else {
                                        isValid = false;
                                    }
                                }

                                CarrierVisitPhaseEnum inPhase = vesselVisitDetails.getVvdVisitPhase()
                                CarrierVisitReadyToBillEnum invoiceReadyToSetFromValue = null
                                CarrierVisitReadyToBillEnum invoiceReadyToSetToValue = null
                                Object library = ExtensionUtils.getLibrary(ContextHelper.getThreadUserContext(), "ITSAutoBillMarineUtility")
                                ScopedBizUnit vvOperator = vesselVisitDetails.getCvdCv() != null ? vesselVisitDetails.getCvdCv().getCvOperator() : null
                                String lineId = vvOperator != null ? vvOperator.getBzuId() : null
                                List<String> eventToRecord = library.getEventsToRecord(lineId)
                                if (inOutFieldChanges.hasFieldChange(MetafieldIdFactory.valueOf("cvdCv.cvReadyToInvoice"))) {
                                    invoiceReadyToSetFromValue = (CarrierVisitReadyToBillEnum) inOutFieldChanges.findFieldChange(MetafieldIdFactory.valueOf("cvdCv.cvReadyToInvoice")).getPriorValue()
                                    invoiceReadyToSetToValue = (CarrierVisitReadyToBillEnum) inOutFieldChanges.findFieldChange(MetafieldIdFactory.valueOf("cvdCv.cvReadyToInvoice")).getNewValue()
                                }


                                if ((CarrierVisitPhaseEnum.COMPLETE.equals(inPhase) || CarrierVisitPhaseEnum.DEPARTED.equals(inPhase))) {
                                    if (invoiceReadyToSetToValue != null && (CarrierVisitReadyToBillEnum.READY.equals(invoiceReadyToSetFromValue) && !CarrierVisitReadyToBillEnum.READY.equals(invoiceReadyToSetToValue))) {
                                        if (findChargeableMarineEventsIsInvoiced(vesselVisitDetails, eventToRecord)) {
                                            inOutResults.put("ERROR", "Invoice is already generated for Complete or Departed Phase !!")
                                        } else {
                                            inOutResults.put("WARNING", "Vessel is already set ready for the bill, if any invoices generated it will be deleted automatically !!")
                                        }

                                    }
                                } else {
                                    if (CarrierVisitReadyToBillEnum.READY.equals(invoiceReadyToSetToValue)) {
                                        inOutResults.put("ERROR", "Invoice Ready to set can set only for Complete or Departed Phase !!")
                                    }
                                }

                            }
                        }

                        if (isValid) {
                            inOutResults.put("ERROR", "Cannot update CARGO-cut-off field because EDI-cut-off field is mandatory !!")
                        }
                    }

                } catch (Exception e) {
                    inOutResults.put("ERROR", e.getMessage())
                }
            }
        })

    }


    private boolean findChargeableMarineEventsIsInvoiced(VesselVisitDetails vesselVisitDetails, List<String> inEventTypeIDs) {
        boolean isCMEInvoiced = false
        DomainQuery domainQuery = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_MARINE_EVENT)
        domainQuery.addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXM_VVD_GKEY, vesselVisitDetails.getPrimaryKey()))
        domainQuery.addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXM_EVENT_TYPE_ID, inEventTypeIDs))
        domainQuery.addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXM_STATUS, "INVOICED"))
        List inValues = HibernateApi.getInstance().findEntitiesByDomainQuery(domainQuery);
        if (inValues != null && inValues.size() >= 1) {
            isCMEInvoiced = true
        }
        return isCMEInvoiced
    }

    private static final MetafieldId CARGO_CUT_OFF = MetafieldIdFactory.valueOf("vvdTimeCargoCutoff")
    private static final MetafieldId CARGO_EDI_OFF = MetafieldIdFactory.valueOf("vvFlexDate02")
    private static Logger LOGGER = Logger.getLogger(ITSVesselVisitBeforeSubmitCallback.class)
}
