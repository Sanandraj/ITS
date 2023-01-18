/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.ServiceQuantityUnitEnum
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.services.IServiceExtract
import com.navis.argo.webservice.external.ServiceLocator
import com.navis.argo.webservice.external.ServiceSoap
import com.navis.billing.BillingConfig
import com.navis.billing.BillingField
import com.navis.billing.BillingPropertyKeys
import com.navis.billing.business.model.Invoice
import com.navis.billing.business.model.InvoiceItem
import com.navis.billing.business.model.TariffRate
import com.navis.billing.business.model.TariffRateTier
import com.navis.external.billing.AbstractTariffRateCalculatorInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.FieldChanges
import com.navis.framework.util.BizFailure
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jdom.Document
import org.jdom.Element
import org.jdom.input.SAXBuilder
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import javax.xml.rpc.Stub
import java.rmi.RemoteException
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId

/**
 * @Author: mailto:annalakshmig@weservetech.com, AnnaLakshmi G; Date: 16/SEP/2022
 *
 * Requirements : IP-407,7-10 Requirements for Waiver or Guarantee Extended Dwell Fee.
 *                IP-14,3-6 Extended Dwell Fee.  Details in section 3.6.
 *
 * @Inclusion Location : Incorporated as a code extension of the type TARIFF_RATE_CALCULATOR
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name:ITSExtendedDwellCalculator
 *     Code Extension Type:TARIFF_RATE_CALCULATOR
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *
 *  S.No    Modified Date        Modified By       Jira      Description
 */

class ITSExtendedDwellCalculator extends AbstractTariffRateCalculatorInterceptor {

    @Override
    void calculateRate(Map inOutMap) {
        LOG.setLevel(Level.DEBUG)
        LOG.debug("ITSExtendedDwellCalculator starts")
        if (inOutMap == null) {
            LOG.debug("Groovy called with null, ignoring the call ")
            return;
        }
        Invoice inv = null;
        IServiceExtract extract = null;
        TariffRate tariffRate = null
        Date cueStartTime = null
        Date lastPTD = null
        String ptdStr = null
        inv = (Invoice) inOutMap.get("inInvoice")
        Object extractEvent = inOutMap.get("inExtractEvent");
        tariffRate = (TariffRate) inOutMap.get("inTariffRate")

        if (extractEvent != null && extractEvent instanceof ChargeableUnitEvent) {
            extract = (ChargeableUnitEvent) inOutMap.get("inExtractEvent");
        } else {
            return
        }
        if (extract != null && !dwell_event.equalsIgnoreCase(extract.getEventType())) {
            return
        }
        if (null == tariffRate) return
        InvoiceItem invoiceItem = createTieredInvItems(inv, extract, tariffRate);
        if (invoiceItem != null) {
            inOutMap.put("outAmount", invoiceItem.getItemAmount() != null ? invoiceItem.getItemAmount() : 0.0);
            FieldChanges invItemFieldChanges = new FieldChanges(invoiceItem.getValueObject())
            invItemFieldChanges.removeFieldChange(BillingField.ITEM_GKEY);
            inOutMap.put("invItemChanges", invItemFieldChanges);
            invoiceItem.setFieldValue(BillingField.ITEM_SERVICE_EXTRACT_GKEY, null);

            HibernateApi hibernateApi = Roastery.getHibernateApi();
            hibernateApi.save(invoiceItem)

            hibernateApi.delete(invoiceItem);
        }
    }

