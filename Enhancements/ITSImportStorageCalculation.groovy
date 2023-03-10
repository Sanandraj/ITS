package ITS.Enhancements

import com.navis.argo.ArgoExtractEntity
import com.navis.argo.ArgoExtractField
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.BillingExtractEntityEnum
import com.navis.argo.business.atoms.CalendarTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.calendar.ArgoCalendar
import com.navis.argo.business.calendar.ArgoCalendarEvent
import com.navis.argo.business.calendar.ArgoCalendarEventType
import com.navis.argo.business.calendar.ArgoCalendarUtil
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.extract.Guarantee
import com.navis.framework.business.atoms.AppCalendarIntervalEnum
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.portal.query.QueryFactory
import com.navis.inventory.business.units.StorageRule
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.business.units.UnitStorageCalculation
import com.navis.inventory.external.inventory.AbstractStorageCalculation
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.time.*

/**
Created by annalakshmig@weservetech.com
 */
public class ITSImportStorageCalculation extends AbstractStorageCalculation {


    @Override
    void unitStorageCalculationExtension(Map map) {

    }

    @Override
    public void beforeUnitStorageCalculationExtension(Map map) {
        LOG.setLevel(Level.DEBUG)
        logMsg("ITSImportStorageCalculation - beforeUnitStorageCalculationExtension - starts: " + ArgoUtils.timeNow());
        UnitStorageCalculation unitStorageCalculation = (UnitStorageCalculation) map.get("UnitStorageCalculation");
        if (unitStorageCalculation == null) {
            logMsg("Stop execution! - The unit Storage Calculation object is null.");
            return;
        }

        String chargeFor = unitStorageCalculation.getChargeFor();

        //It only deals with Line Storage calculation
        if (!("LINE_STORAGE").equals(chargeFor)) {
            logMsg("Stop execution of ITSImportStorageCalculation - This groovy is applicable only for LINE STORAGE events.");
            return;
        }

        UnitFacilityVisit ufv = (UnitFacilityVisit) map.get("UnitFacilityVisit");
        if (ufv == null) {
            logMsg("Stop execution! - UFV is null.");
            return;
        }

        Unit unit = ufv.getUfvUnit();
        if (null == unit) {
            logMsg("Stop execution! - unit is null.");
            return;
        }
        //It only deals with Import
        if (!UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory())) {
            logMsg("Stop execution - This groovy is applicable for IMPORTS only.");
            return;
        }

        //If LFD Overridden is manually set, set it as the last free day. No need to calculate any extended logic
        if (ufv.getUfvLastFreeDay() != null) {
            unitStorageCalculation._lastFreeDay = ufv.getUfvLastFreeDay();
            logMsg("Set manually overridden LFD: " + ufv.getUfvLastFreeDay());
            return;
        }
        Date calculationStartDate = null
        Date firstDeliverableDate = ufv.getUfvFlexDate01()
        if (firstDeliverableDate != null) {
            calculationStartDate = getCalculationStartDate(firstDeliverableDate, unit)
        }
        if (calculationStartDate == null) {
            logMsg("CalculationStartDate is Null");
            return;
        }
        int initialFreeDaysAllowed = unitStorageCalculation.getFreeDaysAllowed();
        int savedInitFreeDaysAllowed = initialFreeDaysAllowed;
        logMsg("Unit: " + unit.getUnitId() + " ,initialFreeDaysAllowed: " + initialFreeDaysAllowed + ", savedInitFreeDaysAllowed= " + savedInitFreeDaysAllowed);
        Date thisStartDate = calculationStartDate
        logMsg("thisStartDate: " + thisStartDate);
        Date lastFreeDay = null;
        //get last free days
        if (ufv.getUfvTimeOut() != null) {
            lastFreeDay = ufv.getUfvTimeOut();
        } else {
            lastFreeDay = addDays(thisStartDate, initialFreeDaysAllowed);
        }
        int exemptDays = 0
        Map<Date,String> exemptDatesMap = new HashMap<>()
        exemptDatesMap = this.getCalendarExemptDays(unitStorageCalculation, thisStartDate, lastFreeDay)
        exemptDays = exemptDatesMap != null ? exemptDatesMap.size() : 0
        int exemptDaysRecalculated = 0
               if(exemptDays > 0) {
                    lastFreeDay = addDays(thisStartDate, (initialFreeDaysAllowed + exemptDays))
                    exemptDatesMap = this.getCalendarExemptDays(unitStorageCalculation, thisStartDate, lastFreeDay)
                    exemptDaysRecalculated = exemptDatesMap != null ? exemptDatesMap.size() : 0
                    while (exemptDays != exemptDaysRecalculated) {
                        lastFreeDay = addDays(thisStartDate, (initialFreeDaysAllowed + exemptDaysRecalculated))
                        exemptDays = exemptDaysRecalculated
                        exemptDatesMap = this.getCalendarExemptDays(unitStorageCalculation, thisStartDate, lastFreeDay)
                        exemptDaysRecalculated = exemptDatesMap != null ? exemptDatesMap.size() : 0

                    }
                }



