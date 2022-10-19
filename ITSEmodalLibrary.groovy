package ITS

import com.navis.argo.ArgoPropertyKeys
import com.navis.argo.ContextHelper
import com.navis.argo.EdiInvoice
import com.navis.argo.InvoiceCharge
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.IImpediment
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.*
import com.navis.argo.business.integration.IntegrationServiceMessage
import com.navis.argo.business.model.ArgoSequenceProvider
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.cargo.business.model.BillOfLading
import com.navis.carina.integrationservice.business.IntegrationService
import com.navis.external.framework.AbstractExtensionCallback
import com.navis.framework.IntegrationServiceField
import com.navis.framework.business.Roastery
import com.navis.framework.business.atoms.IntegrationServiceDirectionEnum
import com.navis.framework.business.atoms.IntegrationServiceTypeEnum
import com.navis.framework.business.atoms.MassUnitEnum
import com.navis.framework.metafields.MetafieldIdList
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.HibernatingEntity
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.portal.query.PredicateIntf
import com.navis.framework.presentation.internationalization.MessageTranslator
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.framework.util.internationalization.TranslationUtils
import com.navis.framework.util.scope.ScopeCoordinates
import com.navis.framework.util.unit.UnitUtils
import com.navis.framework.zk.util.JSONBuilder
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.api.UnitStorageManager
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.units.ReeferRecord
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.business.units.UnitStorageManagerPea
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.road.RoadEntity
import com.navis.road.RoadField
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.services.business.api.EventManager
import com.navis.services.business.event.Event
import com.navis.services.business.event.EventFieldChange
import com.navis.services.business.rules.EventType
import com.navis.services.business.rules.Flag
import com.navis.services.business.rules.FlagType
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import wslite.json.JSONObject

import java.text.DateFormat
import java.text.SimpleDateFormat

/**
 *
 * Created by annalakshmig@weservetech.com
 */
class ITSEmodalLibrary extends AbstractExtensionCallback {
    public void execute(HibernatingEntity inHibernatingEntity, Event event) {
        LOGGER.setLevel(Level.DEBUG)
        String className = inHibernatingEntity.getEntityName();

        if (className.equalsIgnoreCase("Unit")) {
            Unit inUnit = (Unit) inHibernatingEntity;
            List<IntegrationService> integrationServiceList = getUnitDetailsSyncIntegrationServices(INT_SERV_NAME_CTR, false);

            for (IntegrationService integrationService : integrationServiceList) {
                String requestMessage;
                requestMessage = prepareEquipmentStatusFeedEmodalMsg(inUnit, event);
                if (requestMessage != null) {
                    logRequestToInterfaceMessage(inUnit, LogicalEntityEnum.UNIT, integrationService, requestMessage, null);
                }
            }

        } else if (className.equalsIgnoreCase("Booking")) {
            Booking booking = (Booking) inHibernatingEntity;

            List<IntegrationService> integrationServiceList = getUnitDetailsSyncIntegrationServices(INT_SERV_NAME_BKG, false);

            for (IntegrationService integrationService : integrationServiceList) {
                String requestMessage;

                requestMessage = prepareBookingEmodalMsg(booking, event);
                if (requestMessage != null) {
                    logRequestToInterfaceMessage(booking, LogicalEntityEnum.BKG, integrationService, requestMessage, null);
                }

            }
        }
    }

    public void execute(HibernatingEntity inHibernatingEntity, Event event, boolean isCtrNotification) {
        LOGGER.setLevel(Level.DEBUG)
        String className = inHibernatingEntity.getEntityName();
        if (className.equalsIgnoreCase("Unit")) {
            Unit inUnit = (Unit) inHibernatingEntity;
            Long seqNbr = new IntegrationServMessageSequenceProvider().getNextSequenceId()
            List<IntegrationService> integrationServiceList = getUnitDetailsSyncIntegrationServices("ITS_CTR_NOTIFICATION", false);
            UnitFacilityVisit unitFacilityVisit = inUnit.getUnitActiveUfvNowActive()
            if (unitFacilityVisit != null) {
                for (IntegrationService integrationService : integrationServiceList) {
                    String requestMessage;
                    //if (integrationService.getIntservName() != null && integrationService.getIntservName().startsWith("CARGOES")) {
                    requestMessage = prepareCtrNotificationMsg(unitFacilityVisit, event, seqNbr);
                    //}
                    if (requestMessage != null) {

                        logRequestToInterfaceMessage(inUnit, LogicalEntityEnum.UNIT, integrationService, requestMessage, seqNbr);
                    }
                }
            }
        }
    }

