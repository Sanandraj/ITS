/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/

import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.Disjunction
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.zk.util.JSONBuilder
import com.navis.vessel.VesselEntity
import com.navis.vessel.api.VesselVisitField
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.vessel.business.schedule.VesselVisitLine
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId

/*
 *  @Author: mailto:annalakshmig@weservetech.com, Annalakshmi G; Date: 28/12/2021
 *
 *  Requirements: Returns a list of Vessel Schedules arriving between -7 days and +27 days from today in JSON format
 *  First shift - 8:00 to 17:59
 *  Second shift -18:00 to 02:59
 *  Third Shift - 03:00 to 07:59
 *
 *  @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSGetVesselScheduleWSCallback
 *     Code Extension Type: TRANSACTED_BUSINESS_FUNCTION
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *  1       08/08/2022      Gopal B         IP-54     Added vesselScheduleId and updated ATA, ATD values and Vessel status description to match N4 as per Kiyo's requirement
 *
 */

class ITSGetVesselScheduleWSCallback extends AbstractExtensionPersistenceCallback {

    @Override
    void execute(@Nullable Map inMap, @Nullable Map outMap) {
        LOGGER.debug("ITSGetVesselScheduleWSCallback :: start")
        outMap.put("RESPONSE", prepareVesselScheduleMessageToITS(getVisitDetailsList()))

    }

    List<VesselVisitDetails> getVisitDetailsList() {
        DomainQuery dq = QueryUtils.createDomainQuery(VesselEntity.VESSEL_VISIT_DETAILS)
                .addDqPredicate(PredicateFactory.in(VesselVisitField.VVD_VISIT_PHASE, VISIT_PHASE_LIST));
        Disjunction workingOrWithinRange = (Disjunction) PredicateFactory.disjunction()
                .add(PredicateFactory.eq(VesselVisitField.VVD_VISIT_PHASE, CarrierVisitPhaseEnum.WORKING))
                .add(PredicateFactory.between(CVD_ETA, getStartDate(), getEndDate()));
        dq.addDqPredicate(workingOrWithinRange);

        return (List<VesselVisitDetails>) HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
    }

