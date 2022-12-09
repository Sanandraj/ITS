/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.IEventType
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.CarrierVisitReadyToBillEnum
import com.navis.argo.business.extract.ChargeableMarineEvent
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.webservice.types.v1_0.GenericInvokeResponseWsType
import com.navis.argo.webservice.types.v1_0.ResponseType
import com.navis.argo.webservice.types.v1_0.ScopeCoordinateIdsWsType
import com.navis.external.framework.util.ExtensionUtils
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.services.business.api.EventManager
import com.navis.services.business.event.Event
import com.navis.services.business.event.EventFieldChange
import com.navis.services.business.event.GroovyEvent
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.www.services.argoservice.ArgoServicePort
import org.apache.log4j.Level
import org.apache.log4j.Logger

/*
     *
     * @Author : Gopinath Kannappan, 12/Nov/2022
     *
     * Requirements : B 5-1 Standardize Marine Invoices -- This groovy is used to record the Marine Billable event, while Vessel is ready to bill.
     *
     * @Inclusion Location	: Incorporated as a code extension of the type GENERAL_NOTICE_CODE_EXTENSION.
     *
     *  Load Code Extension to N4:
            1. Go to Administration --> System -->  Code Extension
            2. Click Add (+)
            3. Enter the values as below:
                Code Extension Name:  ITSVesselReadyToBillGenNotice
                Code Extension Type:  GENERAL_NOTICE_CODE_EXTENSION
               Groovy Code: Copy and paste the contents of groovy code.
            4. Click Save button

     *  Set up General Notice for event type "UPDATE_VV" on VesselVisit Entity then execute this code extension (ITSVesselReadyToBillGenNotice).
     *
     *
 */


class ITSVesselReadyToBillGenNotice extends AbstractGeneralNoticeCodeExtension {

    private static Logger LOGGER = Logger.getLogger(ITSVesselReadyToBillGenNotice.class)

    @Override
    void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.DEBUG)
        VesselVisitDetails vvDetail = inGroovyEvent != null ? (VesselVisitDetails) inGroovyEvent.getEntity() : null
        Object library = ExtensionUtils.getLibrary(ContextHelper.getThreadUserContext(), "ITSAutoBillMarineUtility")
        EventFieldChange eventFieldChange = inGroovyEvent != null ? library.getFieldChange(inGroovyEvent.getEvent(), MetafieldIdFactory.valueOf("cvdCv.cvReadyToInvoice").getFieldId()) : null
        if (eventFieldChange != null && vvDetail != null && vvDetail.getCvdCv() != null) {
            if (CarrierVisitPhaseEnum.COMPLETE.equals(vvDetail.getVvdVisitPhase()) || CarrierVisitPhaseEnum.DEPARTED.equals(vvDetail.getVvdVisitPhase())) {
                ScopedBizUnit vvOperator = vvDetail.getCvdCv() != null ? vvDetail.getCvdCv().getCvOperator() : null
                String lineId = vvOperator != null ? vvOperator.getBzuId() : null
                List<String> eventToRecord = library.getEventsToRecord(lineId)
                ServicesManager srvcMgr = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
                EventManager eventManager = (EventManager) Roastery.getBean("eventManager")
                IEventType inEventType = null
                if (CarrierVisitReadyToBillEnum.READY.equals(vvDetail.getCvdCv().getCvReadyToInvoice())) {
                    if (eventToRecord != null && !eventToRecord.isEmpty()) {
                        for (String event : eventToRecord) {
                            try {
                                if (event != null) {
                                    inEventType = srvcMgr.getEventType(event)
                                    if (inEventType != null && vvDetail != null) {
                                        srvcMgr.recordEvent(inEventType, null, null, null, vvDetail)
                                    }
                                }
                            } catch (Exception ex) {
                                LOGGER.error("ITSVesselReadyToBillGenNotice error while recording an event " + ex)
                            }

                        }
                    }
                } else {
                    if (eventToRecord != null && !eventToRecord.isEmpty()) {
                        Event cancelEvent = null
                        Long extractBatchId = null
                        List<ChargeableMarineEvent> marineEventList = null
                        for (String event : eventToRecord) {
                            cancelEvent = null
                            try {
                                if (event != null) {
                                    inEventType = srvcMgr.getEventType(event)
                                    cancelEvent = eventManager.getMostRecentEventByType(inEventType, vvDetail)
                                    if (cancelEvent != null) {
                                        extractBatchId = cancelEvent.getEvntBillingExtractBatchId()
                                        if (extractBatchId != null) {
                                            marineEventList = library.findCME(extractBatchId)
                                            if (marineEventList != null) {
                                                for (ChargeableMarineEvent inCME : marineEventList) {
                                                    if (inCME != null) {
                                                        if ("QUEUED".equals(inCME.getStatus())) {
                                                            inCME.setStatusCancelled()
                                                        } else if ("DRAFT".equals(inCME.getStatus())) {
                                                            if (inCME != null) {
                                                                String draftNbr = inCME.getLastDraftInvNbr()
                                                                String deleteInvoiceXML = draftNbr != null ? library.deleteInvoiceRequest(draftNbr) : null
                                                                ScopeCoordinateIdsWsType scopeCoordinates = library.getScopeCoordinatesForWebService()
                                                                ArgoServicePort servicePort = library.getWebServiceStub()
                                                                if (servicePort != null && deleteInvoiceXML != null) {
                                                                    try{
                                                                        GenericInvokeResponseWsType webServiceResponse = scopeCoordinates != null ? servicePort.genericInvoke(scopeCoordinates, deleteInvoiceXML) : null
                                                                        ResponseType ptResponse = webServiceResponse != null ? webServiceResponse.getCommonResponse() : null
                                                                        if (ptResponse == null) {
                                                                            LOGGER.error("Something went wrong in N4 Billing.Billing invoice request failed")
                                                                        } else {
                                                                            LOGGER.debug("ITSAutoGenerateMarineInvoiceGenNotice ptResponse :" + ptResponse)
                                                                        }
                                                                    } catch(Exception ex){
                                                                        LOGGER.error("Exception while calling billing service"+ex)
                                                                    }

                                                                }

                                                            }
                                                            inCME.setStatusCancelled()

                                                        } else if ("INVOICED".equals(inCME.getStatus())) {
                                                            // do nothing here
                                                            LOGGER.debug("CME is INVOICED, not able to delete it " + inCME.getStatus())
                                                        } else {
                                                            // do nothing here
                                                            LOGGER.debug("CME not in proper status " + inCME.getStatus())
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        //cancelEvent.setEventTypeIsBillable(false);
                                    }

                                }
                            } catch (Exception ex) {
                                LOGGER.debug("ITSVesselReadyToBillGenNotice error while recording an event " + ex)
                            }

                        }
                    }

                }

            }

        }
    }


}
