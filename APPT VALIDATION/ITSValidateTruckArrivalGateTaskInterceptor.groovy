/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/

import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.model.GeneralReference
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.road.RoadApptsField
import com.navis.road.business.appointment.model.GateAppointment
import com.navis.road.business.appointment.model.TruckVisitAppointment
import com.navis.road.business.atoms.AppointmentStateEnum
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
 * @Author: uaarthi@weservetech.com, Aarthi U; Date: 29-01-2023
 *
 *  Requirements: IP-309 - Gate 3-5 Appointment validation.
 *                To validate the Truck Visit start time (OCR arrival) against the Appointment window.
 *                General reference set up to determine the Shift periods and Early/Late arrival exceptions.
 *
 * @Inclusion Location: Incorporated as a code extension of the type GATE_TASK_INTERCEPTOR
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSValidateTruckArrivalGateTaskInterceptor
 *     Code Extension Type: GATE_TASK_INTERCEPTOR
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 *
 */
class ITSValidateTruckArrivalGateTaskInterceptor extends AbstractGateTaskInterceptor {
    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        LOGGER.setLevel(Level.DEBUG)
        TruckTransaction tran = inWfCtx.getTran()
        TruckVisitDetails truckDetails = inWfCtx.getTv()

        Date truckStartTime = ArgoUtils.timeNow()
        Long apptNbr = 0;
        Date validationStartTime = null
        Date validationEndTime = null
        String atslotStartSlackTimeMin = null
        String atslotEndSlackTimeMin = null
        int firstShiftStartHr = 0
        int firstShiftEndHr = 0
        int firstShiftStartMin = 0
        int firstShiftEndMin = 0
        int secondShiftStartHr = 0
        int secondShiftEndHr = 0
        int secondShiftStartMin = 0
        int secondShiftEndMin = 0
        if (truckDetails != null && truckDetails.getTvdtlsTruckVisitAppointment() != null) {
            TruckVisitAppointment tva = truckDetails.getTvdtlsTruckVisitAppointment()
            validationStartTime = truckDetails.getTvdtlsTruckVisitAppointment().getSlotStartDate()
            atslotStartSlackTimeMin = String.valueOf(tva.getTimeSlot().getAtslotQuotaRule().getAruleStartSlackTimeMin())

            validationEndTime = truckDetails.getTvdtlsTruckVisitAppointment().getSlotEndDate()
            atslotEndSlackTimeMin = String.valueOf(tva.getTimeSlot().getAtslotQuotaRule().getAruleEndSlackTimeMin())
            apptNbr = tva.getApptNbr()

        } else if (truckDetails != null && truckDetails?.getTvdtlsTruckVisitAppointment() == null) {

            TruckVisitAppointment truckVisitAppointment = getTVA(truckDetails?.getTvdtlsTruckLicenseNbr())
            if (truckVisitAppointment != null) {
                validationStartTime = truckVisitAppointment.getSlotStartDate()
                atslotStartSlackTimeMin = String.valueOf(truckVisitAppointment.getTimeSlot().getAtslotQuotaRule().getAruleStartSlackTimeMin())

                validationEndTime = truckVisitAppointment.getSlotEndDate()
                atslotEndSlackTimeMin = String.valueOf(truckVisitAppointment.getTimeSlot().getAtslotQuotaRule().getAruleEndSlackTimeMin())
                apptNbr = truckVisitAppointment.getApptNbr()
            }
        }

        if (tran != null && tran?.getTranAppointment() != null) {
            GateAppointment appt = tran.getTranAppointment()
            if (appt != null) {
                if (validationStartTime == null) {
                    validationStartTime = tran.getTranAppointment().getSlotStartDate()
                    atslotStartSlackTimeMin = String.valueOf(appt.getTimeSlot().getAtslotQuotaRule().getAruleStartSlackTimeMin())

                }
                if (validationEndTime == null) {
                    validationEndTime = tran.getTranAppointment().getSlotEndDate()
                    atslotEndSlackTimeMin = String.valueOf(appt.getTimeSlot().getAtslotQuotaRule().getAruleEndSlackTimeMin())
                }
                apptNbr = appt?.getApptNbr()
            }

        } else if (tran != null && tran?.getTranAppointment() == null) {
            GateAppointment appt = getAppt(tran?.getTranTruckVisit()?.getTvdtlsTruckLicenseNbr())
            if (appt != null) {
                if (validationStartTime == null) {
                    validationStartTime = tran.getTranAppointment().getSlotStartDate()
                    atslotStartSlackTimeMin = String.valueOf(appt.getTimeSlot().getAtslotQuotaRule().getAruleStartSlackTimeMin())

                }
                if (validationEndTime == null) {
                    validationEndTime = tran.getTranAppointment().getSlotEndDate()
                    atslotEndSlackTimeMin = String.valueOf(appt.getTimeSlot().getAtslotQuotaRule().getAruleEndSlackTimeMin())
                }
                apptNbr = appt?.getApptNbr()
            }

        }


