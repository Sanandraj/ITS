package ITS.Enhancements


/*
 * Copyright (c) 2019. Code Written by WeserveTech LLC. All Rights Reserved.
 */

import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.BillingExtractEntityEnum
import com.navis.argo.business.atoms.CalendarTypeEnum
import com.navis.argo.business.atoms.GuaranteeOverrideTypeEnum
import com.navis.argo.business.atoms.GuaranteeTypeEnum
import com.navis.argo.business.calendar.ArgoCalendar
import com.navis.argo.business.calendar.ArgoCalendarEvent
import com.navis.argo.business.calendar.ArgoCalendarEventType
import com.navis.argo.business.calendar.ArgoCalendarUtil
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.extract.Guarantee
import com.navis.external.argo.AbstractGroovyWSCodeExtension
import com.navis.framework.business.atoms.AppCalendarIntervalEnum
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat

/**
 *
 <groovy class-name="ITSExtendedDwellDatesGroovyWSCodeExtension" class-location="code-extension">
 <parameters>
 <parameter id="PTD" value="28-12-2022T12:00:00 +0530"/>
 <parameter id="EXTRACT_GKEY" value="540196"/>
 </parameters>
 </groovy>
 *
 * This groovy sends back the ufvGkey (if the unit has active ufv),
 * else sends back return value as 'FAIL'
 */

@SuppressWarnings(["GroovyUnusedAssignment", "unused"])
class ITSExtendedDwellDatesGroovyWSCodeExtension extends AbstractGroovyWSCodeExtension {
    public static final String PTD = 'PTD'
    public static final String EXTRACT_GKEY = 'EXTRACT_GKEY'
    public static final String FAIL = 'FAIL'

    public String execute(Map<String, Object> inParams) {
        LOG.setLevel(Level.DEBUG)
        LOG.debug("ITSExtendedDwellDatesGroovyWSCodeExtension begins")
        LOG.debug( "inParams" + inParams.toString())
        if (null == inParams) {
            LOG.debug("Input parameters are Null, cannot process further. Exiting with status FAIL")
            return FAIL
        } else if (null != inParams && inParams.containsKey(PTD) && inParams.containsKey(EXTRACT_GKEY)) {
            String finalProposedPtdStr =inParams.get(PTD);
            String extractGkeyStr = inParams.get(EXTRACT_GKEY)
            if (StringUtils.isEmpty(finalProposedPtdStr) || StringUtils.isEmpty(extractGkeyStr)) {
                LOG.debug("Input parameters don't have value for property PTD, cannot process further. Exiting with status FAIL")
                return FAIL
            }

            Long extractGkey = Long.valueOf(extractGkeyStr)
            Date finalProposedPtd = parseDate(XML_DATE_TIME_ZONE_FORMAT, finalProposedPtdStr)

            ChargeableUnitEvent cue = ChargeableUnitEvent.hydrate((Serializable)extractGkey)

            LOG.debug("finalProposedPtd"+finalProposedPtd)
            Calendar calendar = Calendar.getInstance(TimeZone.getDefault())
            Map<Date, String> calendarEventsMap = new HashMap<Date, String>()
            Date startDate = cue.getBexuFlexDate01()
            /*if(cue.getBexuPaidThruDay() != null){
                startDate = getPreviousPTDPlusOne(cue.getBexuPaidThruDay())
            }*/
            calendarEventsMap = getCalendarGratisDates(startDate, finalProposedPtd)

            Set<String> guaranteeSet = Guarantee.getListOfGuaranteesOnly(BillingExtractEntityEnum.INV, (Serializable)extractGkey)
            Set<String> waiverSet = Guarantee.getListOfWaiversOnly(BillingExtractEntityEnum.INV, (Serializable)extractGkey)
            if (!CollectionUtils.isEmpty(waiverSet)) {
                for (String waiverId : waiverSet) {
                    if (waiverId) {
                        Guarantee waiver = Guarantee.findGuaranteeByGuaranteeId(waiverId)
                        calendarEventsMap = addDatesToCalendar(waiver, calendarEventsMap)
                    }
                }
            }
            if (!CollectionUtils.isEmpty(guaranteeSet)) {
                for (String guaranteeId : guaranteeSet) {
                    if (guaranteeId) {
                        Guarantee guarantee = Guarantee.findGuaranteeByGuaranteeId(guaranteeId)
                        calendarEventsMap = addDatesToCalendar(guarantee, calendarEventsMap)
                    }
                }
            }

            LOG.debug("Success calendarEventsMap.toString()"+calendarEventsMap.toString())
            String mapStr = calendarEventsMap.toString()

            return calendarEventsMap.toMapString()

        }
    }
    Date getPreviousPTDPlusOne(Date previousPTD) {
        TimeZone tz = (ContextHelper.getThreadUserTimezone() == null) ? TimeZone.getDefault() : ContextHelper.getThreadUserTimezone()
        Calendar calendar = Calendar.getInstance(tz);
        calendar.setTime(previousPTD);
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return calendar.getTime()
    }
    private Map<Date, String> addDatesToCalendar(Guarantee guarantee, Map<Date, String> calendarEventMap) {
        TimeZone tz = (ContextHelper.getThreadUserTimezone() == null) ? TimeZone.getDefault() : ContextHelper.getThreadUserTimezone()
        Calendar guaranteeStartDate = Calendar.getInstance(tz);
        Calendar guaranteeEndDate = Calendar.getInstance(tz);
        guaranteeEndDate.setTime(guarantee.getGnteGuaranteeEndDay())
        guaranteeStartDate.setTime(guarantee.getGnteGuaranteeStartDay())
        Date testDate = guaranteeStartDate.getTime()
        Date endDate = guaranteeEndDate.getTime()
        while (testDate.before(endDate) || testDate.equals(endDate)) {
            if (GuaranteeTypeEnum.WAIVER.equals(guarantee.getGnteGuaranteeType())) {
                if (GuaranteeOverrideTypeEnum.FREE_NOCHARGE.equals(guarantee.getGnteOverrideValueType()) && calendarEventMap.get(testDate) == null) {
                    calendarEventMap.put(testDate, "WAIVER_FREE_NO_CHARGE")
                }
            } else if (GuaranteeTypeEnum.OAC.equals(guarantee.getGnteGuaranteeType()) && calendarEventMap.get(testDate) == null) {
                calendarEventMap.put(testDate, "Guarantee")
            }
            guaranteeStartDate.add(Calendar.DAY_OF_MONTH, 1)
            testDate = guaranteeStartDate.getTime()
        }
        return calendarEventMap


    }
    @Nullable
    private Date parseDate(DateFormat targetDateFormat, String ptdStr) {
        if (ptdStr == null || ptdStr.isEmpty()) {
            return null;
        }
        try {
            return targetDateFormat.parse(ptdStr);
        } catch (ParseException e) {
            LOG.debug("Cannot parse the given date " + ptdStr + "due to " + e);
            return null;
        }
    }