    InvoiceItem createTieredInvItems(Invoice invoice, ChargeableUnitEvent dwellEvent, TariffRate tariffRate) {
        TimeZone threadUserTimeZone = (ContextHelper.getThreadUserTimezone() == null) ? TimeZone.getDefault() : ContextHelper.getThreadUserTimezone()
        Calendar calendar = Calendar.getInstance(threadUserTimeZone);
        Calendar endDate = Calendar.getInstance(threadUserTimeZone);
        Date finalProposedPTD = null
        List<TariffRateTier> tariffRateTiersList = new ArrayList<>()
        Set<TariffRateTier> tariffRateTiers = (Set<TariffRateTier>) tariffRate.getRateTiers()
        if (!CollectionUtils.isEmpty(tariffRateTiers)) {
            for (TariffRateTier tariffRateTier : tariffRateTiers) {
                tariffRateTiersList.add(tariffRateTier)

            }
            Collections.sort(tariffRateTiersList, new Comparator<TariffRateTier>() {
                @Override
                int compare(TariffRateTier tier1, TariffRateTier tier2) {
                    return tier1.getTierMinQuantity().compareTo(tier2.getTierMinQuantity())
                }
            });

        } else {
            LOG.debug("No tiers for the given Tariff Rate")
            return null
        }
        Set<InvoiceItem> invoiceItemsSet = new HashSet<InvoiceItem>();

        Date dwellStartDate = dwellEvent.getBexuFlexDate01();

        finalProposedPTD = invoice.getInvoicePaidThruDay();
        if (null == finalProposedPTD) {
            finalProposedPTD = getZonedDate(ArgoUtils.timeNow())
        } else {
            finalProposedPTD = getZonedDate(finalProposedPTD)

        }
        LOG.debug("final ptd" + finalProposedPTD)

        Date previousPaidThruDay = dwellEvent.getBexuPaidThruDay()
        String previousTier = null
        String previousTierPaidQty = 0
        Calendar calendarPtd = Calendar.getInstance(threadUserTimeZone);
        calendarPtd.setTime(finalProposedPTD);
        finalProposedPTD = calendarPtd.getTime()

        String wsResponseString
        wsResponseString = getcalEvents(finalProposedPTD, dwellEvent.getBexuGkey())
        Map<Date, String> wsResponseMap = new HashMap<>()

        wsResponseMap = getWSResponseMap(wsResponseString)
        LOG.debug("wsResponseMap" + wsResponseMap.toString())
        if (previousPaidThruDay != null) {
            String lastInvoiceTierAndPaidQtyStr = deriveLastInvoiceTierAndPaidQty(dwellStartDate, previousPaidThruDay, wsResponseMap, tariffRateTiersList)
            if (!StringUtils.isEmpty(lastInvoiceTierAndPaidQtyStr)) {
                previousTier = lastInvoiceTierAndPaidQtyStr.split(",")[0]
                previousTierPaidQty = lastInvoiceTierAndPaidQtyStr.split(",")[1]
            }

        }
        Date eventPerformedFrom = null;
        Date eventPerformedTo = null;
        Date prevTierToDate = null;
        boolean isUnPaidQtyOfPreviousTierProcessed = false
        if (CollectionUtils.isEmpty(tariffRateTiersList)) {
            return null
        }


        int tierNumberInLastInvoice = 0;
        Double paidQtyinLastInvoice = 0.0
        if (!StringUtils.isEmpty(previousTier)) {
            tierNumberInLastInvoice = Integer.parseInt(previousTier)
        }
        if (!StringUtils.isEmpty(previousTierPaidQty)) {
            paidQtyinLastInvoice = Double.parseDouble(previousTierPaidQty)
        }


        TariffRateTier tariffRateTier = null


        for (int i = tierNumberInLastInvoice; i < tariffRateTiersList.size(); i++) {
            int gratisDaysWithinTier = 0
            int guaranteeDaysWithinTier = 0
            int waivedDaysWithinTier = 0
            //start from the tier in Last Invoice
            tariffRateTier = tariffRateTiersList.get(i)
            Double tierMinQuantity = tariffRateTier.getTierMinQuantity();//   For Tier1 --> 1
            Double tierMaxQuantity = tariffRateTier.findTierMaxQuantity();
            // For Tier1 --> 6; N4 will give max Qty of that tier + 1

            Double presentTierMaxQty = tierMaxQuantity - 1;                // For Tier1 --> 5
            Double presentTierActualQty = 0.0
            if (prevTierToDate != null) {
                calendar.setTime(prevTierToDate);
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                eventPerformedFrom = calendar.getTime()

            } else {
                if (previousPaidThruDay == null) {
                    eventPerformedFrom = dwellStartDate;
                } else {
                    eventPerformedFrom = getPreviousPTDPlusOne(previousPaidThruDay)
                }
            }

            calendar.setTime(eventPerformedFrom)
            Date testDate = calendar.getTime()
            LocalDate lcTestDate = testDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            endDate.setTime(finalProposedPTD)
            Date finalPtd = endDate.getTime()
            LocalDate lcFinalPtd = finalPtd.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            if (new Double(99999999).equals(tierMaxQuantity)) { // Last tier max qty in N4 billing : 9.9999999E7
                presentTierActualQty = differenceInCalendarDays(eventPerformedFrom, finalProposedPTD, threadUserTimeZone);
            } else {
                presentTierActualQty = tierMaxQuantity - tierMinQuantity
            }
            LOG.debug("i" + i)

            if (previousPaidThruDay != null && !isUnPaidQtyOfPreviousTierProcessed) {
                // presentTierActualQty = presentTierMaxQty - paidQtyinLastInvoice
                presentTierActualQty = tierMaxQuantity - tierMinQuantity - paidQtyinLastInvoice
                isUnPaidQtyOfPreviousTierProcessed = true
            }
            if (presentTierActualQty == 0.0) {
                continue
            }
            int daysInTier = 0
            int chargeableDays = 0
            while ((lcTestDate.equals(lcFinalPtd) || lcTestDate.isBefore(lcFinalPtd)) && ((i != tariffRateTiersList.size() && daysInTier < presentTierActualQty) || (i == tariffRateTiersList.size()))) {
                if (isGratisDay(Date.from(lcTestDate.atStartOfDay(ZoneId.systemDefault()).toInstant()), wsResponseMap)) {
                    gratisDaysWithinTier++
                    lcTestDate = lcTestDate.plusDays(1)
                } else if (isWaivedDay(Date.from(lcTestDate.atStartOfDay(ZoneId.systemDefault()).toInstant()), wsResponseMap)) {

                    waivedDaysWithinTier++
                    lcTestDate = lcTestDate.plusDays(1)
                } else if (isGuaranteeDay(Date.from(lcTestDate.atStartOfDay(ZoneId.systemDefault()).toInstant()), wsResponseMap)) {

                    daysInTier++
                    guaranteeDaysWithinTier++
                    lcTestDate = lcTestDate.plusDays(1)
                } else {

                    daysInTier++
                    chargeableDays++
                    lcTestDate = lcTestDate.plusDays(1)
                }
            }

            if (daysInTier == 0) {
                break
            }
            LOG.debug("tierNo::::::::" + i + "presentTierActualQty" + presentTierActualQty)
            if (prevTierToDate != null) {
                calendar.setTime(prevTierToDate);
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                eventPerformedFrom = calendar.getTime()

            } else {
                if (previousPaidThruDay == null) {
                    eventPerformedFrom = dwellStartDate;
                } else {
                    eventPerformedFrom = getPreviousPTDPlusOne(previousPaidThruDay)
                }
            }
            calendar.setTime(Date.from(lcTestDate.atStartOfDay(ZoneId.systemDefault()).toInstant()))
            calendar.add(Calendar.DAY_OF_MONTH, -1)
            eventPerformedTo = calendar.getTime()

            Double ChargeableAmount = chargeableDays * tariffRateTier.getTierAmountPerUnit()

            InvoiceItem invoiceItem = InvoiceItem.createInvoiceItem(invoice, tariffRateTier.getTierTariffRate(), dwellEvent, null, ChargeableAmount, null, null)

            if (invoiceItem == null) {
                return null;
            }
            StringBuilder stringBuilder = new StringBuilder()
            if (gratisDaysWithinTier > 0) {
                stringBuilder.append("GratisDays: ").append(gratisDaysWithinTier).append(".")
            }
            if (waivedDaysWithinTier > 0) {
                stringBuilder.append("WaivedDays: ").append(waivedDaysWithinTier).append(".")
            }
            if (guaranteeDaysWithinTier > 0) {
                stringBuilder.append("GuaranteeDays: ").append(guaranteeDaysWithinTier).append(".")
            }
            String notes = stringBuilder.length() > 0 ? stringBuilder.toString() : "Recorded by Groovy"

            invoiceItem.setFieldValue(BillingField.ITEM_FROM_DATE, eventPerformedFrom);
            invoiceItem.setFieldValue(BillingField.ITEM_TO_DATE, eventPerformedTo);
            invoiceItem.setFieldValue(BillingField.ITEM_QUANTITY, presentTierActualQty);
            invoiceItem.setFieldValue(BillingField.ITEM_QUANTITY_UNIT, ServiceQuantityUnitEnum.ITEMS);
            invoiceItem.setFieldValue(BillingField.ITEM_QUANTITY_BILLED, (Double) chargeableDays);
            invoiceItem.setFieldValue(BillingField.ITEM_RATE_BILLED, tariffRateTier.getTierAmountPerUnit());
            invoiceItem.setFieldValue(BillingField.ITEM_DESCRIPTION, tariffRateTier.getTierDescription());
            invoiceItem.setFieldValue(BillingField.ITEM_NOTES, notes);
            invoiceItem.setFieldValue(BillingField.ITEM_SERVICE_EXTRACT_GKEY, dwellEvent.getServiceExtractGkey());
            invoiceItem.setFieldValue(BillingField.ITEM_GL_CODE, tariffRateTier.getTierOrRateOrTariffGlCode());
            Roastery.getHibernateApi().saveOrUpdate(invoiceItem);
            invoiceItemsSet.add(invoiceItem);

            prevTierToDate = eventPerformedTo;
            LOG.debug("tierNumberInLastInvoice : " + i + " tierMinQuantity : " + tierMinQuantity + " tierMaxQuantity : " + tierMaxQuantity + " presentTierMaxQty :" + presentTierMaxQty + " presentTierActualQty :" + presentTierActualQty + " eventPerformedFrom : " + eventPerformedFrom + " eventPerformedTo : " + eventPerformedTo)

        }

        if (!CollectionUtils.isEmpty(invoiceItemsSet)) {
            Iterator iterator = invoiceItemsSet.iterator()
            InvoiceItem item = iterator.next();
            Set invoiceItems = invoice.getInvoiceInvoiceItems();
            if (!invoiceItems.isEmpty()) {
                invoiceItems.remove(item);
                Roastery.getHibernateApi().update(invoice);
                return item;
            }
        }
        return null;

    }

