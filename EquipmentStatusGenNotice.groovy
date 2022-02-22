import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.LogicalEntityEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.integration.IntegrationServiceMessage
import com.navis.argo.business.model.ArgoSequenceProvider
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.reference.Equipment
import com.navis.carina.integrationservice.business.IntegrationService
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.IntegrationServiceField
import com.navis.framework.business.Roastery
import com.navis.framework.business.atoms.IntegrationServiceDirectionEnum
import com.navis.framework.business.atoms.IntegrationServiceTypeEnum
import com.navis.framework.metafields.MetafieldIdList
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.portal.query.PredicateIntf
import com.navis.framework.presentation.internationalization.MessageTranslator
import com.navis.framework.util.internationalization.TranslationUtils
import com.navis.framework.util.scope.ScopeCoordinates
import com.navis.framework.zk.util.JSONBuilder
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.units.ReeferRecord
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.RoadEntity
import com.navis.road.RoadField
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.services.business.api.EventManager
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import com.navis.services.business.rules.Flag
import com.navis.services.business.rules.FlagType
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import java.text.DateFormat
import java.text.SimpleDateFormat
/*
 * @Author <a href="mailto:annalakshmig@weservetech.com">ANNALAKSHMI G</a>
 * Requirements:- Send the current status of the equipment whenever a new event occurs for a container
 */

class EquipmentStatusGenNotice extends AbstractGeneralNoticeCodeExtension {

    @Override
    void execute(GroovyEvent inEvent) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("Inside the EquipmentStatusGenNotice :: Start")
        if (inEvent == null) {
            return
        }

        Unit unit = (Unit) inEvent.getEntity()
        if (unit == null) {
            return
        }
        List<IntegrationService> integrationServiceList = getUnitDetailsSyncIntegrationServices(INT_SERV_NAME, false);
        Equipment equip = unit.getUnitEquipment()
        UnitCategoryEnum unitCategory = unit.getUnitCategory()
        JSONBuilder mainObj = JSONBuilder.createObject();
        JSONBuilder msgHeaderObj = JSONBuilder.createObject();
        JSONBuilder msgDataArray = JSONBuilder.createArray()
        mainObj.put(MSGHEADER, msgHeaderObj)
        mainObj.put(MSGDATA, msgDataArray)
        msgHeaderObj.put(SOURCE_CD, ContextHelper.getThreadOperator()?.getOprId() ?: OPERATOR_ID)
        msgHeaderObj.put(SENDER_CD, ContextHelper.getThreadOperator()?.getOprId() ?: OPERATOR_ID)
        msgHeaderObj.put(RECEIVER_CD, RECEIVER_CD_VAL)
        msgHeaderObj.put(TRANS_CNT, "1")
        /* msgHeaderObj.put("gsn_nbr", "")
        msgHeaderObj.put("control_nbr", "")*/ //optional gsn_nbr & control_nbr
        msgHeaderObj.put(INTERCHANGE_DTTM, ISO_DATE_FORMAT.format(now))
        //  msgHeaderObj.put("rcvdtrans_cnt", "") // N/A

        JSONBuilder transactionsObj = JSONBuilder.createObject();
        transactionsObj.put(TRANS_DTTM, ISO_DATE_FORMAT.format(now))
        msgDataArray.add(transactionsObj)
        JSONBuilder unitInfoObj = JSONBuilder.createObject();
        unitInfoObj.put(TERMINAL_CD, ContextHelper.getThreadOperator()?.getOprId() ?: OPERATOR_ID)
        unitInfoObj.put(UNIT_CAT, messageTranslator.getMessage(equip.getEqClass().getDescriptionPropertyKey()))
        unitInfoObj.put(UNIT_NBR, unit.getUnitId())
        unitInfoObj.put(UNITTYPEISO_CD, equip?.getEqEquipType()?.getEqtypId() ?: EMPTY_STR)
        if (equip?.getEqEquipType()?.getEqtypArchetype()?.getEqtypId() != null) {
            unitInfoObj.put(UNITSZTYPE_CD, equip.getEqEquipType().getEqtypArchetype().getEqtypId())
        }
        unitInfoObj.put(OWNERLINE_SCAC, equip?.getEquipmentOwner()?.getBzuScac() ?: EMPTY_STR)
        if (unit.getUnitLineOperator()?.getBzuId() != null) {
            unitInfoObj.put(OWNERLINE_CD, unit.getUnitLineOperator().getBzuId())
        }
        if (equip?.getEquipmentOperator()?.getBzuScac() != null) {
            unitInfoObj.put(USERLINE_SCAC, equip.getEquipmentOperator().getBzuScac())
        }
        if (equip?.getEqTareWeightKg() != null) {
            unitInfoObj.put(TARE_WGT, equip.getEqTareWeightKg().toString())
            unitInfoObj.put(TAREWGT_UNIT, KG)
        }
        //unitInfoObj.put("tarewgt_cd","") //tarewgt_cd not used

