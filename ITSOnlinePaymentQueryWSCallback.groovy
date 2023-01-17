package ITS

import com.navis.argo.ArgoExtractEntity
import com.navis.argo.ArgoExtractField
import com.navis.argo.EdiInvoice
import com.navis.argo.InvoiceCharge
import com.navis.argo.business.api.IImpediment
import com.navis.argo.business.api.LogicalEntity
import com.navis.argo.business.api.Serviceable
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.ChargeableUnitEventTypeEnum
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.FlagStatusEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.cargo.business.model.BillOfLading
import com.navis.cargo.business.model.GoodsBl
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.framework.zk.util.JSONBuilder
import com.navis.inventory.InvEntity
import com.navis.inventory.InvField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.api.UnitStorageManager
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.business.units.UnitStorageManagerPea
import com.navis.services.business.api.EventManager
import com.navis.services.business.event.Event
import com.navis.services.business.rules.EventType
import com.navis.services.business.rules.Flag
import com.navis.services.business.rules.FlagType
import com.navis.services.business.rules.Veto
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat

/*
 * @Author <a href="mailto:annalakshmig@weservetech.com">ANNALAKSHMI G</a>
 * Date: 10-OCT-2022
 * Requirements:- Receives one or more Container Number(s) or B/L Number(s) and Returns a list of Container Availability details in JSON format
 *  @Inclusion Location	: Incorporated as a code extension of the type TRANSACTED_BUSINESS_FUNCTION --> Paste this code (ITSGetCtrAvailabilityWSCallback.groovy)

 * *
 */

class ITSOnlinePaymentQueryWSCallback extends AbstractExtensionPersistenceCallback {

    @Override
    void execute(@Nullable Map inMap, @Nullable Map outMap) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSOnlinePaymentQueryWSCallback :: start")

        String unitNbrs = inMap.containsKey(UNIT_IDS) ? inMap.get(UNIT_IDS) : null
        String pickUpDate = inMap.containsKey(PICKUP_DATE) ? inMap.get(PICKUP_DATE) : null