    String prepareCtrNotificationMsg(UnitFacilityVisit ufv, Event event, Long seqNbr) {
        String eventId = event.getEventTypeId()
        String eventcd = ""
        String eventDtTm = ""

        JSONBuilder mainObj = JSONBuilder.createObject();
        mainObj.put(CONTAINER_NUMBER, ufv.getUfvUnit().getUnitId())
        mainObj.put(UNIT_ID, ufv.getUfvUnit().getUnitGkey())
        mainObj.put(MESSAGE_ID, seqNbr)
        switch (eventId) {
            case EventEnum.UNIT_DISCH.getKey():
                eventcd = VESSEL_DISCHARGE
                eventDtTm = ISO_DATE_FORMAT.format(event.getEvntAppliedDate())
                break
            case UNIT_FD_MOVE:
                eventcd = FIRST_DELIVERABLE
                eventDtTm = ISO_DATE_FORMAT.format(event.getEvntAppliedDate())
                break
            case EventEnum.UNIT_OUT_GATE.getKey():
                eventcd = OUT_GATED
                eventDtTm = ISO_DATE_FORMAT.format(event.getEvntAppliedDate())
                break
            case EventEnum.UNIT_ENABLE_ROAD.getKey():
                eventcd = FIRST_AVAILABLE_FOR_TRUCK
                eventDtTm = ISO_DATE_FORMAT.format(event.getEvntAppliedDate())
                break
            case EventEnum.UNIT_ENABLE_RAIL.getKey():
                eventcd = FIRST_AVAILABLE_FOR_RAIL
                eventDtTm = ISO_DATE_FORMAT.format(event.getEvntAppliedDate())
                break
            case EventEnum.UNIT_RAMP.getKey():
                eventcd = RAIL_LOAD
                eventDtTm = ISO_DATE_FORMAT.format(event.getEvntAppliedDate())
                break

        }
        mainObj.put(EVENT_CD, eventcd)
        mainObj.put(EVENT_DT_TM, eventDtTm)
        mainObj.put(CREATE_DT_TM, ISO_DATE_FORMAT.format(ArgoUtils.timeNow()))
        return mainObj.toJSONString()

    }