        logMsg("initialFreeDaysAllowed after exempt: " + initialFreeDaysAllowed + ", savedInitFreeDaysAllowed= " + savedInitFreeDaysAllowed);

        boolean freeDaysRecalculated = false;
        int freeDaysDueToPlacementOfUnitInNDB = 0
        TimeZone threadUserTimeZone = (ContextHelper.getThreadUserTimezone() == null) ? TimeZone.getDefault() : ContextHelper.getThreadUserTimezone()
        //Calendar calendarTestDate = Calendar.getInstance(threadUserTimeZone);
        Date testDate
        ChargeableUnitEvent cue = fetchCUE(ufv)
        if (cue != null) {
            List<Guarantee> guaranteeList = (List<Guarantee>) Guarantee.getListOfGuarantees(BillingExtractEntityEnum.INV, cue.getPrimaryKey())
            if (!CollectionUtils.isEmpty(guaranteeList)) {
                for (Guarantee guarantee : guaranteeList) {
                    if (guarantee.isWavier() /*&& "Waived for NDB".equals(guarantee.getGnteNotes())*/) {
                        testDate = guarantee.getGnteGuaranteeStartDay()
                        // increment the free days if waived day is not an exempt day and it it falls with in calculation Start date and Line LFD
                        while (guarantee.getGnteGuaranteeEndDay() != null && testDate <= guarantee.getGnteGuaranteeEndDay()) {
                            if (!isExemptDay(testDate, exemptDatesMap) && ArgoUtils.datesInRange(testDate, testDate, thisStartDate, lastFreeDay, threadUserTimeZone)) {
                                freeDaysDueToPlacementOfUnitInNDB++
                                freeDaysRecalculated = true
                            }
                            testDate = addDays(testDate, 1)
                        }
                    }
                }
            }
        }
        int recalculatedFreeDays = savedInitFreeDaysAllowed + freeDaysDueToPlacementOfUnitInNDB


        //Only update the freeDaysAllowed to unit storage calculator if it is recomputed
        if (freeDaysRecalculated) {
            unitStorageCalculation._freeDaysAllowed = recalculatedFreeDays;
            logMsg("The Storage Free days allowed is reset to: " + recalculatedFreeDays + " existing exemptDays= " + exemptDays);
        }

