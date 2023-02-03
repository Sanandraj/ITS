/*
* Copyright (c) 2022 WeServe LLC. All Rights Reserved.
*
*/

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.IArgoYardUtils
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Yard
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.RoutingPoint
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.Disjunction
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.ObsoletableFilterFactory
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.*
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.spatial.BinField
import com.navis.spatial.BlockField
import com.navis.spatial.business.model.block.AbstractSection
import com.navis.spatial.business.model.block.AbstractStack
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.yard.business.model.AbstractYardBlock
import org.apache.log4j.Logger
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

/*
* @Author <a href="mailto:rkarthikeyan@weservetech.com">Karthikeyan R</a>,
*
* @Inclusion Location	: Incorporated as a code extension of the type .Copy --> Paste this code (ITSYardBlockCallback.groovy)
*
*/

class ITSYardBlockCallback  extends AbstractExtensionPersistenceCallback {

    @Override
    void execute(@Nullable Map inMap, @Nullable Map outMap) {

        String blockGkey = inMap.get("blockNum")
        String bayNum = inMap.get("bayNum")
        StringBuilder  json = new StringBuilder()
        long start = System.currentTimeMillis()

        LOG.info( " blockGkey............................. "  + blockGkey )
        LOG.info( " bayNum ............................... "  + bayNum )

        List<LinkedHashMap<String, Object>> mapList = getQueryResultUFVMap(blockGkey,bayNum)
        if(mapList.size() > 0 ) {
            ObjectMapper objectMapper = new ObjectMapper()
            json.append("  {  'LT_ContainerDetail'  : \n")
            try {
                json.append(objectMapper.writeValueAsString(mapList))
                json.append(", \n")
            } catch (JsonProcessingException e) {
                e.printStackTrace()
            }
            Map<String, Object> lstmap = mapList.get(0)
            String blockType = lstmap.get("YardBlockTypeCd")
            if ("S".equals(blockType)) {
                json.append("\n")
                json.append(" 'LT_MinRowTier': [{'MinRowNum':1,'MinTierNum':1 } ]  \n")
            } else {
                json.append("\n")
                json.append(" 'LT_MinRowTier': [{'MinRowNum':null,'MinTierNum':null } ]  \n")
            }
        }
        if(bayNum != null && bayNum != "") {
            List<LinkedHashMap<String, Object>> mapListYardSlot = getQueryResultYardSlotMap(blockGkey, bayNum)
            LOG.info(" YardSlot : " + mapListYardSlot.size())
            if (mapListYardSlot.size() > 0) {
                ObjectMapper objectMapperYardSlot = new ObjectMapper()
                json.append(" \n,  'LRef_YardSlot'  : \n")
                try {
                    json.append(objectMapperYardSlot.writeValueAsString(mapListYardSlot))
                    json.append(" } \n")
                } catch (JsonProcessingException e) {
                    e.printStackTrace()
                }
            }else {
                json.append(" } \n")
            }
        } else {
            json.append(" } \n")
        }
        LOG.info(" json............................. " + json)
        outMap.put("responseMessage", json.toString())
        long elapsedTime = System.currentTimeMillis() - start
        LOG.info(" - Time taken in milli Seconds to process the request: " + elapsedTime)
    }