    String prepareBookingEmodalMsg(Booking booking, Event event) {
        if (booking == null || event == null) return null
        final String eventID = event?.getEvntEventType()?.getId()
        final CarrierVisit carrierVisit = booking.getEqoVesselVisit()
        final VesselVisitDetails vesselVisitDetails = carrierVisit ? VesselVisitDetails.resolveVvdFromCv(carrierVisit) : null

        final Set<EquipmentOrderItem> equipmentOrderItems = (Set<EquipmentOrderItem>) booking.getEqboOrderItems() ?: new HashSet<EquipmentOrderItem>()
        final List<HazardItem> hazards = (List<HazardItem>) booking?.getEqoHazards()?.getHzrdItems() ?: new ArrayList<HazardItem>()

        final Set<EventFieldChange> eventChanges = (Set<EventFieldChange>) event?.getEvntFieldChanges()
        final EventFieldChange bookingNumberChange = eventChanges?.find { EventFieldChange change -> change.getPrevVal() && change.getMetafieldId() == "eqboNbr" }
        final String oldBookingNumber = bookingNumberChange?.getPrevVal() ?: ""

        final String allReservedUnits = {
            final DomainQuery reservedUnitsDq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                    .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_DEPARTURE_ORDER, booking.getEqboGkey()))
            final List<Unit> reservedUnits = (List<Unit>) HibernateApi.getInstance().findEntitiesByDomainQuery(reservedUnitsDq)
            if (!reservedUnits) {
                return ""
            }
            String ret = ""
            for (Unit unit in reservedUnits) {
                ret += "," + unit.getUnitId()
            }
            return ret.substring(1)
        }()
        boolean isTMFHold = false


        Collection flagsOnEntity = FlagType.findActiveFlagsOnEntity(booking);
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
        JSONBuilder mainObj = JSONBuilder.createObject();
        JSONBuilder reservedEqArray = JSONBuilder.createArray()
        if (eventID != null) {
            mainObj.put(ACTION, eventID.endsWith("_CREATE") ? "A" : "U")
        } else {
            mainObj.put(ACTION, "D")
        }
        mainObj.put(BKG_TERMINAL_CD, ITS)
        mainObj.put(SO_NBR, booking.getEqboNbr())
        mainObj.put(LINE_SCAC, booking.getEqoLine()?.getBzuScac() ?: "")
        mainObj.put(LLOYDS_CODE, vesselVisitDetails?.getVvdVessel()?.getVesLloydsId() ?: "")
        mainObj.put(RAIL_EXEMPT, "Y")
        mainObj.put(OUT_VOY_NBR, vesselVisitDetails?.getVvdObVygNbr() ?: "")
        mainObj.put(TMF_CLAIMED, isTMFHold ? "Y" : "N")
        mainObj.put(SUB_TYPE, booking.getEqboSubType()?.getKey() ?: "")
        reservedEqArray.add(allReservedUnits)
        mainObj.put(RESERVED_EQ, reservedEqArray)
        mainObj.put(SO_GKEY, booking.getEqboGkey())
        mainObj.put(SO_OLD_NBR, oldBookingNumber)
        if (event != null) {
            mainObj.put(POSTED, event.getEvntAppliedDate()?.format(BKG_ISO_DATE_FORMAT) ?: "")
        } else {
            mainObj.put(POSTED, new Date()?.format(BKG_ISO_DATE_FORMAT) ?: "")
        }
        JSONBuilder portArray = JSONBuilder.createArray()
        JSONBuilder portLoadObj = JSONBuilder.createObject();
        portLoadObj.put(FUNCTION_CD, "L")
        portLoadObj.put(PORT_NM, booking.getEqoPol()?.getPointUnlocId() ?: "")
        portArray.add(portLoadObj)
        JSONBuilder portDestObj = JSONBuilder.createObject();
        portDestObj.put(FUNCTION_CD, "D")
        portDestObj.put(PORT_NM, booking.getEqoPod1()?.getPointUnlocId() ?: "")
        portArray.add(portDestObj)
        mainObj.put(PORT, portArray)
        JSONBuilder hazArray = JSONBuilder.createArray()
        for (HazardItem hazardItem : hazards) {
            JSONBuilder hazObj = JSONBuilder.createObject();
            hazObj.put(UN_NBR, hazardItem.getHzrdiUNnum())
            hazObj.put(HAZ_CLASS, hazardItem.getHzrdiImdgClass()?.getKey())
            hazObj.put(COMMODITY_WEIGHT, UnitUtils.convertTo(hazardItem.getHzrdiWeight() ?: 0.0d, MassUnitEnum.KILOGRAMS, MassUnitEnum.POUNDS))
            hazObj.put(QUANTITY, hazardItem.getHzrdiQuantity())
            hazObj.put(PKG_GROUP, hazardItem.getHzrdiPackingGroup()?.getKey())
            hazObj.put(EMERGENCY_PHONE, hazardItem.getHzrdiEmergencyTelephone())
            hazObj.put(COMMODITY_NAME, hazardItem.getHzrdiProperName())
            hazObj.put(LIMITED_QUANTITY, hazardItem.getHzrdiLtdQty() ? "Y" : "N")
            hazObj.put(COMMENTS, hazardItem.getHzrdiNotes())
            hazArray.add(hazObj)
        }
        mainObj.put(SO_HAZ, hazArray)
        JSONBuilder soItemDetailArray = JSONBuilder.createArray()
        for (EquipmentOrderItem orderItem : equipmentOrderItems) {
            JSONBuilder soItemObj = JSONBuilder.createObject();
            soItemObj.put(SZ_TP_ID, orderItem.getEqoiSampleEquipType()?.getEqtypId() ?: "")
            soItemObj.put(QTY, orderItem.getEqoiQty())
            soItemObj.put(PICKUP_TALLY, orderItem.getEqoiTally())
            soItemObj.put(RECEIVED, orderItem.getEqoiTallyReceive())
            soItemObj.put(IS_OD, orderItem.getEqoiIsOog() ? "Y" : "N")
            soItemObj.put(IS_REEFER, orderItem.hasReeferRequirements() ? "Y" : "N")
            soItemObj.put(SO_ITEM_SEQ_NBR, orderItem.getEqoiSeqNbr() ?: "")
            soItemObj.put(OVERRIDE_GATE_LOCK, booking.getEqoOverrideCutoff() ? "Y" : "N")
            if (orderItem.isHazardous()) {
                final List<HazardItem> hazardItems = (List<HazardItem>) orderItem.getEqoiHazards()?.getHzrdItems() ?: new ArrayList<HazardItem>()
                JSONBuilder hazItemArray = JSONBuilder.createArray()
                for (HazardItem hazard : hazardItems) {
                    JSONBuilder hazItemObj = JSONBuilder.createObject();
                    hazItemObj.put(SO_ITEM_UN_NBR, hazard.getHzrdiUNnum())
                    hazItemObj.put(SO_ITEM_HAZ_CLASS, hazard.getHzrdiImdgClass()?.getKey())
                    hazItemObj.put(SO_ITEM_COMM_WEIGHT, UnitUtils.convertTo(hazard.getHzrdiWeight() ?: 0.0d, MassUnitEnum.KILOGRAMS, MassUnitEnum.POUNDS))
                    hazItemObj.put(SO_ITEM_QUANTITY, hazard.getHzrdiQuantity())
                    hazItemObj.put(SO_ITEM_PKG_GROUP, hazard.getHzrdiPackingGroup()?.getKey())
                    hazItemObj.put(SO_ITEM_EMERGENCY_PH, hazard.getHzrdiEmergencyTelephone())
                    hazItemObj.put(SO_ITEM_COMM_NAME, hazard.getHzrdiProperName() ?: "")
                    hazItemObj.put(SO_ITEM_LIMITED_QUAN, hazard.getHzrdiLtdQty() ? "Y" : "N")
                    hazItemObj.put(SO_ITEM_COMMENTS, hazard.getHzrdiNotes() ?: "")
                    hazItemArray.add(hazItemObj)
                }
                soItemObj.put(SO_ITEM_HAZ, hazItemArray)
            }
            JSONBuilder soItemLockArray = JSONBuilder.createArray()
            final String cargoCutoff = vesselVisitDetails?.getVvdTimeCargoCutoff()?.format(BKG_ISO_DATE_FORMAT) ?: ""
            JSONBuilder soItemLockCargoObj = JSONBuilder.createObject();
            soItemLockCargoObj.put(RECORD_TYPE, "SOIT")
            soItemLockCargoObj.put(LOCK_TYPE, "FULLIN")
            soItemLockCargoObj.put(LOCK_UNTIL, "")
            soItemLockCargoObj.put(LOCK_AFTER, cargoCutoff)
            soItemLockCargoObj.put(EQUIPMENT_TYPE, "Null")
            soItemLockArray.add(soItemLockCargoObj)

            if (orderItem.hasReeferRequirements()) {
                final String reeferCutoff = vesselVisitDetails?.getVvdTimeReeferCutoff()?.format(BKG_ISO_DATE_FORMAT) ?: ""
                JSONBuilder soItemLockReeferObj = JSONBuilder.createObject();
                soItemLockReeferObj.put(RECORD_TYPE, "SOIT")
                soItemLockReeferObj.put(LOCK_TYPE, "FULLIN")
                soItemLockReeferObj.put(LOCK_UNTIL, "")
                soItemLockReeferObj.put(LOCK_AFTER, reeferCutoff)
                soItemLockReeferObj.put(EQUIPMENT_TYPE, "REEFER")
                soItemLockArray.add(soItemLockReeferObj)
            }
            if (orderItem.isHazardous()) {
                final String hazCutoff = vesselVisitDetails?.getVvdTimeHazCutoff()?.format(BKG_ISO_DATE_FORMAT) ?: ""
                JSONBuilder soItemLockHazObj = JSONBuilder.createObject();
                soItemLockHazObj.put(RECORD_TYPE, "SOIT")
                soItemLockHazObj.put(LOCK_TYPE, "FULLIN")
                soItemLockHazObj.put(LOCK_UNTIL, "")
                soItemLockHazObj.put(LOCK_AFTER, hazCutoff)
                soItemLockHazObj.put(EQUIPMENT_TYPE, "HAZARDOUS")
                soItemLockArray.add(soItemLockHazObj)

            }
            soItemObj.put(SO_ITEM_LOCKS, soItemLockArray)
            soItemDetailArray.add(soItemObj)
        }
        mainObj.put(SO_ITEM_DETAIL, soItemDetailArray)

        return mainObj.toJSONString()

    }

    String prepareEquipmentStatusFeedEmodalMsg(Unit unit, Event event) {
        LOGGER.debug("Inside prepareEquipmentStatusFeedEmodalMsg")
        Equipment equip = unit.getUnitEquipment()
        UnitCategoryEnum unitCategory = unit.getUnitCategory()
        UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()

        JSONBuilder mainObj = JSONBuilder.createObject();
        JSONBuilder msgHeaderObj = JSONBuilder.createObject();
        JSONBuilder msgDataArray = JSONBuilder.createArray()
        mainObj.put(MSGHEADER, msgHeaderObj)
        mainObj.put(MSGDATA, msgDataArray)
        msgHeaderObj.put(SOURCE_CD, ContextHelper.getThreadOperator()?.getOprId() ?: OPERATOR_ID)
        msgHeaderObj.put(SENDER_CD, ContextHelper.getThreadOperator()?.getOprId() ?: OPERATOR_ID)
        msgHeaderObj.put(RECEIVER_CD, RECEIVER_CD_VAL)

        int min = 1111111;
        int max = 9999999;
        int random_int = (int) Math.floor(Math.random() * (max - min + 1) + min)
        msgHeaderObj.put(GSN_NBR, String.valueOf(random_int))
        msgHeaderObj.put(CONTROL_NBR, String.format("%09d", random_int)) //optional gsn_nbr & control_nbr
        msgHeaderObj.put(INTERCHANGE_DTTM, ISO_DATE_FORMAT.format(now))
        msgHeaderObj.put(TRANS_CNT, "1")
        msgHeaderObj.put(RCVDTRANS_CNT, "1")

        JSONBuilder transactionsObj = JSONBuilder.createObject();
        transactionsObj.put(TRANS_DTTM, ISO_DATE_FORMAT.format(now))
        String triggerEvt
        if (event != null) {
            if (EventEnum.UNIT_DISCH.getKey().equals(event.getEventTypeId())) {
                triggerEvt = ARRIVAL
            } else if (EventEnum.UNIT_ENABLE_ROAD.getKey().equals(event.getEventTypeId()) || EventEnum.UNIT_ENABLE_RAIL.getKey().equals(event.getEventTypeId())) {
                triggerEvt = DEPARTURE
            }
        }

        transactionsObj.put(TRIGGERINGEVENT_CAT, triggerEvt ?: EMPTY_STR)

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
                transactionsObj.put(ARRIVALINFO, arrivalinfoObj)
                transactionsObj.put(DEPARTUREINFO, departureinfoObj)
            } else if (truckTransaction.getTranSubType().getKey().startsWith("R") && !TranStatusEnum.CANCEL.equals(truckTransaction.getTranStatus())) {
                arrivalinfoObj.put(DIRECTION_CD, "ARRIVAL")
                arrivalinfoObj = getDepartureOrArrivalObj(arrivalinfoObj, truckTransaction)
                transactionsObj.put(ARRIVALINFO, arrivalinfoObj)
                transactionsObj.put(DEPARTUREINFO, departureinfoObj)
            } else {
                LOGGER.warn("else arrival,departure")
                transactionsObj.put(ARRIVALINFO, new JSONObject())
                transactionsObj.put(DEPARTUREINFO, new JSONObject())
            }

        } else {
            LOGGER.warn("null tran else arrival,departure")
            transactionsObj.put(ARRIVALINFO, new JSONObject())
            transactionsObj.put(DEPARTUREINFO, new JSONObject())
        }

        String VoyageNbr = null
        VesselVisitDetails visitDetails = null

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
        BillOfLading billOfLading
        if (unit.getUnitGoods()?.getGdsBlNbr() != null && unit.getUnitLineOperator() != null) {
            billOfLading = BillOfLading.findBillOfLading(unit.getUnitGoods()?.getGdsBlNbr(), unit.getUnitLineOperator(), null)
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
        //routeinfoObj.put(VSLSTOWAGE_LOC, EMPTY_STR)
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
        //cargoinfoObj.put(BCOCOMPANY_NM, EMPTY_STR)
        if (equip?.getEquipmentOperator()?.getBzuScac() != null) {
            cargoinfoObj.put(USERLINE_SCAC, equip.getEquipmentOperator().getBzuScac())
        }
        transactionsObj.put(CARGOINFO, cargoinfoObj)
        JSONBuilder currentconditioninfoObj = JSONBuilder.createObject();
        boolean isTMFHold = false
        boolean isCTFHold = false
        boolean isTMFHoldOnly = false
        boolean isActiveHold = false

        Collection flagsOnEntity = servicesManager.getImpedimentsForEntity(unit);
        int activeFlagCount = 0
        // Collection flagsOnEntity = FlagType.findActiveFlagsOnEntity(unit);
        if (flagsOnEntity != null && flagsOnEntity.size() > 0) {

            for (IImpediment flag : (flagsOnEntity as List<IImpediment>)) {
                if (flag != null && flag.getFlagType() != null && flag.isImpedimentActive()) {
                    isActiveHold = true
                    activeFlagCount = activeFlagCount + 1
                    String flagId = flag.getFlagType().getId();
                    /* for (Flag flag : flagsOnEntity) {
                         if (flag.getFlagFlagType() != null) {
                             String flagId = flag.getFlagFlagType().getFlgtypId();*/
                    if (flagId.contains("TMF")) {
                        isTMFHold = true

                    }
                    if (flagId.contains("CTF")) {
                        isCTFHold = true

                    }
                }
            }
            if (activeFlagCount == 1 && isTMFHold) {
                isTMFHoldOnly = true
            }
            /* if (flagsOnEntity.size() == 1 && isTMFHold) {
                 isTMFHoldOnly = true
             }*/
        }

        JSONBuilder feeInfoArray = JSONBuilder.createArray()
        UnitStorageManagerPea storageManager = (UnitStorageManagerPea) Roastery.getBean(UnitStorageManager.BEAN_ID);
        LOGGER.debug("ediInvoice fro b4 getInvoice")
        EdiInvoice ediInvoice
        try {
            ediInvoice = storageManager.getInvoiceForUnit(ufv, now, invoiceClass, (String) null, ufv?.getUfvUnit().getUnitLineOperator(), (ScopedBizUnit) null, (String) null, now, "INQUIRE");
        } catch (BizViolation | BizFailure bv) {
            LOGGER.debug("BizViolation" + bv)
        }

        Double demmurrageCharge = 0.0
        Double examAmount = 0.0
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
                        }
                    }
            }
            JSONBuilder feeinfoObj = JSONBuilder.createObject();
            feeinfoObj.put(FEETYPE_CD, "4I")
            feeinfoObj.put(FEETYPE_DESC, "DEMURRAGE")
            if (demmurrageCharge > 0) {
                feeinfoObj.put(FEE_AMT, demmurrageCharge.toString())
                feeinfoObj.put(FEEUNTIL_DTTM, ISO_DATE_FORMAT.format(now))
                feeinfoObj.put(FEE_DTTM, ISO_DATE_FORMAT.format(now))

            } else {
                feeinfoObj.put(FEE_AMT, demmurrageCharge.toString())
                feeinfoObj.put(FEEUNTIL_DTTM, ufv.getUfvLineLastFreeDay() != null ? ISO_DATE_FORMAT.format(ufv.getUfvLineLastFreeDay()) : ISO_DATE_FORMAT.format(ufv.getUfvCalculatedLastFreeDayDate()))
                feeinfoObj.put(FEE_DTTM, ufv.getUfvLineLastFreeDay() != null ? ISO_DATE_FORMAT.format(ufv.getUfvLineLastFreeDay()) : ISO_DATE_FORMAT.format(ufv.getUfvCalculatedLastFreeDayDate()))
            }
            if (examAmount > 0) {
                JSONBuilder examfeeinfoObj = JSONBuilder.createObject();
                examfeeinfoObj.put(FEETYPE_CD, "4IE")
                examfeeinfoObj.put(FEETYPE_DESC, "CONTAINER EXAM FEE")
                examfeeinfoObj.put(FEE_AMT, examAmount.toString())
                examfeeinfoObj.put(FEEUNTIL_DTTM, ISO_DATE_FORMAT.format(now))
                examfeeinfoObj.put(FEE_DTTM, ISO_DATE_FORMAT.format(now))
                feeInfoArray.add(examfeeinfoObj)
            }
            feeInfoArray.add(feeinfoObj)

        }
        transactionsObj.put(FEEINFO, feeInfoArray)

        List<LinkedHashMap<String, String>> shipmentStatusList = (List<LinkedHashMap<String, String>>) getMapShipmentStatus(flagsOnEntity, unit, isActiveHold, isTMFHoldOnly, billOfLading)
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


        transactionsObj.put(SHIPMENTSTATUSINFO, shipmentstatusinfoArray)


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
        currentconditioninfoObj.put(CTF_FLG, isCTFHold ? YES : NO)
        String blNbr;

        if (unit.getUnitGoods()?.getGdsBlNbr() != null) {
            if (unit.getUnitGoods().getGdsBlNbr().contains("+")) {
                blNbr = unit.getUnitGoods().getGdsBlNbr().replace("+", "")
            } else {
                blNbr = unit.getUnitGoods().getGdsBlNbr()
            }
            if (blNbr.length() > 5) {
                blNbr = blNbr.substring(blNbr.length() - 5)
            }

        }
        currentconditioninfoObj.put(GATE_CD, blNbr ?: "")
        unit.getUnitGoods().getGdsBlNbr()
        transactionsObj.put(CURRENTCONDITIONINFO, currentconditioninfoObj)

        return mainObj.toJSONString()

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

    public List getMapShipmentStatus(Collection flagsOnUnitEntity, Unit unit, boolean isActiveHold, boolean isTMFHoldOnly, BillOfLading billOfLading) {
        EventManager eventManager = (EventManager) Roastery.getBean(EventManager.BEAN_ID)
        List list = new ArrayList()
        boolean isFreightHold = false
        boolean isISFHold = false
        boolean isCustomsHold = false
        boolean isExamHold = false
        boolean isAGRIHold = false
        boolean isPAHold = false
        // boolean isServiceHold = false
        boolean isHeldForTC = false
        Date paHoldDate
        Date frieghtHoldDate
        Date isfHoldDate
        Date agriHoldDate
        if ("UTL".equals(unit.findCurrentPosition()?.getBlockName())) {
            isHeldForTC = true
        }
        //Collection flagsOnUnitEntity = servicesManager.getImpedimentsForEntity(unit);
        if (flagsOnUnitEntity != null && flagsOnUnitEntity.size() > 0) {

            for (IImpediment flag : (flagsOnUnitEntity as List<IImpediment>)) {
                if (flag != null && flag.getFlagType() != null && flag.isImpedimentActive()) {
                    String flagId = flag.getFlagType().getId();
                    if (flagId.startsWith("SERVICE")) {
                        isHeldForTC = true
                    }
                    if (flagId.startsWith("CUSTOMS")) {
                        isCustomsHold = true
                    }
                    if (flagId.equalsIgnoreCase("NII")) {
                        isPAHold = true
                        isHeldForTC = true
                        paHoldDate = flag.getApplyDate()
                    }
                    if (flagId.equalsIgnoreCase("EXAM_PAYMENT_HOLD") || flagId.startsWith("TMF") || flagId.equalsIgnoreCase("NO GO HOLD")) {
                        isHeldForTC = true
                    }

                    if (flagId.equalsIgnoreCase("FK") || flagId.equalsIgnoreCase("FL") || flagId.equalsIgnoreCase("F0") || flagId.equalsIgnoreCase("F1") || flagId.equalsIgnoreCase("F4") || flagId.equalsIgnoreCase("F5") || flagId.equalsIgnoreCase("FC") || flagId.equalsIgnoreCase("FD")) {
                        isAGRIHold = true
                        isCustomsHold = true
                        agriHoldDate = flag.getApplyDate()
                    }
                    if (flagId.startsWith("FREIGHT_HOLD")) {
                        isFreightHold = true
                        frieghtHoldDate = flag.getApplyDate()
                    }

                    if (flagId.equalsIgnoreCase("2O") || flagId.equalsIgnoreCase("2P") || flagId.equalsIgnoreCase("2Q") || flagId.equalsIgnoreCase("2R")) {
                        isISFHold = true
                        isCustomsHold = true
                        isfHoldDate = flag.getApplyDate()
                    }
                    if (flagId.equalsIgnoreCase("1A") || flagId.equalsIgnoreCase("1U") || flagId.equalsIgnoreCase("7H")) {
                        isHeldForTC = true
                        isCustomsHold = true
                    }


                }
            }
        }

        if (isHeldForTC) {
            list.add(setStatus("TC", "HELD FOR TERMINAL CHARGES", SHIPMENT, ISO_DATE_FORMAT.format(now)))
        }
        if (!isCustomsHold) {
            list.add(setStatus("CT", "CUSTOMS RELEASED", SHIPMENT, ISO_DATE_FORMAT.format(now)))
            // TO DO ::::::::::include freight hold release date time
        }
        if (isFreightHold) {
            list.add(setStatus("FD", "FREIGHT IS DUE", SHIPMENT, ISO_DATE_FORMAT.format(now)))
        } else {
            list.add(setStatus("CR", "CARRIER RELEASE", SHIPMENT, ISO_DATE_FORMAT.format(now)))
            // TO DO ::::::::::include freight hold release date time
        }

        if (isISFHold) {
            list.add(setStatus("PB", "Customs Hold, Insufficient Paperwork", SHIPMENT, isfHoldDate ? ISO_DATE_FORMAT.format(isfHoldDate) : ISO_DATE_FORMAT.format(now)))
        }

        if (isAGRIHold) {
            list.add(setStatus("PL", "Dept. Agr, Hold for Intensive Investigation", SHIPMENT, agriHoldDate ? ISO_DATE_FORMAT.format(agriHoldDate) : ISO_DATE_FORMAT.format(now)))
        }
        if (isPAHold) {
            list.add(setStatus("PA", "Customs Hold, Intensive Examination", SHIPMENT, paHoldDate ? ISO_DATE_FORMAT.format(paHoldDate) : ISO_DATE_FORMAT.format(now)))
        }
        UnitFacilityVisit ufv = unit.getUfvForFacilityLiveOnly(ContextHelper.getThreadFacility())
        boolean isCtrAV = false
        if (ufv != null && ufv.isTransitStateBeyond(UfvTransitStateEnum.S20_INBOUND)) {
            // list.add(setStatus("A", "Arrived", SHIPMENT, ISO_DATE_FORMAT.format(ufv.getUfvTimeIn())))
            list.add(setStatus("A", "Arrived", SHIPMENT, ufv.getUfvTimeIn() != null ? ufv.getUfvTimeIn().format("yyyy-MM-dd'T'HH:mm:ss") : ""))
            if (unit.isUnitInYard() && (UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()) /*|| UnitCategoryEnum.STORAGE.equals(unit.getUnitCategory())*/) && (!isActiveHold || isTMFHoldOnly) && !isHeldForTC) {
                isCtrAV = true
                list.add(setStatus("AV", "Available For Delivery", SHIPMENT, ISO_DATE_FORMAT.format(now)))
            } /*else if ((ufv.isTransitStatePriorTo(UfvTransitStateEnum.S40_YARD) || ufv.isTransitStateBeyond(UfvTransitStateEnum.S40_YARD)) || (UnitCategoryEnum.TRANSSHIP.equals(unit.getUnitCategory()) || UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory()))) {
                list.add(setStatus("DN", "Container Not Available for pickup", SHIPMENT, ISO_DATE_FORMAT.format(now)))
            }*/
        }
        if (!isCtrAV || (UnitCategoryEnum.TRANSSHIP.equals(unit.getUnitCategory()) || UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory()))) {
            list.add(setStatus("DN", "Container Not Available for pickup", SHIPMENT, ISO_DATE_FORMAT.format(now)))
        }
        UnitFacilityVisit unitFacilityVisit = unit.getUnitActiveUfvNowActive()
        if (unitFacilityVisit != null) {
            if (unitFacilityVisit.getUfvLineLastFreeDay() != null) {
                list.add(setStatus("NF", "FREE TIME TO EXPIRE", SHIPMENT, ISO_DATE_FORMAT.format(unitFacilityVisit.getUfvLineLastFreeDay())))
            }

            UnitStorageManager storageManager = (UnitStorageManager) Roastery.getBean(UnitStorageManager.BEAN_ID)
            try {
                if (storageManager.isStorageOwed(ufv, ChargeableUnitEventTypeEnum.STORAGE.getKey())) {
                    list.add(setStatus("FT", "FREE TIME EXPIRED", SHIPMENT, ISO_DATE_FORMAT.format(now)))
                }
            } catch (Exception e) {
                LOGGER.debug("Error occured on calculation of Storage Owed.... " + e)
            }
        }


        if (unit != null && unit.getUnitRouting() != null && unit.getUnitRouting().getRtgDeclaredCv() != null) {
            if (LocTypeEnum.TRAIN.equals(unit.getUnitRouting().getRtgDeclaredCv().getCvCarrierMode())) {
                list.add(setStatus("RO", "Rail Order", SHIPMENT, ISO_DATE_FORMAT.format(now)))
            }
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
                srvEventList.add(setStatus("AE", "Loaded On Vessel", SRVC_EVENT, event.getEvntAppliedDate() ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : EMPTY_STR))
            }
        }
        return srvEventList

    }

    private
    static IntegrationServiceMessage logRequestToInterfaceMessage(HibernatingEntity hibernatingEntity, LogicalEntityEnum inLogicalEntityEnum,
                                                                  IntegrationService inIntegrationService, String inMessagePayload, Long seqNbr) {
        IntegrationServiceMessage integrationServiceMessage = new IntegrationServiceMessage();
        integrationServiceMessage.setIsmEventPrimaryKey((Long) hibernatingEntity.getPrimaryKey());
        integrationServiceMessage.setIsmEntityClass(inLogicalEntityEnum);
        integrationServiceMessage.setIsmEntityNaturalKey(hibernatingEntity.getHumanReadableKey());
        try {
            if (inIntegrationService) {
                integrationServiceMessage.setIsmIntegrationService(inIntegrationService);
                integrationServiceMessage.setIsmFirstSendTime(ArgoUtils.timeNow());
                //integrationServiceMessage.setIsmLastSendTime(ArgoUtils.timeNow());
            }
            integrationServiceMessage.setIsmMessagePayload(inMessagePayload);
           if(seqNbr == null) {
                integrationServiceMessage.setIsmSeqNbr(new IntegrationServMessageSequenceProvider().getNextSequenceId());
            }else{
               integrationServiceMessage.setIsmSeqNbr(seqNbr)
           }
            ScopeCoordinates scopeCoordinates = ContextHelper.getThreadUserContext().getScopeCoordinate();
            integrationServiceMessage.setIsmScopeGkey((String) scopeCoordinates.getScopeLevelCoord(scopeCoordinates.getDepth()));
            integrationServiceMessage.setIsmScopeLevel(scopeCoordinates.getDepth());
            integrationServiceMessage.setIsmUserString3("false")
            HibernateApi.getInstance().save(integrationServiceMessage);
            HibernateApi.getInstance().flush();

        } catch (Exception e) {
        }

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

        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq)

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
    private static final String UNITTYPEISO_CD = "unittypeISO_cd"
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
    private static final String CTF_FLG = "ctf_flg"
    private static final String GATE_CD = "gate_cd"

    private static final String CURRENTCONDITIONINFO = "currentconditioninfo"
    private static final String SHIPMENTSTATUSINFO = "ShipmentStatusinfo"
    private static final String CARGOINFO = "cargoinfo"
    private static final String ROUTEINFO = "routeinfo"
    private static final String DEPARTUREINFO = "departureinfo"
    private static final String ARRIVALINFO = "arrivalinfo"
    private static final String UNITSTATUSINFO = "unitStatusinfo"
    private static final String SVCEVENTINFO = "SvcEventinfo"
    private static final String UNITINFO = "unitinfo"
    private static final String MSGDATA = "msgdata"
    private static final String MSGHEADER = "msgheader"
    private static final String EMPTY_STR = ""
    private static DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static final String _statusCode = "shipmentstatus_cd"
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
    private static final String OPERATOR_ID = "ITS"
    private static final String RECEIVER_CD_VAL = "ECP"
    private static final String INT_SERV_NAME_BKG = "EMODAL_315"
    private static final String INT_SERV_NAME_CTR = "EMODAL_315"
    private static final String TRIGGERINGEVENT_CAT = "triggeringevent_cat"
    private static final String YARD = "YARD"
    private static final String ARRIVAL = "ARRIVAL"
    private static final String DEPARTURE = "DEPARTURE"
    private static final String FEEUPD = "FEEUPD"
    private static final String ANNOUNCE = "ANNOUNCE"


    private static final String ACTION = "action"
    private static final String BKG_TERMINAL_CD = "terminalCd"
    private static final String SO_NBR = "soNbr"
    private static final String LINE_SCAC = "lineScac"
    private static final String LLOYDS_CODE = "lloydsCode"
    private static final String RAIL_EXEMPT = "railExempt"
    private static final String OUT_VOY_NBR = "outVoyNbr"
    private static final String TMF_CLAIMED = "tmfClaimed"
    private static final String SUB_TYPE = "subtype"
    private static final String RESERVED_EQ = "reservedEq"
    private static final String SO_GKEY = "soGkey"
    private static final String SO_OLD_NBR = "soOldNbr"
    private static final String POSTED = "posted"
    private static final String PORT = "port"
    private static final String SO_HAZ = "soHaz"
    private static final String SO_ITEM_DETAIL = "soItemDetail"
    private static final String SO_ITEM_HAZ = "soItemHaz"
    private static final String SO_ITEM_LOCKS = "soItemLocks"
    private static final String FUNCTION_CD = "functionCd"
    private static final String PORT_NM = "portNm"
    private static final String UN_NBR = "unNbr"
    private static final String HAZ_CLASS = "hazClass"
    private static final String COMMODITY_WEIGHT = "commodityWeight"
    private static final String QUANTITY = "quantity"
    private static final String PKG_GROUP = "pkgGroup"
    private static final String EMERGENCY_PHONE = "emergencyPhone"
    private static final String COMMODITY_NAME = "commodityName"
    private static final String LIMITED_QUANTITY = "limitedQuantity"
    private static final String COMMENTS = "comments"
    private static final String SZ_TP_ID = "szTpId"
    private static final String QTY = "qty"
    private static final String PICKUP_TALLY = "pickupTally"
    private static final String RECEIVED = "received"
    private static final String IS_OD = "isOD"
    private static final String IS_REEFER = "isReefer"
    private static final String SO_ITEM_SEQ_NBR = "soItemSeqNbr"
    private static final String OVERRIDE_GATE_LOCK = "overrideGateLock"
    private static final String SO_ITEM_UN_NBR = "soItemUnNbr"
    private static final String SO_ITEM_HAZ_CLASS = "soItemHazClass"
    private static final String SO_ITEM_COMM_WEIGHT = "soItemCommWeight"
    private static final String SO_ITEM_QUANTITY = "soItemQuantity"
    private static final String SO_ITEM_PKG_GROUP = "soItemPkgGroup"
    private static final String SO_ITEM_EMERGENCY_PH = "soItemEmergencyPh"
    private static final String SO_ITEM_COMM_NAME = "soItemCommName"
    private static final String SO_ITEM_LIMITED_QUAN = "soItemLimitedQuan"
    private static final String SO_ITEM_COMMENTS = "soItemComments"
    private static final String RECORD_TYPE = "recordType"
    private static final String LOCK_TYPE = "lockType"
    private static final String LOCK_UNTIL = "lockUntil"
    private static final String LOCK_AFTER = "lockAfter"
    private static final String SLIDE_LOCK_UNTIL_VALUE = "slideLockUntilValue"
    private static final String SLIDE_LOCK_UNTIL_UNITS = "slideLockUntilUnits"
    private static final String SLIDE_LOCK_UNTIL_DATE = "slideLockUntilDate"
    private static final String SLIDE_LOCK_AFTER_VALUE = "slideLockAfterValue"
    private static final String SLIDE_LOCK_AFTER_UNITS = "slideLockAfterUnits"
    private static final String SLIDE_LOCK_AFTER_DATE = "slideLockAfterDate"
    private static final String FEETYPE_CD = "feetype_cd"
    private static final String FEETYPE_DESC = "feetype_desc"
    private static final String FEE_AMT = "fee_amt"
    private static final String FEEUNTIL_DTTM = "feeuntil_dttm"
    private static final String FEE_DTTM = "fee_dttm"
    private static final String FEEINFO = "feeinfo"
    private static final String MESSAGE_ID = "messageId"
    private static final String CONTAINER_NUMBER = "containerNumber"
    private static final String UNIT_ID = "unitId"
    private static final String EVENT_CD = "eventCd"
    private static final String EVENT_DT_TM = "eventDtTm"
    private static final String SHIFT_NUM = "shiftNum"
    private static final String CREATE_DT_TM = "createDtTm"

    private static final String SCHEDULED_FOR_DISCHARGE = "AA"
    private static final String VESSEL_DISCHARGE = "UV"
    private static final String FIRST_DELIVERABLE = "FD"
    private static final String FIRST_AVAILABLE_FOR_TRUCK = "FA"
    private static final String FIRST_AVAILABLE_FOR_RAIL = "RA"
    private static final String OUT_GATED = "OA"
    private static final String RAIL_LOAD = "AL"
    private static final String UNIT_FD_MOVE = "UNIT_DELIVERABLE_DISCHARGE"



    private static final String EQUIPMENT_TYPE = "equipmentType"
    private static ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
    private static final String ITS = "ITS"
    private static final String BKG_ISO_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
    Date now = ArgoUtils.timeNow()
    private String invoiceClass = "IMPORT_PRE_PAY";
    MessageTranslator messageTranslator = TranslationUtils.getTranslationContext(ContextHelper.getThreadUserContext()).getMessageTranslator()
    private static final Logger LOGGER = Logger.getLogger(this.class)


}