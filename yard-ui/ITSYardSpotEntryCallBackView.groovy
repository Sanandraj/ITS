/*
* Copyright (c) 2022 WeServe LLC. All Rights Reserved.
*
*/

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.LineOperator
import com.navis.argo.business.reference.RoutingPoint
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.Routing
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Logger
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class ITSYardSpotEntryCallBackView extends AbstractExtensionPersistenceCallback {


    static StringBuilder sb = new StringBuilder()

    @Override
    void execute(@Nullable Map inMap, @Nullable Map outMap) {
        long start = System.currentTimeMillis();
        LOG.info(" start.............................  " + start)

        String error = null
        String  unitNumber  = inMap.get("unitNumber")
        StringBuilder  json = new StringBuilder()

        LinkedHashMap<String, Object> map  = getResultMap(unitNumber)
        if(map.size() > 0 ) {
            error = map.get("Error")
            if(error == null) {

                Map<String, Object> mapContainerRoute = new HashMap<String, Object>()
                Map<String, Object> mapEventInput = new HashMap<String, Object>()

                mapContainerRoute.put("CargoVslCd", map.get("CargoVslCd"))
                mapContainerRoute.put("CargoVoyNum", map.get("CargoVoyNum"))
                mapContainerRoute.put("CargoCallSeq", map.get("CargoCallSeq"))

                mapEventInput.put("EquipPrefixCd", map.get("EquipPrefixCd"))
                mapEventInput.put("EquipNum", map.get("EquipNum"))
                mapEventInput.put("EquipChkDigit", map.get("EquipChkDigit"))
                mapEventInput.put("EventName", "ContainerSpotEntry")
                mapEventInput.put("ChassisNum",  map.get("ChassisNum"))
                mapEventInput.put("ChassisPrefixCd", map.get("ChassisPrefixCd"))
                mapEventInput.put("ChassisChkDigit",null)

                map.remove("CargoVslCd")
                map.remove("CargoVoyNum")
                map.remove("CargoCallSeq")

                ObjectMapper objectMapper = new ObjectMapper()
                json.append("  {  \"LT_EventDetails\"  : [ ")
                try {
                    json.append(objectMapper.writeValueAsString(map))
                    json.append("], \n")
                } catch (JsonProcessingException e) {
                    e.printStackTrace()
                }

                ObjectMapper objectMapper2 = new ObjectMapper()
                json.append("  \"LT_ContainerRoute\"  :[")
                try {
                    json.append(objectMapper2.writeValueAsString(mapContainerRoute))
                    json.append("], \n")
                } catch (JsonProcessingException e) {
                    e.printStackTrace()
                }
                ObjectMapper objectMapper3 = new ObjectMapper()
                json.append("  \"LT_EventInput\"  :[")
                try {
                    json.append(objectMapper3.writeValueAsString(mapEventInput))
                    json.append("] \n")
                    json.append("} \n")
                } catch (JsonProcessingException e) {
                    e.printStackTrace()
                }
            }else {
                json.append(" {'Error': [{'ErrNbr': 61085 } ] }")
            }

        }
        LOG.info(" json............................. " + json)
        outMap.put("responseMessage", json.toString())

        long elapsedTime = System.currentTimeMillis() - start;
        LOG.info(" - Time taken in milli Seconds to process the request: " + elapsedTime)
    }

    @Nullable
    private LinkedHashMap<String, Object>  getResultMap(@NotNull String inUnitId) {
        LOG.info("unit id : "+ inUnitId)
        LinkedHashMap<String, Object> map  = new LinkedHashMap<String, Object>()

        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                .addDqPredicate(PredicateFactory.eq(InventoryField.UNIT_ID, inUnitId))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_VISIT_STATE, UnitVisitStateEnum.ACTIVE))

        LOG.info(" dq : " + dq.toString())
        List<Unit> unitList  = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        LOG.info(" unitList  size : " + unitList .size())

        if(unitList.size() > 0 ) {
            for (Unit unit : (unitList as List<Unit>)) {
                  UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
                if (ufv != null && ufv.getUfvTransitState().equals(UfvTransitStateEnum.S40_YARD)) {
                    LOG.info(" UfvTransitStateEnum : " + ufv.getUfvTransitState().getName() )
                    Equipment equipment = unit.getUnitEquipment()
                    map.put("EquipVisitId", ufv.getUfvGkey())
                    map.put("EquipPrefixCd", equipment.getEqIdPrefix())
                    map.put("EquipNum", equipment.getEqIdNbrOnly())
                    map.put("EquipChkDigit", equipment.getEqIdCheckDigit())
                    map.put("EquipSzCd", equipment.getEqEquipType().getEqtypNominalLength().getName().replace("NOM", ""))
                    map.put("EquipTypeCd", unit.isReefer() ? "RF" : "DR")
                    map.put("EquipHgtCd", equipment.getEqEquipType().getEqtypNominalHeight().getName().replace("NOM", ""))
                    map.put("EquipGrpCd", equipment.getEqEquipType().getEqtypIsoGroup().getName())
                    map.put("EquipStatusCd", unit.getUnitCategory().getName().substring(0, 1))

                    double wtKg = (unit.getUnitGoodsAndCtrWtKg() / 1000)
                    map.put("GrossWgtKt", wtKg)

                    LineOperator shippingLine = unit.getUnitLineOperator() != null ? LineOperator.resolveLineOprFromScopedBizUnit(unit.getUnitLineOperator()) : null;
                    map.put("ShippingLineCd", shippingLine.getBzuId())
                    map.put("FullEmptyCd", unit.getUnitFreightKind().getName().substring(0, 1))
                    map.put("ShortStatusCd", unit.getUnitCategory().getName().substring(0, 3))

                    UnitEquipment chassisEquip = unit.getUnitCarriageUnit();

                    if (chassisEquip != null) {
                        String chassis = chassisEquip.getUnitId()
                        String chassisPrefixCd = chassis.replaceAll("[^A-Z a-z]", "")
                        String chassisNum = chassis.replaceAll("[^0-9]", "")
                        map.put("ChassisPrefixCd", chassisPrefixCd)
                        map.put("ChassisNum", chassisNum)
                        map.put("ChassisChkDigit", null)

                    } else {
                        map.put("ChassisPrefixCd", null)
                        map.put("ChassisNum", null)
                        map.put("ChassisChkDigit", null)
                    }

                    Routing routing = unit.getUnitRouting()
                    String portOfDischarge = null
                    if (routing != null) {
                        RoutingPoint routingPoint = routing.getRtgPOD1()
                        if (routingPoint != null) {
                            portOfDischarge = routingPoint.getPointId()
                        }
                    }
                    map.put("PODCd", portOfDischarge)

                    String CurrentCarrierType = ""
                    String CurrentCarrierCode = ""
                    String CurrentCarrierPosition = ""
                    String VisitStatusCd = ""
                    long CargoCallSeq = 1
                    String CargoVoyNum = "0"
                    String CvId = ""
                    String plannedCarrierType=null
                    String plannedCarrierCode=null
                    String plannedCarrierPosition=null

                    if ("IMPRT".equals(unit.getUnitCategory().getName())) {
                        CarrierVisit ibcv = ufv.getUfvActualIbCv()
                        VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(ibcv);
                        if (vvd != null) {
                            CargoVoyNum = vvd.getVvdIbVygNbr().replaceAll("[^0-9]", "")
                            CurrentCarrierType = ibcv.getCvCarrierMode().getName()
                            CurrentCarrierCode = vvd.getVesselId() != null ? vvd.getVesselId() : ""
                            CvId = ibcv.getCvId() != null ? ibcv.getCvId() : ""

                        }
                        VisitStatusCd = "P"
                        LocPosition currentPos = ufv.getUfvArrivePosition()
                    } else if ("EXPRT".equals(unit.getUnitCategory().getName())) {
                        CarrierVisit obcv = ufv.getUfvActualObCv();
                        VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(obcv);
                        if (vvd != null) {
                            CargoVoyNum = vvd.getVvdObVygNbr().replaceAll("[^0-9]", "")
                            CurrentCarrierType = obcv.getCvCarrierMode().getName()
                            CurrentCarrierCode = vvd.getVesselId() != null ? vvd.getVesselId() : ""
                            CvId = obcv.getCvId() != null ? obcv.getCvId() : ""
                        }
                        VisitStatusCd = "O"
                    } else if ("STRGE".equals(unit.getUnitCategory().getName())) {
                        VisitStatusCd = "P"
                        LocPosition currentPos = ufv.getUfvArrivePosition()
                        CarrierVisit ibcv = ufv.getUfvActualIbCv()
                        CurrentCarrierType = ibcv != null ? ibcv.getCvCarrierMode().getName() : ""
                        if (currentPos != null) {
                            CurrentCarrierCode = unit.getUnitDeclaredIbCv()
                        }
                    }
                    LocPosition lastKnownPostion = ufv.getUfvLastKnownPosition()
                    if(lastKnownPostion != null ) {
                        CurrentCarrierPosition = lastKnownPostion.getPosName().replace("Y-PIERG-","")
                    }

                    LocPosition locplannedPostion = ufv.getFinalPlannedPosition()
                    if(locplannedPostion != null ) {
                        String plannedPositon = locplannedPostion.getPosName()
                        if(plannedPositon != null) {
                            String[] spltPosition = plannedPositon.split("-")
                            if(spltPosition.size() > 2) {
                                plannedCarrierType = spltPosition[0]
                                plannedCarrierCode = spltPosition[1]
                                plannedCarrierPosition = spltPosition[2]
                            }
                        }
                    }
                    map.put("CurrentCarrierPosition", CurrentCarrierPosition)
                    map.put("CurrentCarrierType",   "Y"  )// CurrentCarrierType.length() >= 1 ? CurrentCarrierType.substring(0, 1) : "")
                    map.put("CurrentCarrierCode", "MAIN")
                    map.put("VisitStatusCd", VisitStatusCd)
                    map.put("ErrFlg", "N")
                    map.put("EventActionCd", "Spot")
                    map.put("UserLineId", null)
                    map.put("CargoVslCd", CurrentCarrierCode)
                    map.put("CargoVoyNum", CargoVoyNum)
                    map.put("CargoCallSeq", CargoCallSeq)

                    map.put("PlannedCarrierType", plannedCarrierType)
                    map.put("PlannedCarrierCode", plannedCarrierCode)
                    map.put("PlannedCarrierPosition", plannedCarrierPosition)

                    LOG.info("------------------------ done ----------------------------------------")
                }else {
                    map.put("Error", "Container is not found ")
                }
            }
        }else {
            map.put("Error", "Container is not found ")
        }
        return map
    }

    private static final Logger LOG = Logger.getLogger(ITSYardSpotEntryCallBackView.class)

}