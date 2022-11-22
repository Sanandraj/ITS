import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.model.GeneralReference
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.util.message.MessageLevel
import com.navis.road.business.appointment.model.GateAppointment
import com.navis.road.business.appointment.model.TruckVisitAppointment
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Gate 3-5 Appointment validation
 *
 * uaarthi@weservetech.com - To validate the Truck Visit start time (OCR arrival) against the Appointment window.
 * General reference set up to determine the Shift periods and Early/Late arrival exceptions.
 */

class ITSValidateTruckArrivalGateTaskInterceptor extends AbstractGateTaskInterceptor {
    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        LOGGER.setLevel(Level.DEBUG)
        TruckTransaction tran = inWfCtx.getTran()
        TruckVisitDetails truckDetails = inWfCtx.getTv()

        Date truckStartTime = ArgoUtils.timeNow()
        Date validationStartTime = null
        Date validationEndTime = null
        String atslotStartSlackTimeMin = null
        String atslotEndSlackTimeMin = null
        int firstShiftStartHr = 6
        int firstShiftEndHr = 18
        if (truckDetails != null && truckDetails.getTvdtlsTruckVisitAppointment() != null) {
            TruckVisitAppointment tva = truckDetails.getTvdtlsTruckVisitAppointment()
            validationStartTime = truckDetails.getTvdtlsTruckVisitAppointment().getSlotStartDate()
            atslotStartSlackTimeMin = String.valueOf(tva.getTimeSlot().getAtslotQuotaRule().getAruleStartSlackTimeMin())

            validationEndTime = truckDetails.getTvdtlsTruckVisitAppointment().getSlotEndDate()
            atslotEndSlackTimeMin = String.valueOf(tva.getTimeSlot().getAtslotQuotaRule().getAruleEndSlackTimeMin())

        }
        if (tran != null && tran.getTranAppointment() != null) {
            GateAppointment appt = tran.getTranAppointment()
            if (validationStartTime == null) {
                validationStartTime = tran.getTranAppointment().getSlotStartDate()
                atslotStartSlackTimeMin = String.valueOf(appt.getTimeSlot().getAtslotQuotaRule().getAruleStartSlackTimeMin())

            }
            if (validationEndTime == null) {
                validationEndTime = tran.getTranAppointment().getSlotEndDate()
                atslotEndSlackTimeMin = String.valueOf(appt.getTimeSlot().getAtslotQuotaRule().getAruleEndSlackTimeMin())
            }
        }

        if (validationStartTime && validationEndTime) {
            GeneralReference gn = GeneralReference.findUniqueEntryById("ITS_APPT_VALIDATION", "SHIFT_SET")
             //TODO determine the Current Shift
              String firstShift = gn.getRefValue5()

              if(StringUtils.isNotEmpty(firstShift) && firstShift.indexOf('-') != -1){
                  String[] shifts = firstShift.split('-')
                  String startShift = shifts[0]
                  String[] startArr =startShift.split(':')
                  String firstShiftStartHrStr = startArr[0]
                  String firstShiftStartMinStr = startArr[1]
                  firstShiftStartHr = Integer.valueOf(firstShiftStartHrStr)

                  String endShift = shifts[1]
                  String[] endArr =endShift.split(':')
                  String firstShiftEndHrStr = endArr[0]
                  String firstShiftEndMinStr = endArr[1]
                  firstShiftEndHr = Integer.valueOf(firstShiftEndHrStr)


              }

              String secondShift = gn.getRefValue6()
              if(StringUtils.isNotEmpty(secondShift) && secondShift.indexOf('-') != -1){
                  String[] shifts = secondShift.split('-')
                  String startShift = shifts[0]
                  String endShift = shifts[1]
              }

            if (truckStartTime.after(validationStartTime) && truckStartTime.before(validationEndTime)) {
                return
            }

            boolean firstShiftEarly = (gn.getRefValue1() != null && "ON".equalsIgnoreCase(gn.getRefValue1())) ? Boolean.TRUE : Boolean.FALSE
            boolean firstShiftLate = (gn.getRefValue2() != null && "ON".equalsIgnoreCase(gn.getRefValue2())) ? Boolean.TRUE : Boolean.FALSE
            boolean secondShiftEarly = (gn.getRefValue3() != null && "ON".equalsIgnoreCase(gn.getRefValue3())) ? Boolean.TRUE : Boolean.FALSE
            boolean secondShiftLate = (gn.getRefValue4() != null && "ON".equalsIgnoreCase(gn.getRefValue4())) ? Boolean.TRUE : Boolean.FALSE

            Calendar startTimeCal = Calendar.getInstance();
            startTimeCal.setTime(validationStartTime)
            Calendar endTimeCal = Calendar.getInstance();
            endTimeCal.setTime(validationEndTime)

            LocalDateTime lcDate = truckStartTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            int hour = lcDate.getHour()
            int minute = lcDate.getMinute()

            if(hour > firstShiftStartHr && hour < firstShiftEndHr){ // assuming First Shift is between 6 to 23 // TODO check if the current shift is 1st Shift
                if(firstShiftEarly){
                    if(atslotStartSlackTimeMin){
                        startTimeCal.add(Calendar.MINUTE, -(Integer.valueOf(atslotStartSlackTimeMin))); //TODO use a temp
                        if(truckStartTime < startTimeCal.getTime()){
                           // RoadBizUtil.appendMessage(MessageLevel.INFO,key,messageParam)

                            //Throw error - ERROR_APPOINTMENT_TIME_NOT_YET_REACHED
                        }
                    }
                }

                if(firstShiftLate){
                    if(atslotEndSlackTimeMin){
                        endTimeCal.add(Calendar.MINUTE, Integer.valueOf(atslotEndSlackTimeMin)); //TODO use a temp
                        if(truckStartTime > endTimeCal.getTime()){
                            // Throw error - ERROR_APPOINTMENT_TIME_PASSED
                        }
                    }
                }
            }

            startTimeCal.setTime(validationStartTime)
            endTimeCal.setTime(validationEndTime)

            // TODO check if the current shift is 2nd Shift
            if(secondShiftEarly){
                if(atslotStartSlackTimeMin){
                    startTimeCal.add(Calendar.MINUTE, -(Integer.valueOf(atslotStartSlackTimeMin))); //TODO use a temp
                    if(truckStartTime < startTimeCal.getTime()){
                        //Throw error - ERROR_APPOINTMENT_TIME_NOT_YET_REACHED
                    }
                }
            }

            if(secondShiftLate){
                if(atslotEndSlackTimeMin){
                    endTimeCal.add(Calendar.MINUTE, Integer.valueOf(atslotEndSlackTimeMin)); //TODO use a temp
                    if(truckStartTime > endTimeCal.getTime()){
                        // Throw error - ERROR_APPOINTMENT_TIME_PASSED
                    }
                }
            }
        }
    }

    private static Logger LOGGER = Logger.getLogger(ITSValidateTruckArrivalGateTaskInterceptor.class)
}
