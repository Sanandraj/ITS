import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.zk.util.JSONBuilder
import com.navis.vessel.VesselEntity
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.vessel.business.schedule.VesselVisitLine
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/*
 * @Author <a href="mailto:annalakshmig@weservetech.com">ANNALAKSHMI G</a>
 * Date: 28/12/2021
 * Requirements:- Returns a list of Vessel Schedules arriving between -7 days and +27 days from today in JSON format
 *  @Inclusion Location	: Incorporated as a code extension of the type TRANSACTED_BUSINESS_FUNCTION --> Paste this code (ITSGetVesselScheduleWSCallback.groovy)
 * First shift - 8:00 to 17:59
 * Second shift -18:00 to 02:59
 * Third Shift - 03:00 to 07:59
 */

class ITSGetVesselScheduleWSCallback extends AbstractExtensionPersistenceCallback {

    @Override
    void execute(@Nullable Map inMap, @Nullable Map outMap) {
        LOGGER.debug("ITSGetVesselScheduleWSCallback :: start")
        outMap.put("RESPONSE", prepareVesselScheduleMessageToITS(getVisitDetailsList()))

    }

    List<VesselVisitDetails> getVisitDetailsList() {
        DomainQuery dq = QueryUtils.createDomainQuery(VesselEntity.VESSEL_VISIT_DETAILS)
                .addDqPredicate(PredicateFactory.between(CVD_ETA, getStartDate(), getEndDate()))
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
                vesselStatus = vvd.getCvdCv()?.getCvVisitPhase()?.getKey()
                if (vesselStatus != null) {
                    switch (vesselStatus) {
                        case CarrierVisitPhaseEnum.ARRIVED.getKey():
                            vesselStatus = ready
                            break
                        case CarrierVisitPhaseEnum.WORKING.getKey():
                            vesselStatus = working
                            break
                        case CarrierVisitPhaseEnum.COMPLETE.getKey():
                            vesselStatus = complete
                            break
                        default:
                            vesselStatus = null;
                    }
                }
                Set<VesselVisitLine> vvdVvlineSet = (Set<VesselVisitLine>) vvd.getVvdVvlineSet()
                StringBuilder slScacsString = new StringBuilder()
                for (VesselVisitLine vvl : vvdVvlineSet) {
                    slScacsString.append(vvl.getVvlineBizu().getBzuId()).append(",")
                }
                JSONBuilder jsonObject = JSONBuilder.createObject();
                jsonObject.put("vesselName", vvd.getVvdVessel()?.getVesName() != null ? vvd.getVvdVessel().getVesName() : "")
                jsonObject.put("inboundVoyageNum", vvd.getVvdIbVygNbr() != null ? vvd.getVvdIbVygNbr() : "")
                jsonObject.put("outboundVoyageNum", vvd.getVvdObVygNbr() != null ? vvd.getVvdObVygNbr() : "")
                jsonObject.put("arrivalDtTm", vvd.getCvdETA() != null ? ISO_DATE_FORMAT.format(vvd.getCvdETA()) : "")
                jsonObject.put("departureDtTm", vvd.getCvdETD() != null ? ISO_DATE_FORMAT.format(vvd.getCvdETD()) : "")
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
                    DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm");
                    LocalTime startTime1 = LocalTime.parse(firstShift, format);
                    LocalTime startTime2 = LocalTime.parse(secondShift, format);
                    LocalTime startTime3 = LocalTime.parse(thirdShift, format);
                    SimpleDateFormat localDateFormat = new SimpleDateFormat("HH:mm");
                    String time = localDateFormat.format(vvd.getVvdTimeStartWork());
                    LocalTime targetTime = LocalTime.parse(time, format);
                    if (targetTime.equals(startTime1) || (targetTime.isBefore(startTime2) && targetTime.isAfter(startTime1))) {
                        jsonObject.put("shiftStartNum", "1")
                    } else if (targetTime.equals(startTime2) || (targetTime.isBefore(startTime3) && targetTime.isAfter(startTime2))) {
                        jsonObject.put("shiftStartNum", "2")
                    } else {
                        jsonObject.put("shiftStartNum", "3")
                    }

                }

                if (vvd.getCvdTimeDischargeComplete() != null) {
                    jsonObject.put("dischargeFinishedDtTm", vvd.getCvdTimeDischargeComplete() != null ? ISO_DATE_FORMAT.format(vvd.getCvdTimeDischargeComplete()) : "")
                }
                jsonObject.put("shippingLineScacs", slScacsString.length() > 0 ? slScacsString.deleteCharAt(slScacsString.length() - 1).toString() : "")
                jsonArray.add(jsonObject)
            }
        }
        JSONBuilder vesselSchedulesObj = JSONBuilder.createObject();
        vesselSchedulesObj.put("vesselSchedules", jsonArray)
        LOGGER.debug("Response string" + vesselSchedulesObj.toJSONString())
        return vesselSchedulesObj.toJSONString()
    }


    private static DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static MetafieldId CVD_ETA = MetafieldIdFactory.valueOf("cvdETA")
    private static final String ready = "READY"
    private static final String working = "WORKING"
    private static final String complete = "COMPLETE"
    private static final String firstShift = "08:00";
    private static final String secondShift = "18:00";
    private static final String thirdShift = "03:00";
    private static Logger LOGGER = Logger.getLogger(this.class);

}