        if (validationStartTime && validationEndTime) {
            GeneralReference gn = GeneralReference.findUniqueEntryById("ITS_APPT_VALIDATION", "SHIFT_SET")
            //TODO determine the Current Shift
            String firstShift = gn.getRefValue5()

            if (StringUtils.isNotEmpty(firstShift) && firstShift.indexOf('-') != -1) {
                String[] shifts = firstShift.split('-')
                String startShift = shifts[0]
                String[] startArr = startShift.split(':')
                String firstShiftStartHrStr = startArr[0]
                String firstShiftStartMinStr = startArr[1]
                firstShiftStartHr = Integer.valueOf(firstShiftStartHrStr)
                // firstShiftStartMin = Integer.valueOf(firstShiftStartMinStr)

                String endShift = shifts[1]
                String[] endArr = endShift.split(':')
                String firstShiftEndHrStr = endArr[0]
                String firstShiftEndMinStr = endArr[1]
                firstShiftEndHr = Integer.valueOf(firstShiftEndHrStr)

            }

            String secondShift = gn.getRefValue6()
            LOGGER.debug("secondShift" + secondShift)
            if (StringUtils.isNotEmpty(secondShift) && secondShift.indexOf('-') != -1) {
                String[] shifts = secondShift.split('-')
                String startShift = shifts[0]
                String[] startArr = startShift.split(':')
                String secondShiftStartHrStr = startArr[0]
                String secondShiftStartMinStr = startArr[1]
                secondShiftStartHr = Integer.valueOf(secondShiftStartHrStr)
                //secondShiftStartMin = Integer.valueOf(secondShiftStartMinStr)

                String endShift = shifts[1]
                String[] endArr = endShift.split(':')
                String secondShiftEndHrStr = endArr[0]
                String secondShiftEndMinStr = endArr[1]
                secondShiftEndHr = Integer.valueOf(secondShiftEndHrStr)
            }

            if (truckStartTime.after(validationStartTime) && truckStartTime.before(validationEndTime)) {
                LOGGER.debug("inside valid scenario")
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
            LocalDateTime lcEndTime = validationEndTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            int endhour = lcEndTime?.getHour()
            LocalDateTime lcStartTime = validationStartTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            int starthour = lcStartTime?.getHour()
            long diff = 0
            long diffMinutes = 0
            // // assuming First Shift is between 6 to 23 // TODO check if the current shift is 1st Shift
            if (truckStartTime.before(validationStartTime) || truckStartTime.after(validationEndTime)) {

                String currentShift = getShiftTimeByGeneralReference(firstShiftStartHr, firstShiftEndHr, secondShiftStartHr, secondShiftEndHr, hour)
                String startShift = getShiftTimeByGeneralReference(firstShiftStartHr, firstShiftEndHr, secondShiftStartHr, secondShiftEndHr, starthour)
                String endShift = getShiftTimeByGeneralReference(firstShiftStartHr, firstShiftEndHr, secondShiftStartHr, secondShiftEndHr, endhour)

                if (truckStartTime.after(validationEndTime) && getShift(hour).equals(SECOND_SHIFT) && getShift(endhour).equals(FIRST_SHIFT)) {
                    if (firstShiftLate) {
                        if (atslotEndSlackTimeMin) {
                            endTimeCal.add(Calendar.MINUTE, Integer.valueOf(atslotEndSlackTimeMin));
                            //TODO use a temp
                            if (truckStartTime > endTimeCal.getTime()) {
                                diff = truckStartTime.getTime() - endTimeCal.getTime().getTime()
                                diffMinutes = diff / (60 * 1000);
                                RoadBizUtil.appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("ERROR_APPOINTMENT_TIME_PASSED"), apptNbr, validationStartTime, validationEndTime, diffMinutes, truckStartTime)
                                // Throw error - ERROR_APPOINTMENT_TIME_PASSED
                            }
                        }
                    } else {
                        if (truckStartTime > endTimeCal.getTime()) {
                            diff = truckStartTime.getTime() - endTimeCal.getTime().getTime()
                            diffMinutes = diff / (60 * 1000);
                            RoadBizUtil.appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("ERROR_APPOINTMENT_TIME_PASSED"), apptNbr, validationStartTime, validationEndTime, diffMinutes, truckStartTime)
                            // Throw error - ERROR_APPOINTMENT_TIME_PASSED
                        }
                    }
                } else if (truckStartTime.before(validationStartTime) && getShift(hour).equals(FIRST_SHIFT) && getShift(starthour).equals(SECOND_SHIFT)) {
                    //else if (truckStartTime.before(validationStartTime) && currentShift.equals(FIRST_SHIFT) && startShift.equals(SECOND_SHIFT)){
                    if (secondShiftEarly) {
                        if (atslotStartSlackTimeMin) {
                            startTimeCal.add(Calendar.MINUTE, -(Integer.valueOf(atslotStartSlackTimeMin)));
                            //TODO use a temp
                            if (truckStartTime < startTimeCal.getTime()) {
                                diff = startTimeCal.getTime().getTime() - truckStartTime.getTime()
                                diffMinutes = diff / (60 * 1000);
                                RoadBizUtil.appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("ERROR_APPOINTMENT_TIME_NOT_YET_REACHED"), apptNbr, validationStartTime, validationEndTime, diffMinutes, truckStartTime)
                                //Throw error - ERROR_APPOINTMENT_TIME_NOT_YET_REACHED
                            }
                        }
                    } else {
                        if (truckStartTime < startTimeCal.getTime()) {
                            diff = startTimeCal.getTime().getTime() - truckStartTime.getTime()
                            diffMinutes = diff / (60 * 1000);
                            RoadBizUtil.appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("ERROR_APPOINTMENT_TIME_NOT_YET_REACHED"), apptNbr, validationStartTime, validationEndTime, diffMinutes, truckStartTime)
                            //Throw error - ERROR_APPOINTMENT_TIME_NOT_YET_REACHED
                        }
                    }
                } else if (getShift(hour).equals(FIRST_SHIFT)) {
                    if (firstShiftEarly) {
                        if (atslotStartSlackTimeMin) {
                            startTimeCal.add(Calendar.MINUTE, -(Integer.valueOf(atslotStartSlackTimeMin)));
                            diff = startTimeCal.getTime().getTime() - truckStartTime.getTime()
                            diffMinutes = diff / (60 * 1000);
                            if (truckStartTime < startTimeCal.getTime()) {
                                RoadBizUtil.appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("ERROR_APPOINTMENT_TIME_NOT_YET_REACHED"), apptNbr, validationStartTime, validationEndTime, diffMinutes, truckStartTime)

                                //Throw error - ERROR_APPOINTMENT_TIME_NOT_YET_REACHED
                            }
                        }
                    } else {
                        diff = startTimeCal.getTime().getTime() - truckStartTime.getTime()
                        diffMinutes = diff / (60 * 1000);
                        if (truckStartTime < startTimeCal.getTime()) {
                            RoadBizUtil.appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("ERROR_APPOINTMENT_TIME_NOT_YET_REACHED"), apptNbr, validationStartTime, validationEndTime, diffMinutes, truckStartTime)

                            //Throw error - ERROR_APPOINTMENT_TIME_NOT_YET_REACHED
                        }
                    }

                    if (firstShiftLate) {
                        if (atslotEndSlackTimeMin) {
                            endTimeCal.add(Calendar.MINUTE, Integer.valueOf(atslotEndSlackTimeMin));
                            //TODO use a temp
                            if (truckStartTime > endTimeCal.getTime()) {
                                diff = truckStartTime.getTime() - endTimeCal.getTime().getTime()
                                diffMinutes = diff / (60 * 1000);
                                RoadBizUtil.appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("ERROR_APPOINTMENT_TIME_PASSED"), apptNbr, validationStartTime, validationEndTime, diffMinutes, truckStartTime)
                                // Throw error - ERROR_APPOINTMENT_TIME_PASSED
                            }
                        }
                    } else {
                        if (truckStartTime > endTimeCal.getTime()) {
                            diff = truckStartTime.getTime() - endTimeCal.getTime().getTime()
                            diffMinutes = diff / (60 * 1000);
                            RoadBizUtil.appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("ERROR_APPOINTMENT_TIME_PASSED"), apptNbr, validationStartTime, validationEndTime, diffMinutes, truckStartTime)
                            // Throw error - ERROR_APPOINTMENT_TIME_PASSED
                        }
                    }
                } // TODO check if the current shift is 2nd Shift
                else if (getShift(hour).equals(SECOND_SHIFT)) {
                    if (secondShiftEarly) {
                        if (atslotStartSlackTimeMin) {
                            startTimeCal.add(Calendar.MINUTE, -(Integer.valueOf(atslotStartSlackTimeMin)));
                            //TODO use a temp
                            if (truckStartTime < startTimeCal.getTime()) {
                                diff = startTimeCal.getTime().getTime() - truckStartTime.getTime()
                                diffMinutes = diff / (60 * 1000);
                                RoadBizUtil.appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("ERROR_APPOINTMENT_TIME_NOT_YET_REACHED"), apptNbr, validationStartTime, validationEndTime, diffMinutes, truckStartTime)
                                //Throw error - ERROR_APPOINTMENT_TIME_NOT_YET_REACHED
                            }
                        }
                    } else {
                        if (truckStartTime < startTimeCal.getTime()) {
                            diff = startTimeCal.getTime().getTime() - truckStartTime.getTime()
                            diffMinutes = diff / (60 * 1000);
                            RoadBizUtil.appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("ERROR_APPOINTMENT_TIME_NOT_YET_REACHED"), apptNbr, validationStartTime, validationEndTime, diffMinutes, truckStartTime)
                            //Throw error - ERROR_APPOINTMENT_TIME_NOT_YET_REACHED
                        }

                    }

                    if (secondShiftLate) {
                        if (atslotEndSlackTimeMin) {
                            endTimeCal.add(Calendar.MINUTE, Integer.valueOf(atslotEndSlackTimeMin));
                            //TODO use a temp
                            if (truckStartTime > endTimeCal.getTime()) {
                                diff = truckStartTime.getTime() - endTimeCal.getTime().getTime()
                                diffMinutes = diff / (60 * 1000);
                                RoadBizUtil.appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("ERROR_APPOINTMENT_TIME_PASSED"), apptNbr, validationStartTime, validationEndTime, diffMinutes, truckStartTime)
                                // Throw error - ERROR_APPOINTMENT_TIME_PASSED
                            }
                        }
                    } else {
                        if (truckStartTime > endTimeCal.getTime()) {

                            diff = truckStartTime.getTime() - endTimeCal.getTime().getTime()
                            diffMinutes = diff / (60 * 1000);
                            RoadBizUtil.appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("ERROR_APPOINTMENT_TIME_PASSED"), apptNbr, validationStartTime, validationEndTime, diffMinutes, truckStartTime)
                            // Throw error - ERROR_APPOINTMENT_TIME_PASSED
                        }

                    }
                }//During 2nd shift with end time in 1st shift

            }

        }
    }

    private String getShift(int targetTime) {
        String shift = null
        if (targetTime >= 6 && targetTime < 18) {
            shift = FIRST_SHIFT
        } else if ((targetTime >= 18 && targetTime < 24) || (targetTime >= 0 && targetTime < 3)) {
            shift = SECOND_SHIFT
        }
        return shift
    }

    private String getShiftTimeByGeneralReference(int firstStartTime, int firstEndTime, int secondStartTime, int secondEndTime, int currentTime) {
        String shift = null
        // 1st start 6 to end 18
        //current time 6 6>=6 - 6<18
        // 2nd start 18 to end 24
        //current time 18 18>=18 - 18<24  // 18>=0 - 18<3 --false?
        if (currentTime >= firstStartTime && currentTime < firstEndTime) {
            shift = FIRST_SHIFT
        } else if ((currentTime >= secondStartTime && currentTime < secondEndTime)) {
            shift = SECOND_SHIFT
        }


        return shift
    }

    private TruckVisitAppointment getTVA(String inLicenseNbr) {
        TruckVisitAppointment truckVisitAppointment = null
        DomainQuery dq = QueryUtils.createDomainQuery("TruckVisitAppointment")
                .addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("tvapptTruckId"), inLicenseNbr))
                .addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("tvapptState"), AppointmentStateEnum.CREATED))
                .addDqOrdering(Ordering.desc(MetafieldIdFactory.valueOf("tvapptCreated")))
        List<TruckVisitAppointment> truckVisitAppointments = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        if (truckVisitAppointments.size() > 0) {
            truckVisitAppointment = (TruckVisitAppointment) truckVisitAppointments?.get(0)

        }
        return truckVisitAppointment
    }

    private GateAppointment getAppt(String inLicenseNbr) {
        GateAppointment appt = null
        DomainQuery dq = QueryUtils.createDomainQuery("GateAppointment")
                .addDqPredicate(PredicateFactory.eq(RoadApptsField.GAPPT_TRUCK_LICENSE_NBR, inLicenseNbr))
                .addDqPredicate(PredicateFactory.eq(RoadApptsField.GAPPT_STATE, AppointmentStateEnum.CREATED))
                .addDqOrdering(Ordering.desc(RoadApptsField.GAPPT_CREATED))
        List<GateAppointment> gateAppts = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        if (gateAppts.size() > 0) {
            appt = (GateAppointment) gateAppts?.get(0)

        }
        return appt
    }

    private static final String FIRST_SHIFT = "1st"
    private static final String SECOND_SHIFT = "2nd"
    private static Logger LOGGER = Logger.getLogger(ITSValidateTruckArrivalGateTaskInterceptor.class)
}