    @NotNull
    private String generateWSRequest(@NotNull Date ptd, @NotNull Long extractGkey) {
        String inPtd = XML_DATE_TIME_ZONE_FORMAT.format(ptd)

        String request = """
                            <groovy class-name="ITSExtendedDwellDatesGroovyWSCodeExtension" class-location="code-extension">
                                <parameters>
                                    <parameter id="PTD" value="${inPtd}"/>
                                    <parameter id="EXTRACT_GKEY" value="${extractGkey}"/>
                                </parameters>
                            </groovy>
                            """
        return request
    }

    private String deriveLastInvoiceTierAndPaidQty(@NotNull Date startDate, @NotNull Date previousPtd, Map<Date, String> gratisDatesMap, List<TariffRateTier> tariffRateList) {
        TimeZone tz = (ContextHelper.getThreadUserTimezone() == null) ? TimeZone.getDefault() : ContextHelper.getThreadUserTimezone();
        int totalDaysInLastInvoice = (int) differenceInCalendarDays(startDate, previousPtd, tz)
        Calendar startDateCalendar = Calendar.getInstance(tz)
        int noOfGratisOrWaivedDaysInLastInvoice = 0
        LocalDate lcTestDate = startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        LocalDate lcPreviousPtd = previousPtd.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        while ((lcTestDate.equals(lcPreviousPtd) || lcTestDate.isBefore(lcPreviousPtd))) {
            if (isGratisDay(Date.from(lcTestDate.atStartOfDay(ZoneId.systemDefault()).toInstant()), gratisDatesMap)) {
                lcTestDate = lcTestDate.plusDays(1)
                noOfGratisOrWaivedDaysInLastInvoice++

            } else if (isWaivedDay(Date.from(lcTestDate.atStartOfDay(ZoneId.systemDefault()).toInstant()), gratisDatesMap)) {
                lcTestDate = lcTestDate.plusDays(1)
                noOfGratisOrWaivedDaysInLastInvoice++
            } else {
                lcTestDate = lcTestDate.plusDays(1)
            }
        }
        int paidQtyInLastInvoice = totalDaysInLastInvoice - noOfGratisOrWaivedDaysInLastInvoice
        int lastInvoiceTier = 0
        int paidQtyInLastTier = 0
        TariffRateTier tariffRateTier
        StringBuilder sb = new StringBuilder()
        for (int k = tariffRateList.size() - 1; k >= 0; k--) {
            tariffRateTier = tariffRateList.get(k)
            if (tariffRateTier.getTierMinQuantity() <= paidQtyInLastInvoice && paidQtyInLastInvoice < tariffRateTier.findTierMaxQuantity()) {
                lastInvoiceTier = k;
                paidQtyInLastTier = paidQtyInLastInvoice - (int) tariffRateTier.getTierMinQuantity() + 1
                sb.append(lastInvoiceTier).append(",").append(paidQtyInLastTier)
                break
            }
        }
        if (sb.length() != 0) {
            return sb.toString()
        }
        return null

    }

