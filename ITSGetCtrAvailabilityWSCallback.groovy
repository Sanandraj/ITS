package ITS

import com.navis.argo.*
import com.navis.argo.business.api.*
import com.navis.argo.business.atoms.*
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.extract.Guarantee
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.business.services.IServiceExtract
import com.navis.argo.business.xps.model.StackStatus
import com.navis.argo.business.xps.util.StackStatusUtils
import com.navis.cargo.business.model.BillOfLading
import com.navis.cargo.business.model.GoodsBl
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.portal.query.QueryFactory
import com.navis.framework.query.common.api.QueryResult
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.framework.zk.util.JSONBuilder
import com.navis.inventory.InvEntity
import com.navis.inventory.InvField
import com.navis.inventory.MovesField
import com.navis.inventory.business.api.MovesCompoundField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.api.UnitStorageManager
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.moves.WorkInstruction
import com.navis.inventory.business.units.*
import com.navis.services.business.api.EventManager
import com.navis.services.business.event.Event
import com.navis.services.business.rules.EventType
import com.navis.services.business.rules.FlagType
import com.navis.spatial.business.model.AbstractBin
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId

/*
 * @Author <a href="mailto:annalakshmig@weservetech.com">ANNALAKSHMI G</a>
 * Date: 07/02/2022
 * Requirements:- Receives one or more Container Number(s) or B/L Number(s) and Returns a list of Container Availability details in JSON format
 *  @Inclusion Location	: Incorporated as a code extension of the type TRANSACTED_BUSINESS_FUNCTION --> Paste this code (ITSGetCtrAvailabilityWSCallback.groovy)
 * First shift - 8:00 to 17:59
 * Second shift -18:00 to 02:59
 * Third Shift - 03:00 to 07:59
 * ufvFlexDate01 ----> First Available Date
 * ufvFlexDate03-----> First Deliverable Date
 * *
 */

class ITSGetCtrAvailabilityWSCallback extends AbstractExtensionPersistenceCallback {