    @Nullable
    static List<Map<String, Object>> getQueryResultUFVMap(String blockGkey,String bayNum) {
        LOG.info( "QueryResultUFVMap blockNum : "  + blockGkey )
        LOG.info( "QueryResultUFVMap   bayNum : "  + bayNum )

        List<LinkedHashMap<String, Object>> resultListMap  = new ArrayList<LinkedHashMap<String, Object>>()
        IArgoYardUtils argoYardUtils = (IArgoYardUtils) Roastery.getBean("argoYardUtils")

        final Yard yard = ContextHelper.getThreadYard()
        long yardAbnGkey = yard.yrdBinModel.getAbnGkey()

        DomainQuery sbl = QueryUtils.createDomainQuery("AbstractYardBlock")
        sbl.addDqPredicate(PredicateFactory.like(BinField.ABN_GKEY, blockGkey))
        sbl.addDqPredicate(PredicateFactory.eq(BinField.ABN_PARENT_BIN, yardAbnGkey))
        sbl.setFilter(ObsoletableFilterFactory.createShowActiveFilter())

        List<AbstractYardBlock> yardBlockList = HibernateApi.getInstance().findEntitiesByDomainQuery(sbl)
        LOG.warn( "yardBlockList : "  + yardBlockList.size() )

        for (AbstractYardBlock abstractYardBlock : (yardBlockList as List<AbstractYardBlock>)) {
            int blocklength = abstractYardBlock.getAbnName().length()
            int baylength = 0
            int rowlength = 0
            int tierlength = 0
            String labelUIFullPosition = abstractYardBlock.getAyblkLabelUIFullPosition()
            if (labelUIFullPosition != null) {
                String[] struiFullPosition = labelUIFullPosition.split("\\.")
                if (struiFullPosition.length > 2) {
                    baylength = Integer.valueOf(struiFullPosition[1].replaceAll("[^0-9]", ""))
                    rowlength = Integer.valueOf(struiFullPosition[2].replaceAll("[^0-9]", ""))
                    tierlength = Integer.valueOf(struiFullPosition[3].replaceAll("[^0-9]", ""))
                } else {
                    baylength = abstractYardBlock.getAyblkLabelUIRowOnly() != null ? Integer.valueOf(abstractYardBlock.getAyblkLabelUIRowOnly().replaceAll("[^0-9]", "")) : 0
                    rowlength = abstractYardBlock.getAyblkLabelUIColOnly() != null ? Integer.valueOf(abstractYardBlock.getAyblkLabelUIColOnly().replaceAll("[^0-9]", "")) : 0
                    tierlength = abstractYardBlock.getAyblkLabelUITierOnly() != null ? Integer.valueOf(abstractYardBlock.getAyblkLabelUITierOnly().replaceAll("[^0-9]", "")) : 0

                }
            }
            long maxRowNum = 0
            long maxTierNum = 0

            String bayFromNum = 0
            String bayToNum = 0

            String posSlot = null
            String posSlotAlt = null
            String blockNum = abstractYardBlock.getAbnName()
            String subType = abstractYardBlock.getAbnSubType()


            String block = "L";
            if("SBL".equals(abstractYardBlock.getAbnSubType())) {
                if (argoYardUtils.isBlockWheeledBlock(abstractYardBlock)) {
                    block = "W"
                } else if (argoYardUtils.isBlockGroundedBlock(abstractYardBlock)) {
                    block = "S"
                }
            }
            String masterAbnName = abstractYardBlock.getAbnName()
            List<String> listBinName = new ArrayList<>(1)

            if ("S".equals(block)  || ("W".equals(block))) {
                List<AbstractSection>  ysnSection = getYSN(abstractYardBlock.getPrimaryKey())

                if (!ysnSection.isEmpty() &&   ysnSection.size() > 0) {

                    List<Serializable> serializableYsn = new ArrayList<Serializable>()
                    for(AbstractSection abstractSection :ysnSection ) {
                        serializableYsn.add(abstractSection.getPrimaryKey())
                    }

                    List<AbstractStack>  abstractStackList = getYstAllAbnName(serializableYsn)
                    for(AbstractStack abs : abstractStackList ) {
                        listBinName.add(abs.getAbnName())
                        listBinName.add(abs.getAbnNameAlt())
                    }
                    if ("S".equals(block)) {

                        Serializable ystGkey = ysnSection.get(0).getPrimaryKey()
                        String sectionNameFirst = ysnSection.get(0).getAbnName()
                        String sectionNameLast = ysnSection.get(ysnSection.size() - 1).getAbnName()
                        bayFromNum = Long.valueOf(sectionNameFirst.replace(masterAbnName, ""))
                        bayToNum = Long.valueOf(sectionNameLast.replace(masterAbnName, ""))
                        List<AbstractStack> yst = getYstIndex(ystGkey)
                        maxRowNum = yst.size()
                        maxTierNum = abstractYardBlock.getMaxTier()

                    } else if ("W".equals(block)) {
                        Serializable ystGkey = ysnSection.get(0).getPrimaryKey()
                        List<AbstractStack> ystSection = getYstAbnName(ystGkey)
                        if (!ystSection.isEmpty() && ystSection.size() > 0) {
                            String stackNameFirst = ystSection.get(0).getAbnName()
                            String stackNameLast = ystSection.get(ystSection.size() - 1).getAbnName()
                            bayFromNum = Long.valueOf(stackNameFirst.replace(masterAbnName, ""))
                            bayToNum = Long.valueOf(stackNameLast.replace(masterAbnName, ""))
                            maxRowNum = 0
                            maxTierNum = 0
                        }
                    }
                }
            }else if ("L".equals(block)) {
                bayFromNum = 0
                bayToNum = 0
                maxTierNum = 0
                maxRowNum = 0
            }
            if (bayNum != null && bayNum != "") {
                bayNum = String.format("%0" + baylength + "d", Integer.valueOf(bayNum))
                posSlot = YardFacility+blockNum + bayNum
                posSlotAlt = YardFacility+blockNum +"." + bayNum
            } else {
                posSlot = YardFacility+blockNum
            }
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
                    dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_VISIT_STATE, UnitVisitStateEnum.ACTIVE))
                    dq.addDqPredicate(PredicateFactory.eq(InventoryField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD))
            if (bayNum != null && bayNum != "") {
                Disjunction slotJunction = (Disjunction) PredicateFactory.disjunction()
                slotJunction.add(PredicateFactory.like(UFV_LAST_KNOWN_POSITION_NAME, posSlotAlt+"%" ))
                slotJunction.add(PredicateFactory.like(UFV_LAST_KNOWN_POSITION_NAME, posSlot+"%"))
                dq.addDqPredicate(slotJunction)
            }else {
                dq.addDqPredicate(PredicateFactory.like(UFV_LAST_KNOWN_POSITION_NAME, posSlot+"%"))
            }
            List<UnitFacilityVisit> ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
            LOG.info( "UnitFacilityVisit Size : "  + ufvList.size() )
            if(ufvList.size() > 0) {
                for (UnitFacilityVisit ufv : (ufvList as List<UnitFacilityVisit>)) {
                    LinkedHashMap<String, Object> resultMap = new LinkedHashMap<String, Object>()
                    Unit unit = ufv.getUfvUnit()
                    String cargoVoyNum = "0"
                    String cargoVslCd = null
                    String chassisNum = ""
                    String commodity = null
                    String containerChkDigit = ""
                    String containerGrpCd = ""
                    String containerHgtCd = ""
                    String containerNum = ""
                    String containerPrefixCd = ""
                    String containerSzCd = ""
                    String containerSeriesCd = null
                    String containerTypeCd = ""
                    String damageFlg = ""
                    String fullEmptyCd = ""
                    String hazFlg = ""
                    String liveReeferFlg = "N"
                    String oDFlg = "N"
                    String rowDirectionCd = null
                    String rowNum = null
                    String shortStatusCd = ""
                    String slotNum = ""
                    String slotTypeCd = null
                    String svcHoldFlg = isHold(unit)
                    String tierNum = null
                    String userLineCd = null
                    String containerStatusCd = "I"
                    String ufvPosSlot = ""
                    String ufvPosName = ""
                    int yardBayId = 0
                    int yardSlotId = 0
                    double grossWgtKT = 0.00
                    long containerVisitId = 0l
                    int numDigits = String.valueOf(bayToNum).length()
                    String ufvPosition = ufv.getUfvLastKnownPosition()

                    if (ufvPosition != null) {
                        ufvPosSlot = ufv.getUfvLastKnownPosition().getPosSlot()
                        ufvPosName = ufv.getUfvLastKnownPosition().getPosName().replace("Y-PIERG-","")
                        slotNum = ufvPosSlot.replace(".","")
                        slotTypeCd = "B"
                        bayNum = ""
                        if(block.equals("W")) {
                            if (listBinName.contains(slotNum)) {
                                bayNum = slotNum.substring(blocklength)
                                slotTypeCd = "R"
                            }
                        }else if (block.equals("S"))  {
                            String newSlotNum = slotNum.substring(0, slotNum.length()-1)
                            if (listBinName.contains(newSlotNum)) {
                                bayNum = slotNum.substring(blocklength, (blocklength + baylength))
                                tierNum = slotNum.substring(slotNum.length() - 1)
                                rowNum = slotNum.substring((blocklength + baylength), (blocklength + baylength + rowlength))
                                slotTypeCd = "R"
                            }
                        }else if(block.equals("L")) {
                            rowNum = null
                            tierNum = null
                        }
                    }
                    if ("IMPRT".equals(unit.getUnitCategory().getName())) {
                        CarrierVisit ibcv = ufv.getUfvActualIbCv()
                        if(ibcv != null) {
                            VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(ibcv);
                            if (vvd != null) {
                                cargoVoyNum = vvd.getVvdIbVygNbr().replaceAll("[^0-9]", "")
                                cargoVslCd = vvd.getVesselId()
                            }
                        }
                    } else if ("EXPRT".equals(unit.getUnitCategory().getName())) {
                        CarrierVisit obcv = ufv.getUfvActualObCv()
                        if(obcv != null) {
                            VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(obcv);
                            if (vvd != null) {
                                cargoVoyNum = vvd.getVvdObVygNbr().replaceAll("[^0-9]", "")
                                cargoVslCd = vvd.getVesselId()

                            }
                        }
                    } else if ("STRGE".equals(unit.getUnitCategory().getName())) {
                        CarrierVisit ibcv = ufv.getUfvActualIbCv()
                        if(ibcv != null) {
                            VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(ibcv);
                            if (vvd != null) {
                                cargoVoyNum = vvd.getVvdIbVygNbr().replaceAll("[^0-9]", "")
                                cargoVslCd = vvd.getVesselId()
                            }
                        }
                    }
                    EqBaseOrderItem eqBaseOrderItem = unit.getUnitDepartureOrderItem()
                    if (eqBaseOrderItem != null) {
                        EquipmentOrderItem eqoi = EquipmentOrderItem.resolveEqoiFromEqboi(eqBaseOrderItem);
                        if (eqoi != null) {
                            commodity = eqoi.getEqoiCommodityDesc()
                        }
                    }
                    UnitEquipment chasEquip = unit.getCarriageUnit()
                    def attachedUnit = unit.getUnitAttachedEquipIds()
                    if (attachedUnit != null && chasEquip !=null)
                    {
                        chassisNum = unit.getUnitAttachedEquipIds()
                    }
                    Equipment equipment = unit.getUnitEquipment()
                    if (equipment != null) {
                        containerChkDigit = equipment.getEqIdCheckDigit()
                        containerGrpCd = equipment.getEqEquipType().getEqtypIsoGroup().getName()
                        containerHgtCd = equipment.getEqEquipType().getEqtypNominalHeight().getName().replace("NOM", "")
                        containerNum = equipment.getEqIdNbrOnly()
                        containerPrefixCd = equipment.getEqIdPrefix()
                        containerSeriesCd = null
                        containerSzCd = equipment.getEqEquipType().getEqtypNominalLength().getName().replace("NOM", "")
                    }
                    containerTypeCd = unit.isReefer() ? "RF" : "DR"
                    containerVisitId = ufv.getUfvGkey()
                    damageFlg = unit.isDamaged(unit.getPrimaryEq()) ? "Y" : null
                    fullEmptyCd = unit.getUnitFreightKind().getName().substring(0, 1)
                    grossWgtKT = Math.round(unit.getUnitGoodsAndCtrWtKg() / 1000.00)
                    hazFlg = unit.getUnitIsHazard() ? "Y" : "N"
                    Routing routing = unit.getUnitRouting()
                    String portOfDischarge = null
                    if (routing != null) {
                        RoutingPoint routingPoint = routing.getRtgPOD1()
                        if (routingPoint != null) {
                            portOfDischarge = routingPoint.getPointId()
                        }
                    }
                    shortStatusCd = unit.getUnitCategory().getName()
                    userLineCd = unit.getUnitLineOperator().getBzuId()
                    if(bayFromNum.length()==1) {
                        bayFromNum = "0"+bayFromNum
                    }
                    if(equipment.getEqEquipType().getEqtypClass().getName() == "CONTAINER") {
                        resultMap.put("Gkey", ufv.getUfvGkey())
                        resultMap.put("BayDirectionCd", "A")
                        resultMap.put("BayNum", bayNum)
                        resultMap.put("YardBlockTypeCd", block)
                        resultMap.put("CargoCallSeq", 1)   // Todo...
                        resultMap.put("CargoVoyNum", cargoVoyNum)
                        resultMap.put("CargoVslCd", cargoVslCd)
                        resultMap.put("ChassisNum", chassisNum)
                        resultMap.put("CmdtyCd", commodity)
                        resultMap.put("ContainerChkDigit", "")
                        resultMap.put("ContainerGrpCd", containerGrpCd)
                        resultMap.put("ContainerHgtCd", containerHgtCd)
                        resultMap.put("ContainerNum", containerNum)
                        resultMap.put("ContainerPrefixCd", containerPrefixCd)
                        resultMap.put("ContainerSeriesCd", containerSeriesCd)
                        resultMap.put("ContainerTypeCd", containerTypeCd)
                        resultMap.put("ContainerVisitId", containerVisitId)
                        resultMap.put("ContainerSzCd", containerSzCd)
                        resultMap.put("DamageFlg", unit.isUnitDamaged() ? "Y" : "N" )
                        resultMap.put("FullEmptyCd", fullEmptyCd)
                        resultMap.put("GrossWgtKT", grossWgtKT)
                        resultMap.put("HazFlg", unit.getUnitIsHazard() ? "Y" : "N")
                        resultMap.put("LiveReeferFlg", unit.isReefer() ? "Y" : "N" )
                        resultMap.put("MaxRowNum", maxRowNum)
                        resultMap.put("MaxTierNum", maxTierNum)
                        resultMap.put("ODFlg", unit.isOutOfGuage() ? "Y" : "N")
                        resultMap.put("PODCd", portOfDischarge)
                        resultMap.put("POD", portOfDischarge)
                        resultMap.put("RowDirectionCd", rowDirectionCd)
                        resultMap.put("RowNum", rowNum)
                        resultMap.put("ShortStatusCd", shortStatusCd)
                        resultMap.put("SlotNum", ufvPosName)
                        resultMap.put("SlotTypeCd", slotTypeCd)
                        resultMap.put("SvcHoldFlg",svcHoldFlg) // Todo..
                        resultMap.put("TierNum", tierNum)
                        resultMap.put("UserLineCd", userLineCd)
                        resultMap.put("YardBayId", bayNum)
                        resultMap.put("YardSlotId", rowNum)
                        resultMap.put("ContainerStatusCd", containerStatusCd)
                        resultMap.put("BayFromNum", bayFromNum)
                        resultMap.put("BayToNum", bayToNum)

                        resultListMap.add(resultMap)
                    }
                }
            }
            if(resultListMap.size() == 0 ) {
                LinkedHashMap<String, Object> resultMap = new LinkedHashMap<String, Object>()
                resultMap.put("Gkey",0)
                resultMap.put("YardBlockId",1)
                resultMap.put("YardMasterId",1)
                resultMap.put("YardBlockNum",blockNum)
                resultMap.put("BayFromNum", bayFromNum)
                resultMap.put("BayToNum", bayToNum)
                resultMap.put("YardBlockTypeCd",block)
                resultMap.put("MaxRowNum", maxRowNum)
                resultMap.put("MaxTierNum", maxTierNum)
                resultMap.put("BayDirectionCd","A")
                resultMap.put("RowDirectionCd",null)
                resultMap.put("BayNum",null)
                resultMap.put("CargoCallSeq",null)
                resultMap.put("CargoVoyNum",null)
                resultMap.put("CargoVslCd",null)
                resultMap.put("ChassisNum",null)
                resultMap.put("CmdtyCd",null)
                resultMap.put("ContainerChkDigit",null)
                resultMap.put("ContainerGrpCd",null)
                resultMap.put("ContainerHgtCd",null)
                resultMap.put("ContainerNum",null)
                resultMap.put("ContainerPrefixCd",null)
                resultMap.put("ContainerSeriesCd",null)
                resultMap.put("ContainerSzCd",null)
                resultMap.put("ContainerTypeCd",null)
                resultMap.put("ContainerVisitId",null)
                resultMap.put("DamageFlg",null)
                resultMap.put("FullEmptyCd",null)
                resultMap.put("GrossWgtKT",null)
                resultMap.put("RowNum",null)
                resultMap.put("HazFlg",null)
                resultMap.put("LiveReeferFlg",null)
                resultMap.put("ODFlg",null)
                resultMap.put("PODCd",null)
                resultMap.put("POD",null)
                resultMap.put("ShortStatusCd",null)
                resultMap.put("SlotNum",null)
                resultMap.put("SlotTypeCd",null)
                resultMap.put("SvcHoldFlg",null)
                resultMap.put("TierNum",null)
                resultMap.put("UserLineCd",null)
                resultMap.put("YardBayId",null)
                resultMap.put("YardSlotId",null)
                resultMap.put("YardZoneCd",null)
                resultMap.put("ContainerStatusCd",null)

                resultListMap.add(resultMap)
            }
        }
        LOG.info( "resultListMap : "+ resultListMap.size() )
        return resultListMap
    }

    @Nullable
    static List<Map<String, Object>> getQueryResultYardSlotMap(String blockGkey,String bayNum) {
        final Yard yard = ContextHelper.getThreadYard()
        LOG.info("getQueryResultYardSlotMap blockGkey [" +blockGkey  +"][ bayNum " + bayNum +"]")
        long yardAbnGkey = yard.yrdBinModel.getAbnGkey()
        long yardBlockId =0l
        long bayFrom=0l
        long bayTo =0l
        long maxRowNum=0l
        long maxTierNum=0l
        long yardBayId=0l
        long bayNumYSN=0l
        long minrowidx=0l
        long minrowpariedinto =0l
        long abnGkey = 0l;
        String rowNum=null
        String tierNum=null
        String slotTypeCd=null
        String slotStatusCd=null
        String yardSlotId=null
        String slotNum=null
        String bayDirectionCd=null
        String rowDirectionCd=null

        List<LinkedHashMap<String, Object>> resultListMap  = new ArrayList<LinkedHashMap<String, Object>>()

        DomainQuery dq = QueryUtils.createDomainQuery("AbstractYardBlock")
        dq.addDqPredicate(PredicateFactory.like(BinField.ABN_GKEY, blockGkey))
        dq.addDqPredicate(PredicateFactory.eq(BinField.ABN_SUB_TYPE, "SBL"))
        dq.addDqPredicate(PredicateFactory.eq(BinField.ABN_PARENT_BIN, yardAbnGkey))
        dq.setFilter(ObsoletableFilterFactory.createShowActiveFilter())

        List<AbstractYardBlock> yardBlockList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        IArgoYardUtils argoYardUtils = (IArgoYardUtils) Roastery.getBean("argoYardUtils")

        for (AbstractYardBlock abstractYardBlock : (yardBlockList as List<AbstractYardBlock>)) {

            String blockNum = abstractYardBlock.getAbnName()
            String blockBay = blockNum+bayNum
            LOG.info("blockBay " + blockBay)

            String block = "L";
            if("SBL".equals(abstractYardBlock.getAbnSubType())) {
                if (argoYardUtils.isBlockWheeledBlock(abstractYardBlock)) {
                    block = "W"
                } else if (argoYardUtils.isBlockGroundedBlock(abstractYardBlock)) {
                    block = "S"
                }
            }
            String masterAbnName = abstractYardBlock.getAbnName()
            if("S".equals(block) ) {
                List<AbstractSection> ysnSection = getYSN(abstractYardBlock.getPrimaryKey())
                if (!ysnSection.isEmpty() &&   ysnSection.size() > 0) {
                    Serializable ystGkey = ysnSection.get(0).getPrimaryKey()
                    String sectionNameFirst = ysnSection.get(0).getAbnName()
                    String sectionNameLast = ysnSection.get(ysnSection.size() - 1).getAbnName()
                    bayFrom = Long.valueOf(sectionNameFirst.replace(masterAbnName, ""))
                    bayTo = Long.valueOf(sectionNameLast.replace(masterAbnName, ""))

                    List<AbstractStack> yst = getYstIndex(ystGkey)
                    maxRowNum = yst.size()
                    maxTierNum =abstractYardBlock.getMaxTier()
                }
            } else if ("W".equals(block)) {
                List<AbstractSection> ysnSection = getYSN(abstractYardBlock.getPrimaryKey())
                if (!ysnSection.isEmpty() &&   ysnSection.size() > 0) {
                    Serializable ystGkey = ysnSection.get(0).getPrimaryKey()
                    List<AbstractStack> ystSection = getYstAbnName(ystGkey)
                    if (!ystSection.isEmpty() && ystSection.size() > 0) {
                        String stackNameFirst = ystSection.get(0).getAbnName()
                        String stackNameLast = ystSection.get(ystSection.size() - 1).getAbnName()
                        bayFrom = Long.valueOf(stackNameFirst.replace(masterAbnName, ""))
                        bayTo = Long.valueOf(stackNameLast.replace(masterAbnName, ""))
                        maxRowNum = 0
                        maxTierNum = 0
                    }
                }
            } else if("L".equals(block) ) {
                bayFrom = 0
                bayTo = 0
                maxTierNum = 0
                maxRowNum = 0
            }
            int blocklength = abstractYardBlock.getAbnName().length()
            int baylength = 0
            int rowlength = 0
            int tierlength = 0
            String labelUIFullPosition = abstractYardBlock.getAyblkLabelUIFullPosition()
            if (labelUIFullPosition != null) {
                String[] struiFullPosition = labelUIFullPosition.split("\\.")
                if (struiFullPosition.length > 2) {
                    baylength = Integer.valueOf(struiFullPosition[1].replaceAll("[^0-9]", ""))
                    rowlength = Integer.valueOf(struiFullPosition[2].replaceAll("[^0-9]", ""))
                    tierlength = Integer.valueOf(struiFullPosition[3].replaceAll("[^0-9]", ""))
                } else {
                    baylength = abstractYardBlock.getAyblkLabelUIRowOnly() != null ? Integer.valueOf(abstractYardBlock.getAyblkLabelUIRowOnly().replaceAll("[^0-9]", "")) : 0
                    rowlength = abstractYardBlock.getAyblkLabelUIColOnly() != null ? Integer.valueOf(abstractYardBlock.getAyblkLabelUIColOnly().replaceAll("[^0-9]", "")) : 0
                    tierlength = abstractYardBlock.getAyblkLabelUITierOnly() != null ? Integer.valueOf(abstractYardBlock.getAyblkLabelUITierOnly().replaceAll("[^0-9]", "")) : 0

                }
            }
            int binLength = (blocklength+baylength+rowlength+tierlength)
            abnGkey = abstractYardBlock.getAbnGkey()
            LOG.warn("abstractYardBlock.getAbnGkey : " + abnGkey)
            List<String> findPositionSlot = new ArrayList<String>()
            String baynumStr =""
            if (Integer.valueOf(bayNum) % 2 == 0) {
                // even
                if ( Integer.valueOf(bayNum)-1  >= bayFrom  && Integer.valueOf(bayNum) -1 <= bayTo ) {
                    baynumStr = String.format("%0" + baylength + "d", (Integer.valueOf(bayNum)-1))
                    findPositionSlot.add(YardFacility+blockNum+"."+baynumStr)
                }
                if ( Integer.valueOf(bayNum)-2  >= bayFrom  && Integer.valueOf(bayNum) -2 <= bayTo ) {
                    baynumStr = String.format("%0" + baylength + "d", (Integer.valueOf(bayNum)-2))
                    findPositionSlot.add(YardFacility+blockNum+"."+baynumStr)
                }
                if ( Integer.valueOf(bayNum)+1  >= bayFrom  && Integer.valueOf(bayNum) +1 <= bayTo ) {
                    baynumStr = String.format("%0" + baylength + "d", (Integer.valueOf(bayNum)+1))
                    findPositionSlot.add(YardFacility+blockNum+"."+baynumStr)
                }
                if ( Integer.valueOf(bayNum)+2  >= bayFrom  && Integer.valueOf(bayNum) +2 <= bayTo ) {
                    baynumStr = String.format("%0" + baylength + "d", (Integer.valueOf(bayNum)+2))
                    findPositionSlot.add(YardFacility+blockNum+"."+baynumStr)
                }
            } else {
                // odd
                if ( Integer.valueOf(bayNum)-1  >= bayFrom  && Integer.valueOf(bayNum) -1 <= bayTo ) {
                    baynumStr = String.format("%0" + baylength + "d", (Integer.valueOf(bayNum)-1))
                    findPositionSlot.add(YardFacility+blockNum+"."+baynumStr)
                }
                if ( Integer.valueOf(bayNum)+1  >= bayFrom  && Integer.valueOf(bayNum) +1 <= bayTo ) {
                    baynumStr = String.format("%0" + baylength + "d", (Integer.valueOf(bayNum)+1))
                    findPositionSlot.add(YardFacility+blockNum+"."+baynumStr)
                }
            }
            DomainQuery domainQuery = QueryUtils.createDomainQuery("UnitFacilityVisit")
            domainQuery.addDqPredicate(PredicateFactory.eq(UnitField.UFV_VISIT_STATE, UnitVisitStateEnum.ACTIVE))
            domainQuery.addDqPredicate(PredicateFactory.eq(InventoryField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD))

            if(findPositionSlot.size() > 0) {
                Disjunction slotJunction = (Disjunction) PredicateFactory.disjunction()
                for(String slot : findPositionSlot) {
                    slotJunction.add(PredicateFactory.like(UFV_LAST_KNOWN_POSITION_NAME, slot+"%" ))
                }
                domainQuery.addDqPredicate(slotJunction)
            }
            List<UnitFacilityVisit> list = HibernateApi.getInstance().findEntitiesByDomainQuery(domainQuery)
            LOG.info("YardSlotMap >>> UnitFacilityVisit size " + list.size())
            for(UnitFacilityVisit ufv : (list as List<UnitFacilityVisit>)) {
                LinkedHashMap<String, Object> resultMap  = new LinkedHashMap<String, Object>()
                resultMap.put("YardBlockId", abnGkey)
                resultMap.put("BayFrom", bayFrom)
                resultMap.put("BayToNum", bayTo)
                resultMap.put("MaxRowNum", maxRowNum)
                resultMap.put("MaxTierNum", maxTierNum)
                resultMap.put("BayDirectionCd", "A")
                resultMap.put("RowDirectionCd", null)
                resultMap.put("YardBayId", abnGkey)
                resultMap.put("YardSlotId", null)

                if(ufv.getUfvLastKnownPosition() != null ) {
                    String ufvPosSlot = ufv.getUfvLastKnownPosition().getPosSlot()
                    String ufvPosName = ufv.getUfvLastKnownPosition().getPosName().replace("Y-PIERG-","")
                    slotNum = ufv.getUfvLastKnownPosition().getPosSlot().replace(".", "")
                    String filterName = ""
                    String bayrowtierStr = ""
                    if (slotNum.length() >= (blocklength + baylength) ) {
                         filterName = slotNum.substring(0, blockNum.length() + baylength)
                         bayrowtierStr = slotNum.replace(filterName, "")

                    }
                    if (slotNum.length() == binLength) {
                        rowNum = Integer.valueOf((bayrowtierStr.substring(0, bayrowtierStr.length() - 1)))
                        tierNum = Integer.valueOf((bayrowtierStr.substring(bayrowtierStr.length() - 1)))
                    }
                    resultMap.put("BayNum ", filterName.replace(abstractYardBlock.getAbnName(), ""))
                    resultMap.put("SlotNum", ufvPosName)
                    resultMap.put("RowNum", rowNum)
                    resultMap.put("TierNum", tierNum)
                    resultMap.put("SlotTypeCd", null)
                    resultMap.put("SlotStatusCd", "X")
                }
                resultListMap.add(resultMap)
            }
        }
        return resultListMap
    }

    @NotNull
    private static List<AbstractSection> getYSN(Serializable gkey) {
        DomainQuery dq = QueryUtils.createDomainQuery("AbstractSection")
        dq.addDqField(BinField.ABN_NAME)
        dq.addDqPredicate(PredicateFactory.eq(BinField.ABN_PARENT_BIN, gkey))
        dq.setFilter(ObsoletableFilterFactory.createShowActiveFilter());
        dq.addDqOrdering(Ordering.asc(BinField.ABN_NAME))

        List<AbstractSection> ysnList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        return ysnList
    }

    @NotNull
    private static List<AbstractStack> getYstIndex(Serializable gkey) {
        DomainQuery dq = QueryUtils.createDomainQuery("AbstractStack")
        dq.addDqField(BlockField.AST_COL_INDEX)
        dq.addDqPredicate(PredicateFactory.eq(BinField.ABN_PARENT_BIN, gkey))
        dq.addDqOrdering(Ordering.asc(BinField.ABN_Z_INDEX_MAX))
        dq.setFilter(ObsoletableFilterFactory.createShowActiveFilter());

        List<AbstractStack> ystIndexList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        return ystIndexList
    }

    @NotNull
    private static List<AbstractStack> getYstAllAbnName(List<Serializable> serializableYsn ) {
        DomainQuery dq = QueryUtils.createDomainQuery("AbstractStack")
        dq.addDqField(BinField.ABN_NAME)
        dq.addDqField(BinField.ABN_NAME_ALT)
        dq.addDqPredicate(PredicateFactory.in(BinField.ABN_PARENT_BIN, serializableYsn))
        dq.setFilter(ObsoletableFilterFactory.createShowActiveFilter());
        dq.addDqOrdering(Ordering.asc(BinField.ABN_NAME))

        List<AbstractStack> ystNameList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        return ystNameList
    }

    @NotNull
    private static List<AbstractStack> getYstAbnName(Serializable gkey) {
        DomainQuery dq = QueryUtils.createDomainQuery("AbstractStack")
        dq.addDqField(BinField.ABN_NAME)
        dq.addDqPredicate(PredicateFactory.eq(BinField.ABN_PARENT_BIN, gkey))
        dq.setFilter(ObsoletableFilterFactory.createShowActiveFilter());
        dq.addDqOrdering(Ordering.asc(BinField.ABN_NAME))

        List<AbstractStack> ystNameList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        return ystNameList
    }


    private static String isHold( Unit inUnit) {
        String hasHolds = "N"
        if(inUnit.getUnitStoppedRail() || inUnit.getUnitStoppedRoad() || inUnit.getUnitStoppedVessel()) {
            hasHolds = "Y"
        }
        return hasHolds
    }

    private static final  MetafieldId UFV_LAST_KNOWN_POSITION_SLOT = MetafieldIdFactory.valueOf("ufvLastKnownPosition.posSlot")
    private static final MetafieldId  UFV_LAST_KNOWN_POSITION_NAME = MetafieldIdFactory.valueOf("ufvLastKnownPosition.posName")
    private ServicesManager _srvMgr = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);
    private static final Logger LOG = Logger.getLogger(ITSYardBlockCallback.class)
    private static final String YardFacility = "Y-PIERG-"

}