    private Map<Date, String> getWSResponseMap(String wsResponseStr) {
        Map<Date, String> eventMap = new HashMap<>()
        if (!StringUtils.isEmpty(wsResponseStr)) {

            String[] stringArray = StringUtils.split(wsResponseStr.substring(1, wsResponseStr.length() - 1), ",")
            for (String str : stringArray) {
                try {
                    Date gratisDate = parseDate(dateFormat, str.substring(0, str.lastIndexOf(":")).trim())

                    eventMap.put(gratisDate, str.substring(str.lastIndexOf(":") + 1, str.length()))
                } catch (Exception e) {
                    LOG.debug("eventMap exception" + e)
                    return null
                }
            }
        }
        return eventMap
    }

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

    @Nullable
    private String getcalEvents(@NotNull Date ptd, @NotNull Long extractGkey) {
        String requestString = generateWSRequest(ptd, extractGkey);
        String webServiceResponseString = invokeWebServiceRequest(requestString);
        if (null != webServiceResponseString) {
            String responseString = parseWebServiceResponse(webServiceResponseString);
            if (null != responseString) {
                return responseString;
            }
        }
        return null;
    }

    @Nullable
    private String parseWebServiceResponse(String inWebServiceResponse) {
        InputStream is = new ByteArrayInputStream(inWebServiceResponse.getBytes());
        SAXBuilder saxBuilder = new SAXBuilder(false);
        try {
            Document wsResponseDoc = saxBuilder.build(is);
            Element rootElement = wsResponseDoc.getRootElement();
            String status = wsResponseDoc.getRootElement().getAttribute("status").getValue();
            if (status && status.equalsIgnoreCase("3")) {
                return null;

            }
            Element ctvResponseElement = wsResponseDoc.getRootElement().getChild("messages");

            if (rootElement.getChildren() != null) {
                Element resultElement = wsResponseDoc.getRootElement().getChild(RESULT);
                if (null != resultElement) {
                    LOG.debug("<result> tag is present in response")
                    String responseStr = resultElement.getText();
                    if (null == responseStr || (null != responseStr && "FAIL".equals(responseStr))) {
                        LOG.debug("<result> is " + responseStr)
                        return null
                    } else {
                        LOG.debug("<result> is " + responseStr)
                        return responseStr
                    }
                }
            }
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
        return null;
    }

    @Nullable
    private String invokeWebServiceRequest(String inWebServiceRequest) {

        String businessCords = scopeCoOrdinatesString;
        LOG.warn("Incoming web Service Request :: " + inWebServiceRequest + "Business Cords :: " + businessCords);
        String url = null
        String webserviceUser = null
        String webservicePassword = null
        String webServiceResponse;

        webserviceUser = getN4WebServiceUserId()
        webservicePassword = getN4WebServicePassWord()

        ServiceSoap port = this._externalServiceLocator.getServiceSoap(getArgoServiceURL());
        Stub stub = (Stub) port;

        stub._setProperty(Stub.USERNAME_PROPERTY, webserviceUser);

        stub._setProperty(Stub.PASSWORD_PROPERTY, webservicePassword);

        try {
            webServiceResponse = port.basicInvoke(businessCords, inWebServiceRequest);
        } catch (RemoteException rex) {
            LOG.error("Exception when invoking N4 webservice ");
            LOG.error(rex)
            return null
        }

        return webServiceResponse;
    }

    private static URL getArgoServiceURL() {
        String inventoryURL = (String) BillingConfig.N4_WS_SERVICES_URL.getSetting(ContextHelper.getThreadUserContext());
        inventoryURL = inventoryURL.replace("serviceservice", "argobasicservice")
        URL url;
        try {
            url = new URL(inventoryURL);

        } catch (MalformedURLException e) {
            throw BizFailure.create(BillingPropertyKeys.ERRKEY__WEBSERVICE_INVENTORY_URL, e, inventoryURL);
        }
        return url;
    }

    private static String getN4WebServiceUserId() {
        String userId = (String) BillingConfig.N4_WS_USERID.getSetting(ContextHelper.getThreadUserContext());
        if (userId == null || userId.isEmpty()) {
            userId = (String) BillingConfig.N4_WS_USERID.getConfigDefaultValue();
        }
        return userId;
    }

    private static String getN4WebServicePassWord() {
        String passWord = (String) BillingConfig.N4_WS_PASSWORD.getSetting(ContextHelper.getThreadUserContext());
        if (passWord == null || passWord.isEmpty()) {
            passWord = (String) BillingConfig.N4_WS_PASSWORD.getConfigDefaultValue();
        }
        return passWord;
    }


    boolean isGratisDay(Date testDate, Map<Date, String> gratisDatesMap) {

        Calendar calendar = getCalendar(testDate)

        if (gratisDatesMap.get(calendar.getTime()) != null && !gratisDatesMap.get(calendar.getTime()).equals("WAIVER_FREE_NO_CHARGE") && !gratisDatesMap.get(calendar.getTime()).equals("Guarantee")) {

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

    boolean isGuaranteeDay(Date testDate, Map<Date, String> gratisDatesMap) {

        Calendar calendar = getCalendar(testDate)

        if (gratisDatesMap.get(calendar.getTime()) != null && gratisDatesMap.get(calendar.getTime()).equals("Guarantee")) {

            return true
        }
        return false
    }

    boolean isWaivedDay(Date testDate, Map<Date, String> gratisDatesMap) {

        Calendar calendar = getCalendar(testDate)

        if (gratisDatesMap.get(calendar.getTime()) != null && gratisDatesMap.get(calendar.getTime()).equals("WAIVER_FREE_NO_CHARGE")) {
            return true
        }
        return false
    }

    Date getPreviousPTDPlusOne(Date previousPTD) {
        Calendar calendar = Calendar.getInstance(ContextHelper.getThreadUserTimezone());
        calendar.setTime(previousPTD);
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return calendar.getTime()
    }

    Date getZonedDate(Date date) {
        Calendar calendar = Calendar.getInstance(ContextHelper.getThreadUserTimezone());
        calendar.setTime(date);
        return calendar.getTime();
    }

    public static long differenceInCalendarDays(Date inDateA, Date inDateB, TimeZone inTimeZone) {
        long MILLIS_PER_SECOND = 1000L;
        long MILLIS_PER_MINUTE = 60000L;
        long MILLIS_PER_HOUR = MILLIS_PER_MINUTE * 60L;
        long MILLIS_PER_DAY = MILLIS_PER_HOUR * 24L;

        TimeZone tz = (inTimeZone == null) ? TimeZone.getDefault() : inTimeZone;
        Calendar calendar = Calendar.getInstance(tz);
        calendar.setLenient(false);
        calendar.setTime(inDateA);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        Calendar calendar2 = Calendar.getInstance(tz);
        calendar2.setLenient(false);
        calendar2.setTime(inDateB);
        calendar2.set(Calendar.HOUR_OF_DAY, 23);
        calendar2.set(Calendar.MINUTE, 59);
        calendar2.set(Calendar.SECOND, 59);
        calendar2.set(Calendar.MILLISECOND, 999);
        return Math.ceil((double) (calendar2.getTimeInMillis() - calendar.getTimeInMillis()) / MILLIS_PER_DAY);
    }
    private static Logger LOG = Logger.getLogger(ITSExtendedDwellCalculator.class)
    private static final String dwell_event = "UNIT_EXTENDED_DWELL"
    public static final String scopeCoOrdinatesString = 'ITS/USLGB/PIERG/PIERG'
    private ServiceLocator _externalServiceLocator = new ServiceLocator();
    public static final String RESULT = "result"
    DateFormat XML_DATE_TIME_ZONE_FORMAT = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss Z");
    DateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy")
}