        Date pickDate = !StringUtils.isEmpty(pickUpDate) ? inputDateFormat.parse(pickUpDate) : null
        LOGGER.debug("pickDate:::::::::" + pickDate)
        outMap.put("RESPONSE", prepareOnlinePaymentMsgToITS(unitNbrs, pickDate))
    }


    String prepareOnlinePaymentMsgToITS(String unitNbrs, Date pickDate) {
        String errorMessage = validateMandatoryFields(unitNbrs, pickDate)
        JSONBuilder mainObj = JSONBuilder.createObject();

        if (errorMessage.length() > 0) {
            mainObj.put(ERROR_MESSAGE, errorMessage)
        } else {
            JSONBuilder jsonArray = JSONBuilder.createArray()
            String[] unitNumbers = unitNbrs?.split(",")*.trim()
            if (unitNumbers != null && unitNumbers.size() > 0) {
                List<Long> unitGkeyList = new ArrayList<>()
                // int i = 0;
                for (String str : unitNumbers) {
                    if (!StringUtils.isEmpty(str) && str.matches("[0-9]+")) {
                        unitGkeyList.add(Long.parseLong(str));
                    }
                }
                Serializable[] gkeyList
                if (!CollectionUtils.isEmpty(unitGkeyList)) {
                    DomainQuery dq = QueryUtils.createDomainQuery(InvEntity.UNIT_FACILITY_VISIT)
                            .addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_CATEGORY, UnitCategoryEnum.IMPORT))
                            .addDqPredicate(PredicateFactory.eq(InvField.UFV_VISIT_STATE, UnitVisitStateEnum.ACTIVE))
                            .addDqPredicate(PredicateFactory.in(UnitField.UFV_UNIT_GKEY, unitGkeyList))
                    //  .addDqOrdering(Ordering.desc(InvField.UFV_TIME_OF_LAST_MOVE))


                    gkeyList = HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq)
                    if (gkeyList != null && gkeyList.size() > 0) {
                        for (Serializable gkey : gkeyList) {
                            UnitFacilityVisit unitFacilityVisit = UnitFacilityVisit.hydrate(gkey)
                            if (unitFacilityVisit != null) {
                                Unit unit = unitFacilityVisit.getUfvUnit()
                                LOGGER.debug("unit ID " + unit.getUnitId())
                                if (unit != null) {
                                    GoodsBase goodsBase = unitFacilityVisit.getUfvUnit()?.getUnitGoods()
                                    boolean isHoldReleased = true
                                    if (goodsBase) isHoldReleased = isDeliverableHoldsReleased(goodsBase)
                                    Date dwellLastPtd
                                    DomainQuery cueDQ = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT)
                                            .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_UNIT_GKEY, unit.getUnitGkey()))
                                            .addDqPredicate(PredicateFactory.isNotNull(ArgoExtractField.BEXU_PAID_THRU_DAY))
                                            .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_EVENT_TYPE, ["UNIT_EXTENDED_DWELL"]))
                                            .addDqOrdering(Ordering.desc(ArgoExtractField.BEXU_PAID_THRU_DAY))
                                    List<ChargeableUnitEvent> cueList = (List<ChargeableUnitEvent>) HibernateApi.getInstance().findEntitiesByDomainQuery(cueDQ)
                                    if (!CollectionUtils.isEmpty(cueList)) {
                                        ChargeableUnitEvent cue = cueList.get(0)
                                        dwellLastPtd = cue.getBexuPaidThruDay()

                                    }
                                    //VesselVisitDetails vesselVisitDetails = unitFacilityVisit.getUfvActualIbCv() != null ? VesselVisitDetails.resolveVvdFromCv(unitFacilityVisit.getUfvActualIbCv()) : null
                                    JSONBuilder jsonUnitObject = JSONBuilder.createObject();
                                    jsonUnitObject.put(UNIT_ID, unit.getUnitGkey())
                                    jsonUnitObject.put(CONTAINER_NUMBER, unit.getUnitId())
                                    jsonUnitObject.put(SHIPPING_LINE_CD, unit.getUnitLineOperator()?.getBzuId() != null ? unit.getUnitLineOperator().getBzuId() : "")
                                    jsonUnitObject.put(B_LNUM, unit.getUnitGoods()?.getGdsBlNbr() != null ? unit.getUnitGoods()?.getGdsBlNbr() : "")
                                    UnitStorageManagerPea storageManager = (UnitStorageManagerPea) Roastery.getBean(UnitStorageManager.BEAN_ID);

                                    EdiInvoice ediInvoice

                                    try {
                                        ediInvoice = storageManager.getInvoiceForUnit(unitFacilityVisit, pickDate, IMPORT_PRE_PAY, (String) null, unitFacilityVisit.getUfvUnit().getUnitLineOperator(), (ScopedBizUnit) null, (String) null, pickDate, "INQUIRE");
                                    } catch (BizViolation | BizFailure bv) {
                                        LOGGER.debug("BizViolation" + bv)
                                    }

                                    // LOGGER.debug("ediInvoice fro after getInvoice" + ediInvoice)
                                    Double demmurrageCharge = 0.0
                                    Double examAmount = 0.0
                                    Double qtyBilled = 0.0
                                    Double dwellAmount = 0.0
                                    Double dwellQtyBilled = 0.0
                                    if (ediInvoice != null) {
                                        List<InvoiceCharge> chargeList = ediInvoice.getInvoiceChargeList();
                                        chargeList.each {
                                            charge ->
                                                if (ChargeableUnitEventTypeEnum.LINE_STORAGE.getKey().equals(charge.getChargeEventTypeId())) {
                                                    demmurrageCharge = demmurrageCharge + charge.getTotalCharged()
                                                    qtyBilled = qtyBilled + charge.getQuantityBilled()

                                                } else {
                                                    if ("TAILGATE_EXAM_REQUIRED".equals(charge.getChargeEventTypeId())) {
                                                        examAmount = examAmount + charge.getTotalCharged()
                                                    } else if ("VACIS_INSPECTION_REQUIRED".equals(charge.getChargeEventTypeId())) {
                                                        examAmount = examAmount + charge.getTotalCharged()
                                                    } else if ("UNIT_EXTENDED_DWELL".equals(charge.getChargeEventTypeId())) {
                                                        dwellAmount = dwellAmount + charge.getTotalCharged()
                                                        dwellQtyBilled = dwellQtyBilled + charge.getQuantityBilled()
                                                    }
                                                }
                                        }

                                        if (demmurrageCharge > 0) {
                                            jsonUnitObject.put(DEMURRAGE_AMT, demmurrageCharge)
                                        }
                                        if (examAmount > 0) {
                                            jsonUnitObject.put(EXAM_FEE_AMT, examAmount)
                                        }
                                        if (dwellAmount > 0) {
                                            jsonUnitObject.put(DWELL_FEE_AMT, dwellAmount)
                                        }
                                    }
                                    if (unitFacilityVisit.isTransitStateBeyond(UfvTransitStateEnum.S30_ECIN)) {
                                        EventManager eventManager = (EventManager) Roastery.getBean(EventManager.BEAN_ID)
                                        EventType unitDischEvnt = EventType.findEventType(EventEnum.UNIT_DISCH.getKey());
                                        if (unitDischEvnt != null) {
                                            Event event = eventManager.getMostRecentEventByType(unitDischEvnt, unit);
                                            if (event != null) {
                                                jsonUnitObject.put(DISCHARGED_DT_TM, event.getEvntAppliedDate() != null ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : "")
                                            }
                                        }
                                    }

                                    ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
                                    Collection<IImpediment> impedimentsCollection = (Collection<IImpediment>) servicesManager.getImpedimentsForEntity(unit)

                                    Flag flag = null
                                    Date custHoldReleaseDateFinal = null
                                    List<Date> dateList = new ArrayList<>()
                                    for (IImpediment impediment : impedimentsCollection) {
                                        if (impediment != null && FlagStatusEnum.RELEASED.equals(impediment.getStatus()) && HOLD_LIST.contains(impediment.getFlagType()?.getId())) {
                                            flag = Flag.hydrate(impediment.getFlagGkey())
                                            Collection vetoCollection = flag.getVetoesForEntity(unit)
                                            if (vetoCollection != null) {
                                                for (Veto veto : (vetoCollection as List<Veto>)) {
                                                    dateList.add(veto.getVetoAppliedDate())
                                                }
                                            }

                                        }
                                    }
                                    if (!CollectionUtils.isEmpty(dateList)) {
                                        custHoldReleaseDateFinal = getGreatestOfDates(dateList)
                                    }

                                    if (isHoldReleased && custHoldReleaseDateFinal) {
                                        jsonUnitObject.put(CUSTOMS_HOLD_REL_DT_TM, ISO_DATE_FORMAT.format(custHoldReleaseDateFinal))
                                    }
                                    if (unitFacilityVisit.getUfvFlexDate03() != null) {
                                        jsonUnitObject.put(FIRST_DELIVERABLE_DT_TM, ISO_DATE_FORMAT.format(unitFacilityVisit.getUfvFlexDate03()))
                                        if (isHoldReleased && custHoldReleaseDateFinal && custHoldReleaseDateFinal.after(unitFacilityVisit.getUfvFlexDate03())) {
                                            jsonUnitObject.put(AVAILABLE_DT_TM, ISO_DATE_FORMAT.format(custHoldReleaseDateFinal))
                                        } else {
                                            jsonUnitObject.put(AVAILABLE_DT_TM, ISO_DATE_FORMAT.format(unitFacilityVisit.getUfvFlexDate03()))
                                        }
                                    }

                                    String calculatedLineLFD = unitFacilityVisit.getUfvCalculatedLineStorageLastFreeDay()
                                    Date llfd = null
                                    if (calculatedLineLFD) {
                                        llfd = getLfdDate(calculatedLineLFD)
                                    }

                                    if (unitFacilityVisit.getUfvLineLastFreeDay() != null || llfd != null) {
                                        jsonUnitObject.put(LAST_FREE_DT_TM, unitFacilityVisit.getUfvLineLastFreeDay() != null ? ISO_DATE_FORMAT.format(unitFacilityVisit.getUfvLineLastFreeDay()) : ISO_DATE_FORMAT.format(llfd))
                                        jsonUnitObject.put(DWELL_LAST_FREE_DT_TM, unitFacilityVisit.getUfvLineLastFreeDay() != null ? ISO_DATE_FORMAT.format(unitFacilityVisit.getUfvLineLastFreeDay()) : ISO_DATE_FORMAT.format(llfd))
                                    }

                                    if (unitFacilityVisit.getUfvLinePaidThruDay() != null) {
                                        jsonUnitObject.put(LAST_PAID_THRU_DT_TM, ISO_DATE_FORMAT.format(unitFacilityVisit.getUfvLinePaidThruDay()))
                                    }
                                    if (qtyBilled > 0) {
                                        jsonUnitObject.put(DAYS_ON_CHARGE, (int) qtyBilled)
                                    }
                                    if (storageManager.getFirstFreeDay(unitFacilityVisit, "LINE_STORAGE") != null) {
                                        jsonUnitObject.put(FIRST_FREE_DT_TM, ISO_DATE_FORMAT.format(storageManager.getFirstFreeDay(unitFacilityVisit, "LINE_STORAGE")))
                                    }

                                    int freeDays = storageManager.getFreeDays(unitFacilityVisit, "LINE_STORAGE")
                                    if (freeDays > 0) {
                                        jsonUnitObject.put(FREE_DAYS, freeDays)
                                        jsonUnitObject.put(DWELL_FREE_DAYS, freeDays)
                                    }
                                    if (dwellQtyBilled > 0) {
                                        jsonUnitObject.put(DWELL_DAYS_ON_CHARGE, (int) dwellQtyBilled)
                                    }
                                    if (dwellLastPtd) {
                                        jsonUnitObject.put(DWELL_LAST_PAID_THRU_DT_TM, ISO_DATE_FORMAT.format(dwellLastPtd))
                                    }
                                    jsonArray.add(jsonUnitObject)
                                }

                            }
                        }
                    }
                }
                mainObj.put(PAY_ONLINE_HEADERS, jsonArray)


            }
        }
        return mainObj.toJSONString()
    }

    boolean isDeliverableHoldsReleased(goodsBase) {
        List<String> holdMap = ['1H', '7H', '2H', '71', '72', '73']
        GoodsBl goodsBl = GoodsBl.resolveGoodsBlFromGoodsBase(goodsBase)
        Set<BillOfLading> blSet = goodsBl?.getGdsblBillsOfLading()

        boolean flagReleased = Boolean.TRUE
        blSet.each {
            bl ->
                holdMap.each {
                    if (isFlagActive(bl, it)) {
                        flagReleased = Boolean.FALSE
                    }
                }
        }
        return flagReleased
    }

    private boolean isFlagActive(LogicalEntity logicalEntity, String holdId) {
        FlagType type = FlagType.findFlagType(holdId)
        if (type != null) {
            return type.isActiveFlagPresent(logicalEntity, null, (Serviceable) logicalEntity)
        }
        return false
    }

    private static Date getGreatestOfDates(@NotNull List<Date> dateList) {

        if (dateList.size() == 0) {
            return null
        } else if (dateList.size() == 1) {
            return dateList.get(0)
        }

        Date greatest = dateList.get(0)
        for (Date date : dateList) {
            greatest = getMaxOf(date, greatest)

        }
        return greatest
    }

    private static Date getMaxOf(@NotNull Date date1, @NotNull Date date2) {

        if (date1.compareTo(date2) > 0) {
            return date1
        }
        return date2
    }

    private static Date getLfdDate(String dt) throws ParseException {
        Calendar cal = Calendar.getInstance()
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MMM-dd")
        if (dt.endsWith("!")) {
            dt = dt.replaceAll("!", "")
        }
        if (dt.contains("no")) {
            return null
        }
        cal.setTime(dateFormat.parse(dt))
        return cal.getTime()
    }

    private String validateMandatoryFields(String unitNbrs, Date pickUpDate) {
        StringBuilder stringBuilder = new StringBuilder()
        if (StringUtils.isEmpty(unitNbrs)) {
            stringBuilder.append("Missing required parameter : unitNbrs.").append("::")
        }
        if (pickUpDate == null) {
            stringBuilder.append("Missing required parameter : pickupDate.").append("::")
        }
        return stringBuilder.toString()
    }


    private static final String UNIT_IDS = "unitIds"
    private static final String PICKUP_DATE = "pickupDate"
    private static final String PAY_ONLINE_HEADERS = "payOnlineHeaders"
    private static final String UNIT_ID = "unitId"
    private static final String CONTAINER_NUMBER = "containerNumber"
    private static final String SHIPPING_LINE_CD = "shippingLineCd"
    private static final String B_LNUM = "bLNum"
    private static final String DEMURRAGE_AMT = "demurrageAmt"
    private static final String EXAM_FEE_AMT = "examFeeAmt"
    private static final String DWELL_FEE_AMT = "dwellFeeAmt"
    private static final String DISCHARGED_DT_TM = "dischargedDtTm"
    private static final String FIRST_DELIVERABLE_DT_TM = "firstDeliverableDtTm"
    private static final String CUSTOMS_HOLD_REL_DT_TM = "customsHoldRelDtTm"

    private static final String AVAILABLE_DT_TM = "availableDtTm"
    private static final String FIRST_FREE_DT_TM = "firstFreeDtTm"
    private static final String FREE_DAYS = "freeDays"
    private static final String LAST_FREE_DT_TM = "lastFreeDtTm"
    private static final String LAST_PAID_THRU_DT_TM = "lastPaidThruDtTm"
    private static final String DAYS_ON_CHARGE = "daysOnCharge"
    private static final String ERROR_MESSAGE = "errorMessage"
    private String IMPORT_PRE_PAY = "IMPORT_PRE_PAY";

    private static final String DWELL_FREE_DAYS = "dwellFreeDays"
    private static final String DWELL_LAST_FREE_DT_TM = "dwellLastFreeDtTm"
    private static final String DWELL_LAST_PAID_THRU_DT_TM = "dwellLastPaidThruDtTm"
    private static final String DWELL_DAYS_ON_CHARGE = "dwellDaysOnCharge"

    private static ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
    private static DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static DateFormat DISCHARGE_DATE_FORMAT = new SimpleDateFormat("MM/dd");
    private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    private static DateFormat calculatedLineLfdDateFormat = new SimpleDateFormat("yyyy-MMM-dd")
    private static DateFormat inputDateFormat = new SimpleDateFormat("MM/dd/yyyy")
    private static final List<String> HOLD_LIST = ["1H", "2H", "7H", "71", "72", "73"]

    private static Logger LOGGER = Logger.getLogger(this.class);

}