        logMsg("ITSImportStorageCalculation - beforeUnitStorageCalculationExtension - ends: " + ArgoUtils.timeNow());
    }

    boolean isExemptDay(Date testDate, Map<Date, String> exemptDatesMap) {

        Calendar calendar = getCalendar(testDate)
        if (exemptDatesMap!= null && exemptDatesMap.size() > 0 && exemptDatesMap.get(calendar.getTime()) != null) {
            return true
        }
        return false
    }
    Calendar getCalendar(Date testDate) {
        Calendar calendar = Calendar.getInstance(ContextHelper.getThreadUserTimezone());
        calendar.setTime(testDate);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar
    }
    private Date getCalculationStartDate(Date firstDeliverableDate, Unit unit) {

        LocalDateTime lcDate = firstDeliverableDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
        if (!StringUtils.isEmpty(unit.getUnitFlexString06()) && "Y".equalsIgnoreCase(unit.getUnitFlexString06())) {
            return firstDeliverableDate
        }
        int hour = lcDate.getHour()
        if (hour < 3) {
            ZonedDateTime zonedDateTime = lcDate.withHour(3).withMinute(0).withSecond(0).atZone(ZoneOffset.systemDefault());
            Instant instant = zonedDateTime.toInstant();
            Date finalDate = Date.from(instant);
            return finalDate
        } else {
            ZonedDateTime zonedDateTime = lcDate.plusDays(1).withHour(3).withMinute(0).withSecond(0).atZone(ZoneOffset.systemDefault());
            Instant instant = zonedDateTime.toInstant();
            Date finalDate = Date.from(instant);
            return finalDate
        }
    }

    private ChargeableUnitEvent fetchCUE(UnitFacilityVisit ufv) {
        DomainQuery dq = QueryFactory.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT)
                .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_EVENT_TYPE, ["LINE_STORAGE"]))
                .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_UFV_GKEY, ufv.getUfvGkey()))
                .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_STATUS, ["QUEUED"]))
        return HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq)
    }

    private Map<Date, String> getCalendarExemptDays(UnitStorageCalculation inUsc, Date inStartDate, Date inEndDate) {
        Map<Date, String> exemptDates = new HashMap<>()
        if (inUsc == null) {
            return exemptDates;
        }

        StorageRule storageRule = StorageRule.getStorageRule(inUsc.getStorageRuleTableKey());
        ArgoCalendar argoCalndr = (storageRule != null) ? storageRule.getSruleCalendar() : null;
        if (argoCalndr == null) {
            //argoCalndr = ArgoCalendar.findDefaultCalendar(CalendarTypeEnum.STORAGE);
            argoCalndr = ArgoCalendar.findCalendar("LINE STORAGE CALENDAR");
        }

        if (argoCalndr == null) {
            logMsg("getCalendarExemptDays: No Storage Calendar found");
            return exemptDates;
        }

        ArgoCalendarEventType[] calEventTypes = new ArgoCalendarEventType[1];
        calEventTypes[0] = ArgoCalendarEventType.findByName("EXEMPT_DAY");
        List<ArgoCalendarEvent> exemptEvents = ArgoCalendarUtil.getEvents(calEventTypes, argoCalndr);
        int exemptDays = 0;

        for (ArgoCalendarEvent calEvent : exemptEvents) {
            Date eventStartDate = calEvent.getArgocalevtOccurrDateStart();
            Calendar instance = Calendar.getInstance(ContextHelper.getThreadUserTimezone());
            instance.setTime(eventStartDate);
            eventStartDate = instance.getTime();

            Date eventEndDate = calEvent.getArgocalevtRecurrDateEnd();
            if (eventEndDate == null) {
                eventEndDate = eventStartDate;
            }

            if ((eventStartDate.after(inStartDate) && eventStartDate.before(inEndDate)) || eventStartDate.equals(inStartDate)) {

                logMsg("getCalendarExemptDays: Match found for event Name = " + calEvent.getArgocalevtName());
            }
            Calendar eStartDate = Calendar.getInstance(ContextHelper.getThreadUserTimezone());
            Calendar eEndDate = Calendar.getInstance(ContextHelper.getThreadUserTimezone());
            if (eventStartDate != null) {
                eStartDate.setTime(eventStartDate);
            }

            if (eventEndDate != null) {
                eEndDate.setTime(eventEndDate);
            }

            while ((eStartDate.getTime().before(inEndDate)) || (eStartDate.getTime().equals(inEndDate))) {
                if (eStartDate.getTime().equals(inStartDate) || eStartDate.getTime().after(inStartDate)) {
                    exemptDays++;
                    exemptDates.put(eStartDate.getTime(), calEvent.getArgocalevtName())
                }

                if (calEvent.getArgocalevtInterval().equals(AppCalendarIntervalEnum.ONCE)) {
                    eStartDate.setTime(inEndDate);
                    eStartDate.add(Calendar.DATE, 1); //so that the while loop breaks
                } else if (calEvent.getArgocalevtInterval().equals(AppCalendarIntervalEnum.DAILY)) {
                    eStartDate.add(Calendar.DATE, 1);
                } else if (calEvent.getArgocalevtInterval().equals(AppCalendarIntervalEnum.WEEKLY)) {
                    eStartDate.add(Calendar.WEEK_OF_YEAR, 1);
                } else if (calEvent.getArgocalevtInterval().equals(AppCalendarIntervalEnum.ANNUALLY)) {
                    eStartDate.add(Calendar.YEAR, 1);
                } else {//so that the while loop breaks
                    eStartDate.setTime(eEndDate.getTime());
                    eStartDate.add(Calendar.DATE, 1);
                }
            }
        }

        logMsg("getCalendarExemptDays: Total Exempt days = " + exemptDays);

        return exemptDates;
    }

    private Date addDays(Date inDate, int inDays) {
        Calendar cl = Calendar.getInstance(ContextHelper.getThreadUserTimezone());
        cl.setTime(inDate);
        cl.add(Calendar.DATE, inDays);
        return cl.getTime();
    }


    /**
     * Add ONE day to the given date
     * @param inDate
     * @return
     */
    private Date getDatePlusOne(Date inDate) {
        Calendar datePlusOne = Calendar.getInstance(ContextHelper.getThreadUserTimezone());
        datePlusOne.setTime(inDate);
        datePlusOne.add(Calendar.DAY_OF_MONTH, 1);

        return datePlusOne.getTime();
    }

    private static final Logger LOG = Logger.getLogger(this.class);

    void logMsg(String msg) {
        LOG.debug("ITSImportStorageCalculation " + msg)
    }
    //private final static Logger LOGGER = Logger.getLogger(ITSImportStorageCalculation.class)
}


