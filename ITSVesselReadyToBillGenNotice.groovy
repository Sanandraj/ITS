/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.business.api.IEventType
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.CarrierVisitReadyToBillEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.services.business.event.Event
import com.navis.services.business.event.EventFieldChange
import com.navis.services.business.event.GroovyEvent
import com.navis.vessel.business.schedule.VesselVisitDetails
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
        LOGGER.debug("ITSVesselReadyToBillGenNotice started")
        VesselVisitDetails vvDetail = inGroovyEvent != null ? (VesselVisitDetails) inGroovyEvent.getEntity() : null;
        EventFieldChange eventFieldChange = inGroovyEvent != null ? getFieldChange(inGroovyEvent.getEvent(), MetafieldIdFactory.valueOf("cvdCv.cvReadyToInvoice").getFieldId()) : null;

        if (eventFieldChange != null && vvDetail != null && vvDetail.getCvdCv() != null) {
            LOGGER.debug("ITSVesselReadyToBillGenNotice vvDetail state : " + vvDetail.getCvdCv().getCvReadyToInvoice())
            if (CarrierVisitReadyToBillEnum.READY.equals(vvDetail.getCvdCv().getCvReadyToInvoice())) {
                IEventType inEventType = null;
                ServicesManager srvcMgr = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);
                List<String> eventToRecord = getEventsToRecord();
                if (eventToRecord != null && !eventToRecord.isEmpty()) {
                    for (String event : eventToRecord) {
                        try {
                            if (event != null) {
                                inEventType = srvcMgr.getEventType(event);
                                LOGGER.debug("ITSVesselReadyToBillGenNotice inEvent " + inEventType)
                                if (inEventType != null && vvDetail != null) {
                                    srvcMgr.recordEvent(inEventType, null, null, null, vvDetail)
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
/**
 *
 * @param inEvent
 * @param inMetafieldId
 * @return
 */
    private static EventFieldChange getFieldChange(Event inEvent, String inMetafieldId) {
        Set<EventFieldChange> fcs = inEvent != null ? inEvent.getFieldChanges() : null
        if (fcs != null && inMetafieldId != null) {
            for (EventFieldChange efc : fcs) {
                if (inMetafieldId.equals(efc.getEvntfcMetafieldId())) {
                    return efc
                }
            }
        }
        return null
    }

/**
 * Get the Genereal Reference value
 * @return
 */
    private static List<String> getEventsToRecord() {
        GeneralReference inEvntFromGenRef = GeneralReference.findUniqueEntryById("ITS", "VESSEL_READY_TO_BILL", "BILLABLE_EVENT", null)
        List<String> inEvntList = new ArrayList<String>()

        if (inEvntFromGenRef != null) {
            if (inEvntFromGenRef.getRefValue1() != null) {
                inEvntList.addAll(inEvntFromGenRef.getRefValue1().split(","))
            }
            if (inEvntFromGenRef.getRefValue2() != null) {
                inEvntList.addAll(inEvntFromGenRef.getRefValue2().split(","))
            }
            if (inEvntFromGenRef.getRefValue3() != null) {
                inEvntList.addAll(inEvntFromGenRef.getRefValue3().split(","))
            }
            if (inEvntFromGenRef.getRefValue4() != null) {
                inEvntList.addAll(inEvntFromGenRef.getRefValue4().split(","))
            }
            if (inEvntFromGenRef.getRefValue5() != null) {
                inEvntList.addAll(inEvntFromGenRef.getRefValue5().split(","))
            }
            if (inEvntFromGenRef.getRefValue6() != null) {
                inEvntList.addAll(inEvntFromGenRef.getRefValue6().split(","))
            }
        }
        return inEvntList != null ? inEvntList : null
    }

}