    private Map<Date, String> getCalendarGratisDates(Date inStartDate, Date inEndDate) {
        Map<Date, String> gratisDates = new HashMap<>()
        ArgoCalendar argoCalndr = ArgoCalendar.findDefaultCalendar(CalendarTypeEnum.STORAGE);
        if (argoCalndr == null) {
            LOG.debug("getCalendarGratisDates: No Storage Calendar found");
            return gratisDates;
        }
        TimeZone tz = (ContextHelper.getThreadUserTimezone() == null) ? TimeZone.getDefault() : ContextHelper.getThreadUserTimezone()
        ArgoCalendarEventType[] calEventTypes = new ArgoCalendarEventType[1];
        calEventTypes[0] = ArgoCalendarEventType.findByName("GRATIS_DAY");
        List<ArgoCalendarEvent> gratisEvents = ArgoCalendarUtil.getEvents(calEventTypes, argoCalndr);
        int gratisDays = 0;
        //inEndDate = adjustWithCalendarHolidays(gratisEvents, calEventTypes, inStartDate, inEndDate, inForStr);
        for (ArgoCalendarEvent calEvent : gratisEvents) {
            Date eventStartDate = calEvent.getArgocalevtOccurrDateStart();
            Calendar instance = Calendar.getInstance(tz);
            instance.setTime(eventStartDate);
            eventStartDate = instance.getTime();

            Date eventEndDate = calEvent.getArgocalevtRecurrDateEnd();
            if (eventEndDate == null) {
                eventEndDate = eventStartDate;
            }

            if ((eventStartDate.after(inStartDate) && eventStartDate.before(inEndDate)) || eventStartDate.equals(inStartDate)) {
                LOG.debug("getCalendarGratisDays: Match found for event Name = " + calEvent.getArgocalevtName());
            }
            Calendar eStartDate = Calendar.getInstance(tz);
            Calendar eEndDate = Calendar.getInstance(tz);
            if (eventStartDate != null) {
                eStartDate.setTime(eventStartDate);
            }

            if (eventEndDate != null) {
                eEndDate.setTime(eventEndDate);
            }

            while ((eStartDate.getTime().before(inEndDate)) || (eStartDate.getTime().equals(inEndDate))) {
                if (eStartDate.getTime().equals(inStartDate) || eStartDate.getTime().after(inStartDate)) {
                    gratisDays++;
                    gratisDates.put(eStartDate.getTime(), calEvent.getArgocalevtName())
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

        LOG.debug("getCalendarGratisDates: Total Gratis days = " + gratisDays);
        return gratisDates;
    }
    private static final Logger LOG = Logger.getLogger(this.class)
    DateFormat XML_DATE_TIME_ZONE_FORMAT = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss Z");
    DateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy")
}