    @Override
    void execute(@Nullable Map inMap, @Nullable Map outMap) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSGetCtrAvailabilityWSCallback :: start")
        String refType = inMap.containsKey(REF_TYPE) ? inMap.get(REF_TYPE) : null
        String refNums = inMap.containsKey(REF_NUMS) ? inMap.get(REF_NUMS) : null
        String pickupDate = inMap.containsKey(PICKUP_DATE) ? inMap.get(PICKUP_DATE) : null
        Date pickDate = !StringUtils.isEmpty(pickupDate) ? inputDateFormat.parse(pickupDate) : inputDateFormat.parse(currentDate)
        LOGGER.debug("pickDate:::::::::" + pickDate)
        outMap.put("RESPONSE", prepareCtrAvailabilityMsgToITS(refType, refNums, pickDate))
    }


    String prepareCtrAvailabilityMsgToITS(String refType, String refNums, Date pickDate) {
        String errorMessage = validateMandatoryFields(refType, refNums)
        JSONBuilder mainObj = JSONBuilder.createObject();

        if (errorMessage.length() > 0) {
            mainObj.put(ERROR_MESSAGE, errorMessage)
        } else {
            String[] refNumbers = refNums?.toUpperCase()?.split(",")*.trim()

            DomainQuery dq = QueryUtils.createDomainQuery(InvEntity.UNIT_FACILITY_VISIT)
                    .addDqField(UnitField.UFV_UNIT_ID)
                    .addDqField(InvField.UFV_GKEY)
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S10_ADVISED))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_CATEGORY, UnitCategoryEnum.IMPORT))
                    .addDqOrdering(Ordering.desc(InvField.UFV_TIME_OF_LAST_MOVE))
            if (CTR_TYPE.equalsIgnoreCase(refType)) {
                dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_UNIT_ID, refNumbers))
            } else {
                dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_GDS_BL_NBR, refNumbers))
            }
            QueryResult rs = HibernateApi.getInstance().findValuesByDomainQuery(dq)
            Map<Serializable, String> map = new HashMap<>()
            if (rs.getTotalResultCount() > 0) {
                for (int i = 0; i < rs.getTotalResultCount(); i++) {
                    if (!map.containsValue(rs.getValue(i, UnitField.UFV_UNIT_ID))) { //to filter latest unit alone
                        map.put(rs.getValue(i, InvField.UFV_GKEY) as Serializable, rs.getValue(i, UnitField.UFV_UNIT_ID).toString())
                    }
                }
            }
            JSONBuilder jsonArray = JSONBuilder.createArray()
            for (Map.Entry<Serializable, String> entry : map.entrySet()) {
                UnitFacilityVisit unitFacilityVisit = UnitFacilityVisit.hydrate(entry.getKey())
                if (unitFacilityVisit != null) {
                    Unit unit = unitFacilityVisit.getUfvUnit()
                    LOGGER.debug("unit ID " + unit.getUnitId())
                    if (unit != null) {
                        JSONBuilder jsonEventObject = JSONBuilder.createObject();
                        jsonEventObject.put(UNIT_ID, unit.getUnitGkey())
                        jsonEventObject.put(CONTAINER_NUMBER, unit.getUnitId())
                        jsonEventObject.put(SHIPPING_LINE_CD, unit.getUnitLineOperator()?.getBzuId() != null ? unit.getUnitLineOperator().getBzuId() : "")
                        jsonEventObject.put(SHIPPING_LINE_SCAC, unit.getUnitLineOperator()?.getBzuScac() != null ? unit.getUnitLineOperator().getBzuScac() : "")
                        jsonEventObject.put(CONTAINER_SZ_TP_HT, new StringBuilder().append(unit.getUnitEquipment()?.getEqEquipType()?.getEqtypNominalLength()?.getKey()?.substring(3, 5))
                                .append(unit.getUnitEquipment()?.getEqEquipType()?.getEqtypIsoGroup()?.getKey())
                                .append(unit.getUnitEquipment()?.getEqEquipType()?.getEqtypNominalHeight()?.getKey()?.substring(3, 5)).toString())

                        if (unit.getUnitCarriageUnit()?.getUnitId() != null) {
                            jsonEventObject.put(CHASSIS_NUMBER, unit.getUnitCarriageUnit().getUnitId())
                        }
                        Map<String, String> spotParms = new HashMap<>()
                        spotParms = deriveContainerSpot(unitFacilityVisit)
                        jsonEventObject.put(SPOT_NUM, spotParms.get("CTR_SPOT") != null ? spotParms.get("CTR_SPOT") : "")

                        Date finalEstMoveTime = null
                        DomainQuery wiDq = QueryUtils.createDomainQuery("WorkInstruction").addDqPredicate(PredicateFactory.eq(MovesCompoundField.WI_UYV_UFV, unitFacilityVisit.getPrimaryKey()))
                                .addDqPredicate(PredicateFactory.eq(MovesField.WI_MOVE_KIND, WiMoveKindEnum.VeslDisch))
                                .addDqPredicate(PredicateFactory.ne(MovesField.WI_MOVE_STAGE, WiMoveStageEnum.COMPLETE))
                                .addDqOrdering(Ordering.desc(MovesField.WI_TIME_CREATED)).setDqMaxResults(1)
                        WorkInstruction workInstruction = (WorkInstruction) HibernateApi.getInstance().getUniqueEntityByDomainQuery(wiDq)

                        if (workInstruction != null) {
                            finalEstMoveTime = workInstruction.getWiEstimatedMoveTime()
                            if (finalEstMoveTime != null) {
                                String shift = getShift(finalEstMoveTime.getHours())
                                jsonEventObject.put(EST_DISCHARGE_DATE_SHIFT, DISCHARGE_DATE_FORMAT.format(finalEstMoveTime) + " " + shift)
                            }
                        }

                        EventManager eventManager = (EventManager) Roastery.getBean(EventManager.BEAN_ID)
                        if (unitFacilityVisit.isTransitStateBeyond(UfvTransitStateEnum.S30_ECIN)) {
                            EventType unitDischEvnt = EventType.findEventType(EventEnum.UNIT_DISCH.getKey());
                            if (unitDischEvnt != null) {
                                Event event = eventManager.getMostRecentEventByType(unitDischEvnt, unit);
                                if (event != null) {
                                    jsonEventObject.put(DISCHARGED_DT_TM, event.getEvntAppliedDate() != null ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : "")
                                }
                            }
                        }
                        if (unitFacilityVisit.isTransitStateBeyond(UfvTransitStateEnum.S50_ECOUT)) {
                            EventType unitDeliveredEvnt = EventType.findEventType(EventEnum.UNIT_DELIVER.getKey());
                            if (unitDeliveredEvnt != null) {
                                Event event = eventManager.getMostRecentEventByType(unitDeliveredEvnt, unit);
                                if (event != null) {
                                    jsonEventObject.put(DELIVERED_DT_TM, event.getEvntAppliedDate() != null ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : "")
                                }
                            }
                        }


                        jsonEventObject.put(BL_NUM, unit.getUnitGoods()?.getGdsBlNbr() != null ? unit.getUnitGoods().getGdsBlNbr() : "")
                        jsonEventObject.put(VESSEL_NAME, unitFacilityVisit.getUfvActualIbCv()?.getCarrierVehicleName() != null ? unitFacilityVisit.getUfvActualIbCv().getCarrierVehicleName() : "")
                        jsonEventObject.put(VOYAGE_NUM, unitFacilityVisit.getUfvActualIbCv()?.getCarrierIbVoyNbrOrTrainId() != null ? unitFacilityVisit.getUfvActualIbCv().getCarrierIbVoyNbrOrTrainId() : "")
                        // optional
                        if (unit.getUnitRouting()?.getRtgTruckingCompany()?.getBzuName() != null) {
                            jsonEventObject.put(TRUCKING_CO_NAME, unit.getUnitRouting().getRtgTruckingCompany().getBzuName())
                        }
                        boolean isContainerAvailable = true
                        Double examAmount = 0.0
                        GoodsBase goodsBase = unitFacilityVisit.getUfvUnit()?.getUnitGoods()
                        boolean isHoldReleased = true
                        if (goodsBase) isHoldReleased = isDeliverableHoldsReleased(goodsBase)

                        if ((unitFacilityVisit.getUfvFlexDate03() != null && unitFacilityVisit.getUfvFlexDate01() != null) || (null == spotParms.get("SPOT_STATUS_NOTE") && isHoldReleased)) { // send amount details only if fad != null
                            if (unitFacilityVisit.getUfvFlexDate03() == null) {
                                unitFacilityVisit.setUfvFlexDate03(ArgoUtils.timeNow())
                            }
                            if (unitFacilityVisit.getUfvFlexDate01() == null) {
                                unitFacilityVisit.setUfvFlexDate01(ArgoUtils.timeNow())
                                unit.setUnitFlexString03("Y")
                                unit.setUnitFlexString08("Y")
                            }

                            DomainQuery domainQuery = QueryFactory.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT)
                                    .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_EVENT_TYPE, ["LINE_STORAGE", "UNIT_EXTENDED_DWELL"]))
                                    .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_UFV_GKEY, unitFacilityVisit.getPrimaryKey()))
                                    .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_STATUS, ["QUEUED", "PARTIAL"]))
                            Serializable[] extractGkeys = HibernateApi.getInstance().findPrimaryKeysByDomainQuery(domainQuery)
                            boolean isWaiverApplied = false
                            ChargeableUnitEvent cue
                            Date today = ArgoUtils.timeNow()
                            LocalDate lcToday = getLocalDate(today)
                            LocalDate lcPreviousDay = lcToday.minusDays(1l)
                            if (extractGkeys != null && extractGkeys.size() > 0) {
                                LOGGER.debug("ITSRecordWaiverForUnit extractGkeys" + extractGkeys.size())
                                for (Serializable extractGkey : extractGkeys) {
                                    List<Guarantee> guaranteeList = (List<Guarantee>) Guarantee.getListOfGuarantees(BillingExtractEntityEnum.INV, extractGkey)
                                    isWaiverApplied = false
                                    cue = ChargeableUnitEvent.hydrate(extractGkey)
                                    if (NO.equalsIgnoreCase(unit.getUnitFlexString03())) {
                                        if (!CollectionUtils.isEmpty(guaranteeList)) {
                                            for (Guarantee guarantee : guaranteeList) {
                                                isWaiverApplied = false
                                                //updating a waiver record is possible only if the CUE status is "CANCELLED"
                                                if (dwellEvent.equals(cue.getEventType()) && !IServiceExtract.CANCELLED.equals(cue.getBexuStatus())) {
                                                    cue.setBexuStatus(IServiceExtract.CANCELLED)
                                                }
                                                if (guarantee.isWavier() /*&& NDB_WAIVER.equals(guarantee.getGnteNotes())*/) {
                                                    if (guarantee.getGnteGuaranteeEndDay() != null && lcPreviousDay.equals(getLocalDate(guarantee.getGnteGuaranteeEndDay()))) {
                                                        guarantee.setGnteGuaranteeEndDay(today)
                                                        isWaiverApplied = true
                                                    } else if (guarantee.getGnteGuaranteeEndDay() != null && lcToday.equals(getLocalDate(guarantee.getGnteGuaranteeEndDay()))) {
                                                        isWaiverApplied = true
                                                    }
                                                }
                                            }
                                        }
                                        if (!isWaiverApplied && cue != null) {

                                            Guarantee gtr = new Guarantee();
                                            String gtId = gtr.getGuaranteeIdFromSequenceProvide();
                                            gtr.setFieldValue(ArgoExtractField.GNTE_GUARANTEE_ID, gtId);
                                            gtr.setGnteAppliedToClass(BillingExtractEntityEnum.INV);
                                            gtr.setGnteAppliedToPrimaryKey((Long) extractGkey);
                                            gtr.setGnteAppliedToNaturalKey(cue.getBexuEqId())
                                            gtr.setGnteExternalUserId(ContextHelper.getThreadUserId());
                                            gtr.setGnteGuaranteeType(GuaranteeTypeEnum.WAIVER)
                                            gtr.setGnteOverrideValueType(GuaranteeOverrideTypeEnum.FREE_NOCHARGE)
                                            gtr.setGnteQuantity(1)
                                            gtr.setGnteGuaranteeStartDay(today)
                                            gtr.setGnteGuaranteeEndDay(today)
                                            gtr.setGnteNotes("Waived for NDB")
                                            gtr.setGnteGuaranteeCustomer(deriveScopedBizUnit(cue.getBexuLineOperatorId()))
                                            try {
                                                GuaranteeManager.recordGuarantee(gtr);
                                                // guaranteeList.add(gtr)

                                            } catch (Exception e) {
                                                LOGGER.debug("ITSRecordWaiverForNonDeliverableUnits from Ctr Availability exception catch" + e)
                                            }
                                        }
                                    } else if (YES.equalsIgnoreCase(unit.getUnitFlexString03()) && YES.equalsIgnoreCase(unit.getUnitFlexString07())) {
                                        isWaiverApplied = false
                                        if (!CollectionUtils.isEmpty(guaranteeList)) {
                                            for (Guarantee guarantee : guaranteeList) {
                                                if (dwellEvent.equals(cue.getEventType()) && !IServiceExtract.CANCELLED.equals(cue.getBexuStatus())) {
                                                    cue.setBexuStatus(IServiceExtract.CANCELLED)
                                                }
                                                if (guarantee.isWavier() /*&& NDB_WAIVER.equals(guarantee.getGnteNotes())*/) {
                                                    if (guarantee.getGnteGuaranteeEndDay() != null && lcPreviousDay.equals(getLocalDate(guarantee.getGnteGuaranteeEndDay()))) {
                                                        guarantee.setGnteGuaranteeEndDay(today)
                                                        isWaiverApplied = true
                                                    }
                                                }
                                            }
                                        }
                                        if (isWaiverApplied) {
                                            unit.setUnitFlexString08(null)
                                        }
                                    }
                                }
                            }

                            jsonEventObject.put(FIRST_AVAILABLE_DT_TM, ISO_DATE_FORMAT.format(unitFacilityVisit.getUfvFlexDate01()))
                            jsonEventObject.put(FIRST_DELIVERABLE_DAY, ISO_DATE_FORMAT.format(unitFacilityVisit.getUfvFlexDate03()))
                            //}

                            String lineLFDStr = unitFacilityVisit.getUfvCalculatedLineStorageLastFreeDay()
                            Date llfd = null
                            if (lineLFDStr) {
                                llfd = getLfdDate(lineLFDStr)
                            }
                            // jsonEventObject.put(DELIVERY_ORDER_REMARK, "")
                            if (unitFacilityVisit.getUfvLineLastFreeDay() != null || llfd != null) {
                                jsonEventObject.put(LAST_FREE_DAY, unitFacilityVisit.getUfvLineLastFreeDay() != null ? ISO_DATE_FORMAT.format(unitFacilityVisit.getUfvLineLastFreeDay()) : ISO_DATE_FORMAT.format(llfd))
                            }
                            UnitStorageManagerPea storageManager = (UnitStorageManagerPea) Roastery.getBean(UnitStorageManager.BEAN_ID);

                            EdiInvoice ediInvoice
                            try {
                                ediInvoice = storageManager.getInvoiceForUnit(unitFacilityVisit, pickDate, IMPORT_PRE_PAY, (String) null, unitFacilityVisit.getUfvUnit().getUnitLineOperator(), (ScopedBizUnit) null, (String) null, pickDate, "INQUIRE");
                            } catch (BizViolation | BizFailure bv) {
                                LOGGER.debug("BizViolation" + bv)
                            }
                            Date dwellNoteDate = null
                            Date examNoteDate = null
                            DomainQuery cueDQ = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT)
                                    .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_UNIT_GKEY, unit.getUnitGkey()))
                                    .addDqPredicate(PredicateFactory.isNotNull(ArgoExtractField.BEXU_PAID_THRU_DAY))
                                    .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_EVENT_TYPE, ["UNIT_EXTENDED_DWELL", "TAILGATE_EXAM_REQUIRED", "VACIS_INSPECTION_REQUIRED"]))
                                    .addDqOrdering(Ordering.asc(ArgoExtractField.BEXU_PAID_THRU_DAY))
                            List<ChargeableUnitEvent> cueList = (List<ChargeableUnitEvent>) HibernateApi.getInstance().findEntitiesByDomainQuery(cueDQ)
                            if (!CollectionUtils.isEmpty(cueList)) {
                                for (ChargeableUnitEvent event : cueList) {
                                    if ("UNIT_EXTENDED_DWELL".equals(event.getBexuEventType())) {
                                        dwellNoteDate = event.getBexuPaidThruDay()
                                    } else if ("TAILGATE_EXAM_REQUIRED".equals(event.getBexuEventType())) {
                                        examNoteDate = event.getBexuPaidThruDay()
                                    } else if ("VACIS_INSPECTION_REQUIRED".equals(event.getBexuEventType())) {
                                        examNoteDate = event.getBexuPaidThruDay()
                                    }
                                }
                            }
                            // LOGGER.debug("ediInvoice fro after getInvoice" + ediInvoice)
                            Double demmurrageCharge = 0.0
                            // Double examAmount = 0.0
                            Double dwellAmount = 0.0
                            if (ediInvoice != null) {
                                List<InvoiceCharge> chargeList = ediInvoice.getInvoiceChargeList();
                                chargeList.each {
                                    charge ->
                                        if (ChargeableUnitEventTypeEnum.LINE_STORAGE.getKey().equals(charge.getChargeEventTypeId())) {
                                            demmurrageCharge = demmurrageCharge + charge.getTotalCharged()
                                        } else {
                                            if ("TAILGATE_EXAM_REQUIRED".equals(charge.getChargeEventTypeId())) {
                                                examAmount = examAmount + charge.getTotalCharged()
                                            } else if ("VACIS_INSPECTION_REQUIRED".equals(charge.getChargeEventTypeId())) {
                                                examAmount = examAmount + charge.getTotalCharged()
                                            } else if ("UNIT_EXTENDED_DWELL".equals(charge.getChargeEventTypeId())) {
                                                dwellAmount = dwellAmount + charge.getTotalCharged()
                                            }
                                        }
                                }

                                if (demmurrageCharge > 0) {
                                    jsonEventObject.put(DEMURRAGE_AMT, demmurrageCharge)
                                }
                                if (unitFacilityVisit.getUfvLinePaidThruDay() != null) {
                                    jsonEventObject.put(DEMURRAGE_NOTE, ISO_DATE_FORMAT.format(unitFacilityVisit.getUfvLinePaidThruDay()))
                                }
                                if (examAmount > 0) {
                                    jsonEventObject.put(EXAM_FEE_AMT, examAmount)
                                }
                                if (examNoteDate != null) {
                                    jsonEventObject.put(EXAM_FEE_NOTE, ISO_DATE_FORMAT.format(examNoteDate))
                                }
                            }

                            if (dwellAmount > 0) {
                                jsonEventObject.put(DWELL_FEE_AMT, dwellAmount)
                            }

                            if (dwellNoteDate != null) {
                                jsonEventObject.put(DWELL_FEE_NOTE, ISO_DATE_FORMAT.format(dwellNoteDate))
                            }
                        }
                        String flagType = null
                        boolean tmfHoldExist = false
                        boolean customsHoldExist = false
                        boolean freightHoldExist = false
                        boolean examHoldExist = false
                        boolean lineFeeHoldExist = false
                        boolean specialStatusHold = false

                        boolean portFeeHold = false
                        String specialStatusNote
                        String customStatusNote
                        String freightStatusNote
                        String portFeeNote
                        String pierPassStatusNote
                        String examStatusNote = ""
                        String lineFeeNote

                        ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
                        Collection<IImpediment> impedimentsCollection = (Collection<IImpediment>) servicesManager.getImpedimentsForEntity(unit)
                        LOGGER.debug("impedimentsCollection   " + impedimentsCollection.toString())
                        StringBuilder customsSB = new StringBuilder()
                        StringBuilder frieghtSB = new StringBuilder()
                        StringBuilder examSB = new StringBuilder()
                        StringBuilder specialStatusSB = new StringBuilder()
                        StringBuilder lineFeeSB = new StringBuilder()
                        StringBuilder tmfSB = new StringBuilder()
                        StringBuilder portFeeSB = new StringBuilder()


                        for (IImpediment impediment : impedimentsCollection) {
                            if (impediment != null && FlagStatusEnum.ACTIVE.equals(impediment.getStatus())) {
                                IFlagType iFlagType = impediment.getFlagType()
                                flagType = impediment.getFlagType()?.getId()
                                if (flagType != null) {
                                    if (iFlagType.getHpvId().equals("CUSTOMS")) {
                                        customsHoldExist = true
                                        customsSB = customsSB.append(flagType).append(",")
                                        isContainerAvailable = false
                                    }
                                    if (iFlagType.getHpvId().equals("LINE")) {
                                        freightHoldExist = true
                                        frieghtSB = frieghtSB.append(flagType).append(",")
                                        isContainerAvailable = false
                                    }
                                    if (iFlagType.getHpvId().equals("EXAM")) {
                                        examHoldExist = true
                                        examSB = examSB.append(flagType).append(",")
                                        if (examAmount > 0) {
                                            isContainerAvailable = false
                                        }
                                    }
                                    if (iFlagType.getHpvId().equals("SPECIAL")) {
                                        specialStatusHold = true
                                        specialStatusSB = specialStatusSB.append(flagType).append(",")
                                        isContainerAvailable = false

                                    }
                                    if (iFlagType.getHpvId().equals("LINEFEE")) {
                                        lineFeeHoldExist = true
                                        lineFeeSB = lineFeeSB.append(flagType).append(",")
                                        isContainerAvailable = false

                                    }
                                    if (iFlagType.getHpvId().equals("PIERPASS")) {
                                        tmfHoldExist = true
                                        tmfSB = tmfSB.append(flagType).append(",")
                                        isContainerAvailable = false

                                    }
                                    if (iFlagType.getHpvId().equals("PORTFEE")) {
                                        portFeeHold = true
                                        portFeeSB = portFeeSB.append(flagType).append(",")
                                    }
                                }

                            }
                        }
                        customStatusNote = StringUtils.chop(customsSB.toString())
                        freightStatusNote = StringUtils.chop(frieghtSB.toString())
                        examStatusNote = StringUtils.chop(examSB.toString())
                        specialStatusNote = StringUtils.chop(specialStatusSB.toString())
                        lineFeeNote = StringUtils.chop(lineFeeSB.toString())
                        portFeeNote = StringUtils.chop(portFeeSB.toString())
                        pierPassStatusNote = StringUtils.chop(tmfSB.toString())
                        // if (!specialStatusHold) {
                        if (LocTypeEnum.TRAIN.equals(unit.getUnitRouting()?.getRtgDeclaredCv()?.getCvCarrierMode())) {
                            specialStatusHold = true
                            isContainerAvailable = false
                            specialStatusNote = TRAIN_LOAD
                            // }
                        }


                        jsonEventObject.put(IS_CUSTOMS_STATUS_OK, !customsHoldExist)
                        String blNbr = unit.getUnitGoods()?.getGdsBlNbr()

                        if (!StringUtils.isEmpty(blNbr)) {
                            LOGGER.debug("ITSGetCtrAvailabilityWSCallback :: blNbr start")
                            BillOfLading billOfLading = blNbr.contains("+") ? BillOfLading.findBillOfLading(blNbr.replace("+", ""), unitFacilityVisit.getUfvActualIbCv()) : BillOfLading.findBillOfLading(blNbr, unitFacilityVisit.getUfvActualIbCv())
                            LOGGER.debug("ITSGetCtrAvailabilityWSCallback :: blNbr end")

                        }
                        jsonEventObject.put(CUSTOMS_STATUS_NOTE, !StringUtils.isEmpty(customStatusNote) ? customStatusNote : "")
                        jsonEventObject.put(IS_FREIGHT_STATUS_OK, !freightHoldExist)
                        jsonEventObject.put(FREIGHT_STATUS_NOTE, !StringUtils.isEmpty(freightStatusNote) ? freightStatusNote : "")
                        jsonEventObject.put(IS_EXAM_STATUS_OK, !examHoldExist)
                        jsonEventObject.put(EXAM_STATUS_NOTE, !StringUtils.isEmpty(examStatusNote) ? examStatusNote : "")
                        jsonEventObject.put(IS_LINE_FEE_STATUS_OK, !lineFeeHoldExist)
                        jsonEventObject.put(LINE_FEE_STATUS_NOTE, !StringUtils.isEmpty(lineFeeNote) ? lineFeeNote : "")
                        jsonEventObject.put(IS_PIER_PASS_STATUS_OK, !tmfHoldExist)
                        jsonEventObject.put(PIER_PASS_STATUS_NOTE, !StringUtils.isEmpty(pierPassStatusNote) ? pierPassStatusNote : "")
                        jsonEventObject.put(IS_SPOT_STATUS_OK, spotParms.get("SPOT_STATUS_NOTE") == null)
                        jsonEventObject.put(SPOT_STATUS_NOTE, spotParms.get("SPOT_STATUS_NOTE") != null ? spotParms.get("SPOT_STATUS_NOTE") : "")
                        jsonEventObject.put(IS_SPECIAL_STATUS_OK, !specialStatusHold)
                        jsonEventObject.put(SPECIAL_STATUS_NOTE, !StringUtils.isEmpty(specialStatusNote) ? specialStatusNote : "")
                        jsonEventObject.put(IS_PORT_FEE_STATUS_OK, !portFeeHold)
                        jsonEventObject.put(PORT_FEE_STATUS_NOTE, !StringUtils.isEmpty(portFeeNote) ? portFeeNote : "")
                        /*else {
                            isContainerAvailable = false
                        }*/
                        jsonEventObject.put(IS_CONTAINER_AVAILABLE, spotParms.get("SPOT_STATUS_NOTE") == null && isContainerAvailable)
                        jsonEventObject.put(IS_CONTAINER_ACCESSIBLE,spotParms.get("SPOT_STATUS_NOTE") == null)
                        jsonArray.add(jsonEventObject)
                    }
                }
            }
            mainObj.put(CONTAINER_AVAILABILITIES, jsonArray)

        }
        LOGGER.debug("ITSGetCtrAvailabilityWSCallback :: ends")
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

    private String validateMandatoryFields(String refType, String refNums) {
        StringBuilder stringBuilder = new StringBuilder()
        if (StringUtils.isEmpty(refType)) {
            stringBuilder.append("Missing required parameter : refType.").append("::")
        }
        if (StringUtils.isEmpty(refNums)) {
            stringBuilder.append("Missing required parameter : refNums.").append("::")
        }
        if (!StringUtils.isEmpty(refType) && !(CTR_TYPE.equalsIgnoreCase(refType) || BL_TYPE.equalsIgnoreCase(refType))) {
            stringBuilder.append("Invalid value for parameter : refType.").append("::")
        }
        return stringBuilder.toString()
    }

    private String getShift(int targetTime) {
        String shift = null
        if (targetTime >= 8 && targetTime < 18)
            shift = FIRST_SHIFT
        else if ((targetTime >= 18 && targetTime < 24) || (targetTime >= 0 && targetTime < 3))
            shift = SECOND_SHIFT
        else
            shift = THIRD_SHIFT
        return shift
    }

    private Map<String, String> deriveContainerSpot(UnitFacilityVisit ufv) {
        Map<String, String> spotParams = new HashMap<>()
        String containerSpot = null
        String spotStatusNote = null
        boolean isSpotStatusOk = false
        switch (ufv.getUfvTransitState().getKey()) {
            case UfvTransitStateEnum.S20_INBOUND.getKey():
                containerSpot = CTR_SPOT_ARRIVING
                spotStatusNote = CTR_SPOT_ARRIVING
                break;

            case UfvTransitStateEnum.S30_ECIN.getKey():
                containerSpot = CTR_SPOT_SPOTTING
                spotStatusNote = CTR_SPOT_SPOTTING
                break;
            case UfvTransitStateEnum.S40_YARD.getKey():
                containerSpot = StringUtils.substringAfter(ufv.getUfvLastKnownPosition()?.getPosName(), "Y-PIERG-")
                if ("Y-PIERG-UTL".equalsIgnoreCase(ufv.getUfvLastKnownPosition()?.getPosName())) {
                    spotStatusNote = UNABLE_TO_LOCATE
                }
                LocPosition locPosition = ufv.getUfvLastKnownPosition()
                if (locPosition != null && LocTypeEnum.YARD.equals(locPosition.getPosLocType()) && locPosition.getPosBin() != null) {
                    StackStatus stackStatus = StackStatus.findStackStatus(locPosition.getPosBin(), ContextHelper.getThreadYard())
                    if (stackStatus != null && stackStatus.getStackstatusStatusChars() != null && "C".equalsIgnoreCase(StackStatusUtils.getProtectedStatus(stackStatus.getStackstatusStatusChars()).toString())) {
                        spotStatusNote = AREA_CLOSED
                    }
                }
                String blockName = null
                LocPosition position = ufv.getUfvLastKnownPosition()
                String bayName = getBayNumber(position)
                if (position.isWheeled() || position.isWheeledHeap() || (ufv.getUfvActualObCv() != null
                        && LocTypeEnum.TRAIN == ufv.getUfvActualObCv().getCvCarrierMode()) || (ufv.getUfvUnit()?.getUnitRouting() != null && ufv.getUfvUnit().getUnitRouting().getRtgGroup() != null
                        && StringUtils.isNotEmpty(ufv.getUfvUnit().getUnitRouting().getRtgGroup().getGrpId()))) {
                    ufv.getUfvUnit().setUnitFlexString03("Y")
                    ufv.getUfvUnit().setUnitFlexString06("N")
                    if (ufv.getUfvFlexDate01() == null) { // DO not clear the FAD - [Container sorting Fee]
                        ufv.setUfvFlexDate01(ArgoUtils.timeNow())
                    }
                    if (ufv.getUfvFlexDate03() == null) {
                        ufv.setUfvFlexDate03(ArgoUtils.timeNow())
                    }

                } else {
                    String currPosition = position.getPosSlot()
                    if (currPosition != null && ufv.getUfvUnit()?.getUnitEquipment()?.getEqEquipType() != null) {
                        position = LocPosition.resolvePosition(ContextHelper.getThreadFacility(), LocTypeEnum.YARD, ContextHelper.getThreadYardId(), currPosition, null, ufv.getUfvUnit().getUnitEquipment().getEqEquipType().getEqtypBasicLength())
                    }

                    if (position != null && StringUtils.isNotEmpty(position.getPosSlot())) {
                        blockName = (position.getBlockName() != null) ? position.getBlockName() :
                                position.getPosSlot().indexOf('.') != -1 ? position.getPosSlot().split('\\.')[0] : null
                    }

                    if (!StringUtils.isEmpty(blockName) && !StringUtils.isEmpty(bayName)) {
                        if (!isBayDeliverable(blockName, bayName)) {
                            spotStatusNote = UNDELIVERABLE_SPOT
                        }
                    } else if (StringUtils.isEmpty(bayName)) {
                        if (!isBlockDeliverable(blockName)) {
                            spotStatusNote = UNDELIVERABLE_SPOT
                        }
                    }
                    if (null == spotStatusNote && !YES.equalsIgnoreCase(ufv.getUfvUnit()?.getUnitFlexString03())) { //if the unit is deliverable at the time of deriving ctrSpot, it is due to yard area open
                        ufv.getUfvUnit()?.setUnitFlexString03("Y")
                        ufv.getUfvUnit()?.setUnitFlexString08("Y")
                    }
                }
                break;
            case UfvTransitStateEnum.S50_ECOUT.getKey():
                containerSpot = StringUtils.substringAfter(ufv.getUfvLastKnownPosition()?.getPosName(), "Y-PIERG-")
                spotStatusNote = DEPARTING
                break;
            case UfvTransitStateEnum.S60_LOADED.getKey():
            case UfvTransitStateEnum.S70_DEPARTED.getKey():
                containerSpot = CTR_SPOT_DEPARTED
                spotStatusNote = CTR_SPOT_DEPARTED
                break;

        }


        spotParams.put("CTR_SPOT", containerSpot)
        spotParams.put("SPOT_STATUS_NOTE", spotStatusNote)
        spotParams.put("IS_SPOT_OK", isSpotStatusOk.toString())

        return spotParams;
    }

    private String getBayNumber(LocPosition position) {

        String bay = null
        String row = null;
        String slot = null
        AbstractBin stackBin = position.getPosBin();
        if (stackBin != null) {
            if ("ABM_STACK".equalsIgnoreCase(stackBin.getAbnBinType().getBtpId())) {
                String stackBinName = stackBin.getAbnName()
                AbstractBin sectionBin = stackBin.getAbnParentBin();
                if (sectionBin != null && "ABM_SECTION".equalsIgnoreCase(sectionBin.getAbnBinType().getBtpId())) {
                    String sectionBinName = sectionBin.getAbnName()
                    row = sectionBinName;
                    slot = stackBinName.substring(stackBinName.indexOf(sectionBinName) + sectionBinName.size())
                    AbstractBin blockBin = sectionBin.getAbnParentBin();
                    if (!position.isWheeled() && blockBin != null && "ABM_BLOCK".equalsIgnoreCase(blockBin.getAbnBinType().getBtpId())) {
                        String blockBinName = blockBin.getAbnName()
                        row = sectionBinName.substring(sectionBinName.indexOf(blockBinName) + blockBinName.size());
                    }
                }
                if (position.isWheeled()) {
                    bay = slot;
                } else {
                    bay = row;
                }

            }
        }
        return bay
    }

    private ScopedBizUnit deriveScopedBizUnit(String lineId) {
        ScopedBizUnit scopedBizUnit
        scopedBizUnit = ScopedBizUnit.findScopedBizUnit(lineId, BizRoleEnum.LINEOP)
        return scopedBizUnit;
    }

    boolean isBayDeliverable(String blkId, String bayId) {
        List<GeneralReference> genRefList = (List<GeneralReference>) GeneralReference.findAllEntriesById("ITS", "DELIVERABLE_BAY", blkId)
        if (!CollectionUtils.isEmpty(genRefList)) {
            List<String> deliverableBayList = new ArrayList<>()
            List<String> nonDeliverableBayList = new ArrayList<>()
            for (GeneralReference generalReference : genRefList) {
                if (generalReference.getRefId3() != null && isDateWithinRange(generalReference)) {
                    String[] bays = StringUtils.split(generalReference.getRefId3(), ",")
                    for (String bay : bays) {
                        if ("Y".equalsIgnoreCase(generalReference.getRefValue1())) {
                            deliverableBayList.add(generalReference.getRefId2().concat(":").concat(bay))
                        } else if ("N".equalsIgnoreCase(generalReference.getRefValue1())) {
                            nonDeliverableBayList.add(generalReference.getRefId2().concat(":").concat(bay))
                        }
                    }

                }
            }
            if (!CollectionUtils.isEmpty(deliverableBayList) || !CollectionUtils.isEmpty(nonDeliverableBayList)) {
                if (!CollectionUtils.isEmpty(deliverableBayList) && deliverableBayList.contains(blkId.concat(":").concat(bayId))) {
                    return true
                } else if (!CollectionUtils.isEmpty(nonDeliverableBayList) && nonDeliverableBayList.contains(blkId.concat(":").concat(bayId))) {
                    return false
                } else {
                    return isBlockDeliverable(blkId)
                }
            } else {
                return isBlockDeliverable(blkId)
            }

        } else {
            return isBlockDeliverable(blkId)
        }

        // return false
    }

    boolean isBlockDeliverable(String blkId) {
        GeneralReference genRef = GeneralReference.findUniqueEntryById("ITS", "DELIVERABLE_BLOCK", blkId)
        if (genRef != null && "Y".equalsIgnoreCase(genRef.getRefValue1()) && isDateWithinRange(genRef)) {
            return true
        }
        return false
    }


    private boolean isDateWithinRange(GeneralReference generalReference) {
        Date endDate = null
        Date startDate = null
        Date testDate = ArgoUtils.timeNow()
        startDate = getDate(generalReference.getRefValue2())
        endDate = getDate(generalReference.getRefValue3())
        if (null == startDate && null == endDate) {
            return true
        } else if (startDate != null && testDate.after(startDate) && null == endDate) {
            return true
        } else if (startDate != null && endDate != null && testDate.after(startDate) && testDate.before(endDate)) {
            return true
        } else {
            return false
        }

    }

    private getLocalDate(Date date) {
        if (date != null) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        }
        return null
    }

    private static Date getDate(String dt) throws ParseException {
        if (!StringUtils.isEmpty(dt)) {
            Calendar cal = Calendar.getInstance()
            cal.setTime(dateFormat.parse(dt))
            return cal.getTime()
        }
        return null
    }
    private static final String DEPARTING = "DEPARTING"
    private static final String AREA_CLOSED = "AREA CLOSED"
    private static final String REF_TYPE = "refType"
    private static final String REF_NUMS = "refNums"
    private static final String PICKUP_DATE = "pickupDate"
    private static final String CONTAINER_NUMBER = "containerNumber"
    private static final String SHIPPING_LINE_SCAC = "shippingLineScac"
    private static final String SHIPPING_LINE_CD = "shippingLineCd"
    private static final String CONTAINER_SZ_TP_HT = "containerSzTpHt"
    private static final String CHASSIS_NUMBER = "chassisNumber"
    private static final String SPOT_NUM = "spotNum"
    private static final String EST_DISCHARGE_DATE_SHIFT = "estDischargeDateShift"
    private static final String DISCHARGED_DT_TM = "dischargedDtTm"
    private static final String DELIVERED_DT_TM = "deliveredDtTm"
    private static final String BL_NUM = "blNum"
    private static final String UNIT_ID = "unitId"

    private static final String VESSEL_NAME = "vesselName"
    private static final String VOYAGE_NUM = "voyageNum"
    private static final String TRUCKING_CO_NAME = "truckingCoName"
    private static final String DELIVERY_ORDER_REMARK = "deliveryOrderRemark"
    private static final String LAST_FREE_DAY = "lastFreeDay"
    private static final String DEMURRAGE_AMT = "demurrageAmt"
    private static final String DEMURRAGE_NOTE = "demurrageLastPTD"
    private static final String EXAM_FEE_AMT = "examFeeAmt"
    private static final String EXAM_FEE_NOTE = "examFeeLastPTD"
    private static final String IS_CUSTOMS_STATUS_OK = "isCustomsStatusOK"
    private static final String CUSTOMS_STATUS_NOTE = "customsStatusNote"
    private static final String IS_FREIGHT_STATUS_OK = "isFreightStatusOK"
    private static final String FREIGHT_STATUS_NOTE = "freightStatusNote"
    private static final String IS_EXAM_STATUS_OK = "isExamStatusOK"
    private static final String EXAM_STATUS_NOTE = "examStatusNote"
    private static final String IS_SPOT_STATUS_OK = "isSpotStatusOK"
    private static final String SPOT_STATUS_NOTE = "spotStatusNote"
    private static final String UNDELIVERABLE_SPOT = "UNDELIVERABLE SPOT"

    private static final String IS_SPECIAL_STATUS_OK = "isSpecialStatusOK"
    private static final String SPECIAL_STATUS_NOTE = "specialStatusNote"
    private static final String IS_LINE_FEE_STATUS_OK = "isLineFeeStatusOK"
    private static final String LINE_FEE_STATUS_NOTE = "lineFeeStatusNote"
    private static final String IS_PIER_PASS_STATUS_OK = "isPierPassStatusOK"
    private static final String PIER_PASS_STATUS_NOTE = "pierPassStatusNote"
    private static final String IS_CONTAINER_AVAILABLE = "isContainerAvailable"
    private static final String CONTAINER_AVAILABILITIES = "containerAvailabilities"
    private static final String OK_STATUS = "OK"
    private static final String FIRST_AVAILABLE_DT_TM = "firstAvailableDtTm"

    private static final String NOT_RELEASED = "NOT RELEASED"
    private static final String TMF_HOLD = "TMF HOLD"
    private static final String ERROR_MESSAGE = "errorMessage"
    private static final String FIRST_SHIFT = "1st"

    private static final String SECOND_SHIFT = "2nd"
    private static final String THIRD_SHIFT = "3rd"
    private static final String CTR_TYPE = "CN"
    private static final String PAID = "PAID "
    private static final String CTR_SPOT_ARRIVING = "ARRIVING"
    private static final String CTR_SPOT_DEPARTED = "DELIVERED"
    private static final String CTR_SPOT_SPOTTING = "SPOTTING"
    private static final String UNABLE_TO_LOCATE = "UNABLE TO LOCATE"
    private static final String BL_TYPE = "BL"
    private static final String IS_CONTAINER_ACCESSIBLE = "isContainerAccessible"
    private static final String TMF_START = "TMF HOLD"
    private static final String CUSTOMS_START = "CUSTOMS"
    private static final String FREIGHT_START = "FREIGHT"
    private static final String SERVICE_START = "SERVICE"
    private static final String NO_GO_HOLD_START = "NO GO HOLD"
    private static final String LINE_FEE_HOLD_START = "LINE FEE HOLD"
    private static final String TRAIN_LOAD = "TRAIN LOAD"
    private static final String NO_GO = "NO-GO"
    private static final String DEM = "DEM"
    private static final String EXAM_HOLD_1A = "1A"
    private static final String EXAM_HOLD_1U = "1U"
    private static final String EXAM_HOLD_7H = "7H"
    private static final String EXAM_HOLD_NII = "NII"
    private static final String SERVICE_HOLD = "SERVICE HOLD"
    private static final String IS_PORT_FEE_STATUS_OK = "isPortFeeStatusOK"
    private static final String PORT_FEE_STATUS_NOTE = "portFeeStatusNote"
    private static final String CTF_HOLD = "CTF HOLD"
    private static final String CTF = "CTF"
    private static final String TAILGATE = "TAILGATE"


    private static final String FIRST_DELIVERABLE_DAY = "firstDeliverableDtTm"
    private static final String DWELL_FEE_AMT = "dwellFeeAmt"
    private static final String DWELL_FEE_NOTE = "dwellFeeLastPTD"
    private final static String NO = "N"
    private final static String YES = "Y"
    private final static String dwellEvent = "UNIT_EXTENDED_DWELL"


    private static ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
    private static DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static DateFormat DISCHARGE_DATE_FORMAT = new SimpleDateFormat("MM/dd");
    private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    private static DateFormat inputDateFormat = new SimpleDateFormat("MM/dd/yyyy")
    String currentDate = inputDateFormat.format(new Date())
    private String IMPORT_PRE_PAY = "IMPORT_PRE_PAY";


    private static Logger LOGGER = Logger.getLogger(this.class);

}