    private Date getEndDate() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 27);
        return cal.getTime();
    }

    private Date getStartDate() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -7);
        return cal.getTime();
    }

    String prepareVesselScheduleMessageToITS(List<VesselVisitDetails> vvdList) {

        JSONBuilder jsonArray = JSONBuilder.createArray()
        String vesselStatus = null;
        for (VesselVisitDetails vvd : vvdList) {
            if (vvd != null) {
                CarrierVisit carrierVisit = CarrierVisit.findByCvdGkey(vvd.getCvdGkey());
                vesselStatus = vvd.getCvdCv()?.getCvVisitPhase()?.getKey()
                if (vesselStatus != null) {
                    vesselStatus = vesselStatus.substring(2);
                }
                Set<VesselVisitLine> vvdVvlineSet = (Set<VesselVisitLine>) vvd.getVvdVvlineSet()
                StringBuilder slScacsString = new StringBuilder()
                StringBuilder slScacsCdString = new StringBuilder()
                for (VesselVisitLine vvl : vvdVvlineSet) {
                    slScacsString.append(vvl.getVvlineBizu().getBzuId()).append(",")
                    slScacsCdString.append(vvl.getVvlineBizu().getBzuScac()).append(",")

                }
                JSONBuilder jsonObject = JSONBuilder.createObject();
                jsonObject.put("vesselScheduleId", vvd.getCvdGkey() != null ? vvd.getCvdGkey() : "")
                jsonObject.put("vesselName", vvd.getVvdVessel()?.getVesName() != null ? vvd.getVvdVessel().getVesName() : "")
                jsonObject.put("inboundVoyageNum", vvd.getVvdIbVygNbr() != null ? vvd.getVvdIbVygNbr() : "")
                jsonObject.put("outboundVoyageNum", vvd.getVvdObVygNbr() != null ? vvd.getVvdObVygNbr() : "")
                jsonObject.put("arrivalDtTm", carrierVisit != null && carrierVisit.getCvATA() != null ? ISO_DATE_FORMAT.format(carrierVisit.getCvATA()) :
                        vvd.getCvdETA() != null ? ISO_DATE_FORMAT.format(vvd.getCvdETA()) : "")
                jsonObject.put("departureDtTm", carrierVisit != null && carrierVisit.getCvATD() != null ? ISO_DATE_FORMAT.format(carrierVisit.getCvATD()) :
                        vvd.getCvdETD() != null ? ISO_DATE_FORMAT.format(vvd.getCvdETD()) : "")
                if (vvd.getVvdTimeBeginReceive() != null) {
                    jsonObject.put("beginReceivingDtTm", ISO_DATE_FORMAT.format(vvd.getVvdTimeBeginReceive()))
                }
                if (vesselStatus != null) {
                    jsonObject.put("vesselStatusDsc", vesselStatus)
                }
                if (vvd.getVvFlexDate02() != null) {
                    jsonObject.put("bookingCloseDtTm", ISO_DATE_FORMAT.format(vvd.getVvFlexDate02()))
                }

                if (vvd.getVvdTimeCargoCutoff() != null) {
                    jsonObject.put("fullReceiveCutoffDtTm", ISO_DATE_FORMAT.format(vvd.getVvdTimeCargoCutoff()))
                }
                if (vvd.getVvFlexDate01() != null) {
                    jsonObject.put("lateReceiveCutoffDtTm", ISO_DATE_FORMAT.format(vvd.getVvFlexDate01()))
                }
                if (vvd.getVvdTimeStartWork() != null) {
                    jsonObject.put("shiftStartDtTm", ISO_DATE_FORMAT.format(vvd.getVvdTimeStartWork()))
                    LocalDateTime vvdStartTime = vvd.getVvdTimeStartWork().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                    int targetTime = vvdStartTime.getHour()
                    LOGGER.debug("targetTime" + targetTime)
                    if (targetTime >= 8 && targetTime < 18)
                        jsonObject.put("shiftStartNum", "1")
                    else if ((targetTime >= 18 && targetTime < 24) || (targetTime >= 0 && targetTime < 3))
                        jsonObject.put("shiftStartNum", "2")
                    else
                        jsonObject.put("shiftStartNum", "3")
                }

                if (vvd.getCvdTimeDischargeComplete() != null) {
                    jsonObject.put("dischargeFinishedDtTm", vvd.getCvdTimeDischargeComplete() != null ? ISO_DATE_FORMAT.format(vvd.getCvdTimeDischargeComplete()) : "")
                }
                jsonObject.put("shippingLineCds", slScacsString.length() > 0 ? slScacsString.deleteCharAt(slScacsString.length() - 1).toString() : "")
                jsonObject.put("shippingLineScacs", slScacsCdString.length() > 0 ? slScacsCdString.deleteCharAt(slScacsCdString.length() - 1).toString() : "")
                jsonArray.add(jsonObject)
            }
        }
        JSONBuilder vesselSchedulesObj = JSONBuilder.createObject();
        vesselSchedulesObj.put("vesselSchedules", jsonArray)
        LOGGER.debug("Response string" + vesselSchedulesObj.toJSONString())
        return vesselSchedulesObj.toJSONString()
    }

    private static final List<CarrierVisitPhaseEnum> VISIT_PHASE_LIST = [CarrierVisitPhaseEnum.CLOSED, CarrierVisitPhaseEnum.INBOUND, CarrierVisitPhaseEnum.WORKING, CarrierVisitPhaseEnum.ARRIVED, CarrierVisitPhaseEnum.COMPLETE]
    private static DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static MetafieldId CVD_ETA = MetafieldIdFactory.valueOf("cvdETA")
    private static Logger LOGGER = Logger.getLogger(this.class);

}