        transactionsObj.put(UNITINFO, unitInfoObj)
        List<LinkedHashMap<String, String>> srvEvtList = (List<LinkedHashMap<String, String>>) getSrvEventInfo(unit)
        JSONBuilder SvcEventinfoArray = JSONBuilder.createArray()
        for (LinkedHashMap<String, String> linkedHashMap : (srvEvtList)) {
            JSONBuilder SvcEventinfoObj = JSONBuilder.createObject();
            for (Map.Entry<String, Object> entry : linkedHashMap.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(_svcEventCode)) {
                    SvcEventinfoObj.put(_svcEventCode, entry.getValue())
                } else if (entry.getKey().equalsIgnoreCase(_svcEventDesc)) {
                    SvcEventinfoObj.put(_svcEventDesc, entry.getValue())
                } else if (entry.getKey().equalsIgnoreCase(_svcEventDttm)) {
                    SvcEventinfoObj.put(_svcEventDttm, entry.getValue())
                }
            }
            SvcEventinfoArray.add(SvcEventinfoObj)
        }

        transactionsObj.put(SVCEVENTINFO, SvcEventinfoArray)
        JSONBuilder unitStatusinfoObj = JSONBuilder.createObject();
        // Not used status_cd & status_desc
        //unitStatusinfoObj.put("status_cd","")
        //unitStatusinfoObj.put("status_desc","")
        String unitUseCode = EMPTY_STR
        String unitUseDesc = EMPTY_STR
        if (UnitCategoryEnum.STORAGE.equals(unitCategory)) {
            unitUseCode = EMPTY_CD
            unitUseDesc = EMPTY
        } else {
            unitUseDesc = messageTranslator.getMessage(unitCategory.getDescriptionPropertyKey()).toUpperCase()
            if (UnitCategoryEnum.EXPORT.equals(unitCategory)) {
                unitUseCode = EXPORT_CD
            } else if (UnitCategoryEnum.IMPORT.equals(unitCategory)) {
                unitUseCode = IMPORT_CD

            } else if (UnitCategoryEnum.TRANSSHIP.equals(unitCategory)) {
                unitUseCode = TRANSSHIP_CD

            }
        }
        unitStatusinfoObj.put(UNITUSE_CD, unitUseCode)
        unitStatusinfoObj.put(UNITUSE_DESC, unitUseDesc)
        transactionsObj.put(UNITSTATUSINFO, unitStatusinfoObj)

        TruckTransaction truckTransaction = getTruckTransaction(unit.getUnitId())
        // give depature and arrival info if it is thru Truck
        if (truckTransaction != null) {
            JSONBuilder departureinfoObj = JSONBuilder.createObject();
            JSONBuilder arrivalinfoObj = JSONBuilder.createObject();
            if (truckTransaction.getTranSubType().getKey().startsWith("D") && !TranStatusEnum.CANCEL.equals(truckTransaction.getTranStatus())) {
                departureinfoObj.put(DIRECTION_CD, "DEPARTURE")
                departureinfoObj = getDepartureOrArrivalObj(departureinfoObj, truckTransaction)
            } else if (truckTransaction.getTranSubType().getKey().startsWith("R") && !TranStatusEnum.CANCEL.equals(truckTransaction.getTranStatus())) {
                arrivalinfoObj.put(DIRECTION_CD, "ARRIVAL")
                arrivalinfoObj = getDepartureOrArrivalObj(arrivalinfoObj, truckTransaction)
            }
            transactionsObj.put(ARRIVALINFO, arrivalinfoObj)
            transactionsObj.put(DEPARTUREINFO, departureinfoObj)
        }

        String VoyageNbr = null
        VesselVisitDetails visitDetails = null
        UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
        CarrierVisit carrierVisit = null
        if (ufv != null) {
            if (UnitCategoryEnum.IMPORT.equals(unitCategory) && ufv.getUfvActualIbCv() != null) {
                VoyageNbr = ufv.getUfvActualIbCv().getCarrierIbVoyNbrOrTrainId()
                carrierVisit = ufv.getUfvActualIbCv()

            } else if (UnitCategoryEnum.EXPORT.equals(unitCategory) && ufv.getUfvActualObCv() != null) {
                VoyageNbr = ufv.getUfvActualObCv().getCarrierObVoyNbrOrTrainId()
                carrierVisit = ufv.getUfvActualObCv()

            }
            if (carrierVisit != null) {
                visitDetails = VesselVisitDetails.resolveVvdFromCv(carrierVisit)
            }

        }

        JSONBuilder routeinfoObj = JSONBuilder.createObject();
        if (unit.getUnitRouting()?.getRtgPOL()?.getPointUnlocId() != null) {
            routeinfoObj.put(POL_CD, unit.getUnitRouting().getRtgPOL().getPointUnlocId())
        }
        if (unit.getUnitRouting()?.getRtgPOD1()?.getPointUnlocId() != null) {
            routeinfoObj.put(POD_CD, unit.getUnitRouting().getRtgPOD1().getPointUnlocId())
        }
        if (unit.getUnitRouting()?.getRtgBondedDestination() != null) {
            routeinfoObj.put(PDL_CD, unit.getUnitRouting().getRtgBondedDestination())
        }
        if (carrierVisit?.getCvCvd()?.getLloydsId() != null) {
            routeinfoObj.put(VESSEL_CD, carrierVisit.getCvCvd().getLloydsId())
        }
        if (carrierVisit?.getCarrierVehicleName() != null) {
            routeinfoObj.put(VESSEL_NM, carrierVisit.getCarrierVehicleName())
        }
        if (!StringUtils.isEmpty(VoyageNbr)) {
            routeinfoObj.put(VOYAGE_NBR, VoyageNbr)
        }
        if (carrierVisit?.getCvCvd()?.getLloydsId() != null) {
            routeinfoObj.put(VSLCDTYPE_CD, "L")
        }
        routeinfoObj.put(VSLSTOWAGE_LOC, EMPTY_STR)
        if (visitDetails != null && visitDetails.getVvdTimeStartWork() != null) {
            int targetTime = visitDetails.getVvdTimeStartWork().getHours()
            if (targetTime >= 8 && targetTime < 18)
                routeinfoObj.put(SHIFT_NBR, "1")
            else if ((targetTime >= 18 && targetTime < 24) || (targetTime >= 0 && targetTime < 3))
                routeinfoObj.put(SHIFT_NBR, "2")
            else
                routeinfoObj.put(SHIFT_NBR, "3")
        }
        if (visitDetails?.getCvdETA() != null) {
            routeinfoObj.put(ESTARRIVAL_DT, ISO_DATE_FORMAT.format(visitDetails.getCvdETA()))
        }
        if (carrierVisit?.getCvATA() != null) {
            routeinfoObj.put(ACTARRIVAL_DT, ISO_DATE_FORMAT.format(carrierVisit.getCvATA()))
        }

        transactionsObj.put(ROUTEINFO, routeinfoObj)
        JSONBuilder cargoinfoObj = JSONBuilder.createObject();
        if (unit.getUnitDepartureOrderItem()?.getEqboiOrder()?.getEqboNbr() != null) {
            cargoinfoObj.put(BOOKING_NBR, unit.getUnitDepartureOrderItem().getEqboiOrder().getEqboNbr())
        }
        if (unit.getUnitGoods()?.getGdsBlNbr() != null) {
            cargoinfoObj.put(BOL_NBR, unit.getUnitGoods().getGdsBlNbr())
        }
        cargoinfoObj.put(REEFER_FLG, unit.getUnitRequiresPower() ? YES : NO)
        if (unit.getUnitGoods()?.getGdsReeferRqmnts()?.getRfreqTempLimitMinC() != null) {
            cargoinfoObj.put(TEMP_MIN, unit.getUnitGoods().getGdsReeferRqmnts().getRfreqTempLimitMinC().toString())
            cargoinfoObj.put(MINTEMP_UOM, "CE")
        }

        if (unit.getUnitGoods()?.getGdsReeferRqmnts()?.getRfreqTempLimitMaxC() != null) {
            cargoinfoObj.put(TEMP_MAX, unit.getUnitGoods().getGdsReeferRqmnts().getRfreqTempLimitMaxC().toString())
            cargoinfoObj.put(MAXTEMP_UOM, "CE")
        }
        List<HazardItem> hazardItemList = new ArrayList<>();
        StringBuilder imoSB = new StringBuilder()
        if (unit.getUnitIsHazard()) {
            hazardItemList = (List<HazardItem>) unit.getGoods()?.getGdsHazards()?.getHzrdItems()
            for (HazardItem hazardItem : hazardItemList) {
                if (hazardItem.getHzrdiImdgClass() != null) {
                    imoSB.append(hazardItem.getHzrdiImdgClass().getKey()).append(",")
                }
            }
        }
        cargoinfoObj.put(HAZMAT_FLG, unit.getUnitIsHazard() ? YES : NO)
        if (imoSB.length() > 0) {
            cargoinfoObj.put(IMOCLASS_CD, StringUtils.substring(imoSB.toString(), 0, imoSB.length() - 1))
        }
        cargoinfoObj.put(OD_FLG, unit.getUnitIsOog() ? YES : NO)
        StringBuilder sealNbrs = new StringBuilder()
        if (!StringUtils.isEmpty(unit.getUnitSealNbr1())) {
            sealNbrs.append(unit.getUnitSealNbr1())
        }
        if (!StringUtils.isEmpty(unit.getUnitSealNbr2())) {
            sealNbrs.append(",").append(unit.getUnitSealNbr2())
        }
        if (!StringUtils.isEmpty(unit.getUnitSealNbr3())) {
            sealNbrs.append(",").append(unit.getUnitSealNbr3())
        }
        if (!StringUtils.isEmpty(unit.getUnitSealNbr4())) {
            sealNbrs.append(",").append(unit.getUnitSealNbr4())
        }
        if (sealNbrs.length() > 0) {
            cargoinfoObj.put(SEAL_NBR, sealNbrs.toString())
        }
        cargoinfoObj.put(BCOCOMPANY_NM, EMPTY_STR)
        if (equip?.getEquipmentOperator()?.getBzuScac() != null) {
            cargoinfoObj.put(USERLINE_SCAC, equip.getEquipmentOperator().getBzuScac())
        }
        transactionsObj.put(CARGOINFO, cargoinfoObj)

        List<LinkedHashMap<String, String>> shipmentStatusList = (List<LinkedHashMap<String, String>>) getMapShipmentStatus(unit)
        JSONBuilder shipmentstatusinfoArray = JSONBuilder.createArray()
        for (LinkedHashMap<String, String> linkedHashMap : shipmentStatusList) {
            JSONBuilder shipmentstatusinfoObj = JSONBuilder.createObject();
            for (Map.Entry<String, Object> entry : linkedHashMap.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(_statusCode)) {
                    shipmentstatusinfoObj.put(_statusCode, entry.getValue())
                } else if (entry.getKey().equalsIgnoreCase(_statusDesc)) {
                    shipmentstatusinfoObj.put(_statusDesc, entry.getValue())
                } else if (entry.getKey().equalsIgnoreCase(_statusApplyTime)) {
                    shipmentstatusinfoObj.put(_statusApplyTime, entry.getValue())
                }
            }
            shipmentstatusinfoArray.add(shipmentstatusinfoObj)
        }
        boolean isTMFHold = false
        Collection flagsOnEntity = FlagType.findActiveFlagsOnEntity(unit);
        if (flagsOnEntity != null && flagsOnEntity.size() > 0) {
            for (Flag flag : flagsOnEntity) {
                if (flag.getFlagFlagType() != null) {
                    String flagId = flag.getFlagFlagType().getFlgtypId();
                    if (flagId.contains("TMF")) {
                        isTMFHold = true
                    }
                }
            }
        }

        transactionsObj.put(SHIPMENTSTATUSINFO, shipmentstatusinfoArray)
        JSONBuilder currentconditioninfoObj = JSONBuilder.createObject();
        currentconditioninfoObj.put(DAMAGED_FLG, unit.isUnitDamaged() ? YES : NO)
        LocPosition locPosition = unit.findCurrentPosition()
        String blockNbr = null // give block name if unit is in Yard else give current position
        if (locPosition != null) {
            if (locPosition.getPosName() != null) {
                currentconditioninfoObj.put(YARD_LOC, locPosition.getPosName())
            }
            blockNbr = locPosition.isYardPosition() ? locPosition.getBlockName() : locPosition.getPosName()
            if (blockNbr != null) {
                currentconditioninfoObj.put(BLOCK_NBR, blockNbr)
            }
        }
        if (unit.isReefer()) {
            if (unit.getUnitGoods()?.getGdsReeferRqmnts()?.getRfreqTempRequiredC() != null) {
                currentconditioninfoObj.put(SET_TEMP, unit.getUnitGoods().getGdsReeferRqmnts().getRfreqTempRequiredC().toString())
            }

            ReeferRecord record = unit.getLatestReeferRecord()
            if (record != null && record.getRfrecSupplyTmp() != null) {
                currentconditioninfoObj.put(SUPPLY_TEMP, record.getRfrecSupplyTmp())
            }
            if (record != null && record.getRfrecReturnTmp() != null) {
                currentconditioninfoObj.put(RETURN_TEMP, record.getRfrecReturnTmp().toString())
            }

        }

        if (FreightKindEnum.MTY.equals(unit.getUnitFreightKind()) || FreightKindEnum.FCL.equals(unit.getUnitFreightKind())) {
            currentconditioninfoObj.put(FULLEMPTY_CD, FreightKindEnum.MTY.equals(unit.getUnitFreightKind()) ? EMPTY : FULL)
        }
        if (unit.getUnitGoodsAndCtrWtKg() != null) {
            currentconditioninfoObj.put(GROSS_WGT, unit.getUnitGoodsAndCtrWtKg().toString())
            currentconditioninfoObj.put(GROSSWGT_UNIT, KG)
        }

        if (unit.getUnitGoodsAndCtrWtKgVerfiedGross() != null) {
            currentconditioninfoObj.put(ACTUAL_WGT, unit.getUnitGoodsAndCtrWtKgVerfiedGross().toString())
            currentconditioninfoObj.put(ACTUALWGT_UNIT, KG)
        }

        if (unit.getUnitAcryId() != null) {
            currentconditioninfoObj.put(GENSET_NBR, unit.getUnitAcryId())
        }
        if (unit.getUnitCarriageUnit()?.getUnitId() != null) {
            currentconditioninfoObj.put(CHASSIS_NBR, unit.getUnitCarriageUnit().getUnitId())
        }
        if (unit.getUnitRouting()?.getRtgGroup()?.getGrpId() != null) {
            currentconditioninfoObj.put(GROUP_CD, unit.getUnitRouting().getRtgGroup().getGrpId())
        }
        currentconditioninfoObj.put(TMF_FLG, isTMFHold ? YES : NO)
        transactionsObj.put(CURRENTCONDITIONINFO, currentconditioninfoObj)
        LOGGER.debug("response string ::::::::::::: " + mainObj.toJSONString())
        for (IntegrationService integrationService : integrationServiceList) {
            if (mainObj.toJSONString() != null) {

                logRequestToInterfaceMessage(unit, LogicalEntityEnum.UNIT, integrationService, mainObj.toJSONString());
            }
        }
    }

    private static TruckTransaction getTruckTransaction(String unitId) {
        final TranSubTypeEnum[] tranTypes = [TranSubTypeEnum.DI, TranSubTypeEnum.DE, TranSubTypeEnum.DM, TranSubTypeEnum.DB, TranSubTypeEnum.DC, TranSubTypeEnum.RI, TranSubTypeEnum.RE, TranSubTypeEnum.RM, TranSubTypeEnum.RB, TranSubTypeEnum.RC]
        DomainQuery dq = QueryUtils.createDomainQuery(RoadEntity.TRUCK_TRANSACTION)
                .addDqPredicate(PredicateFactory.eq(RoadField.TRAN_CTR_NBR, unitId))
                .addDqPredicate(PredicateFactory.in(RoadField.TRAN_SUB_TYPE, tranTypes))
                .addDqOrdering(Ordering.desc(RoadField.TRAN_CREATED))
                .setDqMaxResults(1)
        List<TruckTransaction> truckTransactionList = (List<TruckTransaction>) HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        return truckTransactionList.size() > 0 ? truckTransactionList[0] : null
    }

    public JSONBuilder getDepartureOrArrivalObj(JSONBuilder departureinfoObj, TruckTransaction truckTransaction) {
        if (truckTransaction.getTranChsNbr() != null) {
            departureinfoObj.put(CHASSIS_NBR, truckTransaction.getTranChsNbr())
        }
        departureinfoObj.put(CARRIERTYPE_CD, "MC")
        departureinfoObj.put(CARRIERTYPE_DESC, "MOTOR CARRIER")
        if (truckTransaction.getTranCarrierVisit()?.getCarrierOperatorId() != null) {
            departureinfoObj.put(CARRIER_CD, truckTransaction.getTranCarrierVisit().getCarrierOperatorId())
        }
        if (truckTransaction.getTranTruckVisit()?.getTvdtlsTruckAeiTagId() != null) {
            departureinfoObj.put(RFID_NBR, truckTransaction.getTranTruckVisit().getTvdtlsTruckAeiTagId())
        }
        if (truckTransaction.getTranTruckVisit()?.getTruckLicenseNbr() != null) {
            departureinfoObj.put(PLATE_NBR, truckTransaction.getTranTruckVisit().getTruckLicenseNbr())
        }
        if (truckTransaction.getTranTruckVisit()?.getTvdtlsTruck()?.getTruckLicenseState() != null) {
            departureinfoObj.put(PLATESTATE_CD, truckTransaction.getTranTruckVisit().getTvdtlsTruck().getTruckLicenseState())
        }
        //departureinfoObj.put("svctype_cd","")
        //departureinfoObj.put("svctype_desc","") // Not used svctype_cd & svctype_desc
        if (truckTransaction.getCtrAccessoryId() != null || truckTransaction.getChsAccessoryId() != null) {
            departureinfoObj.put(GENSET_NBR, truckTransaction.getCtrAccessoryId() ?: truckTransaction.getChsAccessoryId())
        }
        return departureinfoObj;
    }

    public List getMapShipmentStatus(Unit unit) {

        EventManager eventManager = (EventManager) Roastery.getBean(EventManager.BEAN_ID)
        List list = new ArrayList()

        UnitFacilityVisit ufv = unit.getUfvForFacilityLiveOnly(ContextHelper.getThreadFacility())
        if (ufv != null && ufv.isTransitStateBeyond(UfvTransitStateEnum.S20_INBOUND)) {
            list.add(setStatus("A", "Arrived", SHIPMENT, ISO_DATE_FORMAT.format(now)))
        }


        EventType unitLoadEvnt = EventType.findEventType(EventEnum.UNIT_LOAD.getKey());
        if (unitLoadEvnt != null && unit != null) {
            Event event = eventManager.getMostRecentEventByType(unitLoadEvnt, unit);
            if (event != null) {
                list.add(setStatus("AE", "Loaded On Vessel", SHIPMENT, event.getEvntAppliedDate() ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : EMPTY_STR))
            }
        }

        EventType unitInEvnt = EventType.findEventType(EventEnum.UNIT_IN_GATE.getKey());
        if (unitInEvnt != null && unit != null) {
            Event event = eventManager.getMostRecentEventByType(unitInEvnt, unit);
            if (event != null) {
                list.add(setStatus("I", "In Gate", SHIPMENT, event.getEvntAppliedDate() ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : EMPTY_STR))
            }
        }

        EventType outGateEvnt = EventType.findEventType(EventEnum.UNIT_OUT_GATE.getKey());
        if (outGateEvnt != null && unit != null) {
            Event event = eventManager.getMostRecentEventByType(outGateEvnt, unit);
            if (event != null) {
                list.add(setStatus("OA", "Out-gate", SHIPMENT, event.getEvntAppliedDate() ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : EMPTY_STR))
            }
        }
        EventType unitRamp = EventType.findEventType(EventEnum.UNIT_RAMP.getKey());
        if (unitRamp != null && unit != null) {
            Event event = eventManager.getMostRecentEventByType(unitRamp, unit);
            if (event != null) {
                list.add(setStatus("AL", "Loaded on Rail", SHIPMENT, event.getEvntAppliedDate() ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : EMPTY_STR))
            }
        }
        EventType unitDisch = EventType.findEventType(EventEnum.UNIT_DISCH.getKey());
        if (unitDisch != null && unit != null) {
            Event event = eventManager.getMostRecentEventByType(unitDisch, unit);
            if (event != null) {
                list.add(setStatus("UV", "Unload From Vessel", SHIPMENT, event.getEvntAppliedDate() ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : EMPTY_STR))
            }
        }
        if (!unit.getAllAttachedChassis().isEmpty()) {
            list.add(setStatus("CB", "Chassis Tie", SHIPMENT, ISO_DATE_FORMAT.format(now)))
        }

        if (unit.isReefer()) {
            list.add(setStatus("CZ", "Reefer Container", SHIPMENT, ISO_DATE_FORMAT.format(now)))
        }

        if (unit.isOutOfGuage()) {
            list.add(setStatus("OD", "Over Dimension", SHIPMENT, ISO_DATE_FORMAT.format(now)))
        }

        if (unit.getUnitIsHazard()) {
            list.add(setStatus("H1", "Hazmat", SHIPMENT, ISO_DATE_FORMAT.format(now)))
        }


        return list
    }

    protected ServicesManager getServiceManager() {
        return (ServicesManager) Roastery.getBean("servicesManager");
    }


    public Map setStatus(String code, String desc, String info, String time) {
        Map responseMap = new LinkedHashMap()
        String keyCode = SHIPMENT.equals(info) ? _statusCode : _svcEventCode
        String keyDesc = SHIPMENT.equals(info) ? _statusDesc : _svcEventDesc
        String keyTime = SHIPMENT.equals(info) ? _statusApplyTime : _svcEventDttm
        responseMap.put(keyCode, code)
        responseMap.put(keyDesc, desc)
        responseMap.put(keyTime, time)
        return responseMap
    }

    public List getSrvEventInfo(Unit unit) {
        List srvEventList = new ArrayList()
        EventManager eventManager = (EventManager) Roastery.getBean(EventManager.BEAN_ID)

        EventType outGate = EventType.findEventType(EventEnum.UNIT_OUT_GATE.getKey());
        if (outGate != null && unit != null) {
            Event event = eventManager.getMostRecentEventByType(outGate, unit);
            if (event != null) {
                srvEventList.add(setStatus("OA", "Out-gate", SRVC_EVENT, event.getEvntAppliedDate() ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : EMPTY_STR))
            }
        }

        EventType inGate = EventType.findEventType(EventEnum.UNIT_IN_GATE.getKey());
        if (inGate != null && unit != null) {
            Event event = eventManager.getMostRecentEventByType(inGate, unit);
            if (event != null) {
                srvEventList.add(setStatus("I", "In-gate", SRVC_EVENT, event.getEvntAppliedDate() ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : EMPTY_STR))
            }
        }

        EventType unitRamp = EventType.findEventType(EventEnum.UNIT_RAMP.getKey());
        if (unitRamp != null && unit != null) {
            Event event = eventManager.getMostRecentEventByType(unitRamp, unit);
            if (event != null) {
                srvEventList.add(setStatus("AL", "Loaded on Rail", SRVC_EVENT, event.getEvntAppliedDate() ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : EMPTY_STR))
            }
        }

        EventType unitDeRamp = EventType.findEventType(EventEnum.UNIT_DERAMP.getKey());
        if (unitDeRamp != null && unit != null) {
            Event event = eventManager.getMostRecentEventByType(unitDeRamp, unit);
            if (event != null) {
                srvEventList.add(setStatus("UR", "Rail Unload", SRVC_EVENT, event.getEvntAppliedDate() ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : EMPTY_STR))
            }
        }
        EventType unitDisch = EventType.findEventType(EventEnum.UNIT_DISCH.getKey());
        if (unitDisch != null && unit != null) {
            Event event = eventManager.getMostRecentEventByType(unitDisch, unit);
            if (event != null) {
                srvEventList.add(setStatus("UV", "Unload From Vessel", SRVC_EVENT, event.getEvntAppliedDate() ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : EMPTY_STR))
            }
        }
        EventType unitLoad = EventType.findEventType(EventEnum.UNIT_LOAD.getKey());
        if (unitLoad != null && unit != null) {
            Event event = eventManager.getMostRecentEventByType(unitLoad, unit);
            if (event != null) {
                srvEventList.add(setStatus("UV", "Loaded On Vessel", SRVC_EVENT, event.getEvntAppliedDate() ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : EMPTY_STR))
            }
        }
        return srvEventList

    }

    private
    static IntegrationServiceMessage logRequestToInterfaceMessage(Unit unit, LogicalEntityEnum inLogicalEntityEnum,
                                                                  IntegrationService inIntegrationService, String inMessagePayload) {
        IntegrationServiceMessage integrationServiceMessage = new IntegrationServiceMessage();
        integrationServiceMessage.setIsmEventPrimaryKey((Long) unit.getPrimaryKey());
        integrationServiceMessage.setIsmEntityClass(inLogicalEntityEnum);
        integrationServiceMessage.setIsmEntityNaturalKey(unit.getHumanReadableKey());
        try {
            if (inIntegrationService) {
                integrationServiceMessage.setIsmIntegrationService(inIntegrationService);
                integrationServiceMessage.setIsmFirstSendTime(ArgoUtils.timeNow());
                //integrationServiceMessage.setIsmLastSendTime(ArgoUtils.timeNow());
            }
            integrationServiceMessage.setIsmMessagePayload(inMessagePayload);
            integrationServiceMessage.setIsmSeqNbr(new IntegrationServMessageSequenceProvider().getNextSequenceId());
            ScopeCoordinates scopeCoordinates = ContextHelper.getThreadUserContext().getScopeCoordinate();
            integrationServiceMessage.setIsmScopeGkey((String) scopeCoordinates.getScopeLevelCoord(scopeCoordinates.getDepth()));
            integrationServiceMessage.setIsmScopeLevel(scopeCoordinates.getDepth());
            integrationServiceMessage.setIsmUserString3("false")
            HibernateApi.getInstance().save(integrationServiceMessage);

        } catch (Exception e) {
        }
        HibernateApi.getInstance().flush();
        return integrationServiceMessage;
    }

    public static class IntegrationServMessageSequenceProvider extends ArgoSequenceProvider {
        public Long getNextSequenceId() {
            return super.getNextSeqValue(serviceMsgSequence, ContextHelper.getThreadFacilityKey() != null ? (Long) ContextHelper.getThreadFacilityKey() : 1l);
        }
        private String serviceMsgSequence = "INT_MSG_SEQ";
    }

    private static List<IntegrationService> getUnitDetailsSyncIntegrationServices(String integrationServiceName, boolean isGroup) {
        MetafieldIdList metafieldIdList = new MetafieldIdList(3);
        metafieldIdList.add(IntegrationServiceField.INTSERV_URL);
        metafieldIdList.add(IntegrationServiceField.INTSERV_USER_ID);
        metafieldIdList.add(IntegrationServiceField.INTSERV_PASSWORD);
        PredicateIntf namePredicate;
        if (isGroup) {
            namePredicate = PredicateFactory.eq(IntegrationServiceField.INTSERV_GROUP, integrationServiceName);
        } else {
            namePredicate = PredicateFactory.eq(IntegrationServiceField.INTSERV_NAME, integrationServiceName);
        }

        DomainQuery dq = QueryUtils.createDomainQuery("IntegrationService").addDqFields(metafieldIdList)
                .addDqPredicate(namePredicate)
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_TYPE, IntegrationServiceTypeEnum.WEB_SERVICE))
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_ACTIVE, Boolean.TRUE))
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_DIRECTION, IntegrationServiceDirectionEnum.OUTBOUND));
        dq.setScopingEnabled(false);
        LOGGER.debug("domainQuery" + dq)
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        //return Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);
    }

    private static final String SOURCE_CD = "source_cd"
    private static final String SENDER_CD = "sender_cd"
    private static final String RECEIVER_CD = "receiver_cd"
    private static final String GSN_NBR = "gsn_nbr"
    private static final String CONTROL_NBR = "control_nbr"
    private static final String INTERCHANGE_DTTM = "interchange_dttm"
    private static final String TRANS_CNT = "trans_cnt"
    private static final String RCVDTRANS_CNT = "rcvdtrans_cnt"
    private static final String TRANS_DTTM = "trans_dttm"
    private static final String TERMINAL_CD = "terminal_cd"
    private static final String UNIT_CAT = "unit_cat"
    private static final String UNIT_NBR = "unit_nbr"
    private static final String UNITTYPEISO_CD = "unittypeiso_cd"
    private static final String UNITSZTYPE_CD = "unitsztype_cd"
    private static final String OWNERLINE_CD = "ownerline_cd"
    private static final String OWNERLINE_SCAC = "ownerline_scac"
    private static final String TARE_WGT = "tare_wgt"
    private static final String TAREWGT_CD = "tarewgt_cd"
    private static final String TAREWGT_UNIT = "tarewgt_unit"
    private static final String STATUS_CD = "status_cd"
    private static final String STATUS_DESC = "status_desc"
    private static final String UNITUSE_CD = "unituse_cd"
    private static final String UNITUSE_DESC = "unituse_desc"
    private static final String DIRECTION_CD = "direction_cd"
    private static final String CHASSIS_NBR = "chassis_nbr"
    private static final String CARRIERTYPE_CD = "carriertype_cd"
    private static final String CARRIERTYPE_DESC = "carriertype_desc"
    private static final String CARRIER_CD = "carrier_cd"
    private static final String RFID_NBR = "rfid_nbr"
    private static final String PLATE_NBR = "plate_nbr"
    private static final String PLATESTATE_CD = "platestate_cd"
    private static final String SVCTYPE_CD = "svctype_cd"
    private static final String SVCTYPE_DESC = "svctype_desc"
    private static final String POL_CD = "pol_cd"
    private static final String POD_CD = "pod_cd"
    private static final String PDL_CD = "pdl_cd"
    private static final String VESSEL_CD = "vessel_cd"
    private static final String VESSEL_NM = "vessel_nm"
    private static final String VOYAGE_NBR = "voyage_nbr"
    private static final String VSLCDTYPE_CD = "vslcdtype_cd"
    private static final String VSLSTOWAGE_LOC = "vslstowage_loc"
    private static final String ESTARRIVAL_DT = "estarrival_dt"
    private static final String ACTARRIVAL_DT = "actarrival_dt"
    private static final String SHIFT_NBR = "shift_nbr"
    private static final String BOOKING_NBR = "booking_nbr"
    private static final String BOL_NBR = "bol_nbr"
    private static final String REEFER_FLG = "reefer_flg"
    private static final String TEMP_MIN = "temp_min"
    private static final String MINTEMP_UOM = "mintemp_uom"
    private static final String TEMP_MAX = "temp_max"
    private static final String MAXTEMP_UOM = "maxtemp_uom"
    private static final String HAZMAT_FLG = "hazmat_flg"
    private static final String IMOCLASS_CD = "imoclass_cd"
    private static final String OD_FLG = "od_flg"
    private static final String GENSET_NBR = "genset_nbr"
    private static final String SEAL_NBR = "seal_nbr"
    private static final String BCOCOMPANY_NM = "bcocompany_nm"
    private static final String USERLINE_SCAC = "userline_scac"
    private static final String DAMAGED_FLG = "damaged_flg"
    private static final String YARD_LOC = "yard_loc"
    private static final String BLOCK_NBR = "block_nbr"
    private static final String SUPPLY_TEMP = "supply_temp"
    private static final String RETURN_TEMP = "return_temp"
    private static final String SET_TEMP = "set_temp"
    private static final String FULLEMPTY_CD = "fullempty_cd"
    private static final String GROSS_WGT = "gross_wgt"
    private static final String GROSSWGT_UNIT = "grosswgt_unit"
    private static final String ACTUAL_WGT = "actual_wgt"
    private static final String ACTUALWGT_UNIT = "actualwgt_unit"
    private static final String GROUP_CD = "group_cd"
    private static final String TMF_FLG = "tmf_flg"
    private static final String CURRENTCONDITIONINFO = "currentconditioninfo"
    private static final String SHIPMENTSTATUSINFO = "shipmentstatusinfo"
    private static final String CARGOINFO = "cargoinfo"
    private static final String ROUTEINFO = "routeinfo"
    private static final String DEPARTUREINFO = "departureinfo"
    private static final String ARRIVALINFO = "arrivalinfo"
    private static final String UNITSTATUSINFO = "unitstatusinfo"
    private static final String SVCEVENTINFO = "svceventinfo"
    private static final String UNITINFO = "unitinfo"
    private static final String MSGDATA = "msgdata"
    private static final String MSGHEADER = "msgheader"
    private static final String EMPTY_STR = ""
    private static DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static final String _statusCode = "shipmentstaus_cd"
    private static final String _statusDesc = "shipmentstatus_desc"
    private static final String _statusApplyTime = "shipmentstatus_dttm"
    private static final String _svcEventCode = "svcevent_cd"
    private static final String _svcEventDesc = "svcevent_desc"
    private static final String _svcEventDttm = "svcevent_dttm"
    private static final String SHIPMENT = "Shipment"
    private static final String SRVC_EVENT = "service_event"
    private static final COAST_GUARD_HOLD = "C.G."
    private static final FDA = "FDA"
    private static final String KG = "Kilograms"
    private static final String EMPTY_CD = "E"
    private static final String EXPORT_CD = "X"
    private static final String IMPORT_CD = "I"
    private static final String TRANSSHIP_CD = "T"
    private static final String YES = "Y"
    private static final String NO = "N"
    private static final String EMPTY = "EMPTY"
    private static final String FULL = "FULL"
    private static final String OPERATOR_ID = "HUSKY"
    private static final String RECEIVER_CD_VAL = "ECP"
    private static final String INT_SERV_NAME = "EQUIP_STATUS"
    Date now = ArgoUtils.timeNow()
    private String invoiceClass = "IMPORT_PRE_PAY";
    MessageTranslator messageTranslator = TranslationUtils.getTranslationContext(ContextHelper.getThreadUserContext()).getMessageTranslator()
    private static final Logger LOGGER = Logger.getLogger(this.class)


}
