/*
* Copyright (c) 2022 WeServe LLC. All Rights Reserved.
*
*/

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.IArgoYardUtils
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.model.Yard
import com.navis.argo.business.reference.Chassis
import com.navis.argo.business.reference.EquipType
import com.navis.argo.business.reference.Equipment
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.FieldChanges
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.*
import com.navis.framework.query.common.api.QueryResult
import com.navis.inventory.InventoryField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.MoveInfoBean
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.rules.EventType
import com.navis.spatial.BinField
import com.navis.spatial.BlockField
import com.navis.spatial.business.model.AbstractBin
import com.navis.spatial.business.model.BinContext
import com.navis.xpscache.business.atoms.EquipBasicLengthEnum
import com.navis.yard.business.model.AbstractYardBlock
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

/*
* @Author <a href="mailto:rkarthikeyan@weservetech.com">Karthikeyan R</a>,
*
* @Inclusion Location	: Incorporated as a code extension of the type .Copy --> Paste this code (ITSYardPostInEquipmentCallback.groovy)
*
*/


class ITSYardPostInEquipmentCallback extends AbstractExtensionPersistenceCallback {
    @Override
    void execute(@Nullable Map inMap, @Nullable Map outMap) {
        long start = System.currentTimeMillis()
        LOG.info(" start.............................  " + start)
        LOG.info(" inMap .............................. " + inMap.toString())

        StringBuilder json = new StringBuilder()
        String ufgGkey = inMap.get("ufgGkey")
        String strSlotNum = inMap.get("newSlotNum")
        String chassisNum = inMap.get("chassisNum")
        String userName = inMap.get("userName")
        String result = null
        LOG.info(" ufvGkey.................................. " + ufgGkey)
        LOG.info(" chassisNum .............................. " + chassisNum)
        LOG.info(" userName (TMS)........................... " + userName)
        LOG.info(" strSlotNum .............................. " + strSlotNum)

        String newSlotNum = ""
        if (strSlotNum != null) {
            newSlotNum = strSlotNum.replace(".", "")
            slotValidate(newSlotNum)
        }
        LOG.info(" newSlotNum .............................. " + newSlotNum)
        // 60840  - Chassis is married with another container
        // 60415  - Chassis is not in yard
        // 60241  - Slot does not accept container size
        // 60068  - Slot <slotnum> does not exist in the master - done
        // 61218  - Slot <slotnum> is occupied                  - done
        // 61220  - Overleap slot <slotnum> is occupied
        // 60240  - slot does not accept live reefer

        if (newSlotNum == null || newSlotNum == "") {
            json.append(" {'Error': [{'ErrNbr': 60068 } ] } ")
        } else if (!isSlotExist) {
            json.append(" {'Error': [{'ErrNbr': 60068 } ] } ")
        } else if (isOverleap) {
            json.append(" {'Error': [{'ErrNbr': 61220 } ] } ")
        } else if (isHeap) {
            result = updateHeapPosition(ufgGkey, strSlotNum, chassisNum, userName)
            json.append(result)
        } else if (isSlotExist && "W".equals(yardBlockTypeCd)) {
            result = updateWheeledPosition(ufgGkey, strSlotNum, chassisNum, userName)
            json.append(result)
        } else if (isSlotOccupied(strSlotNum, newSlotNum)) {
            json.append(" {'Error': [{'ErrNbr': 61218 } ] }")
        } else if (!acceptOver40xFlg(ufgGkey)) {
            json.append(" {'Error': [{'ErrNbr': 60241 } ] } ")
        } else {
            result = updateRecordYardMove(ufgGkey, strSlotNum, chassisNum, userName)
            json.append(result)
        }

        outMap.put("responseMessage", json.toString())
        long elapsedTime = System.currentTimeMillis() - start
        LOG.info(" - Time taken in milli Seconds to process the request: " + elapsedTime)

    }

    private static String updateHeapPosition(String ufgGkey, String newSlotNum, String chassisNum, String userName) {
        String result = ""
        try {
            UnitFacilityVisit unitFacilityVisit = UnitFacilityVisit.hydrate(Long.valueOf(ufgGkey))
            if (unitFacilityVisit != null) {
                FieldChanges newFieldChanges = new FieldChanges()
                MetafieldId UNIT_SLOT = MetafieldIdFactory.valueOf("unitSlot")
                String unitId = unitFacilityVisit.getUfvUnit().getUnitId()
                LocPosition locPosition = unitFacilityVisit.getUfvLastKnownPosition()
                String oldSlotNum = locPosition.getPosSlot()
                locPosition.setPosSlot(newSlotNum)

                if (userName != null) {
                    ContextHelper.setThreadExternalUser("tms:" + userName.toLowerCase())
                }

                locPosition.updatePosName(LocTypeEnum.YARD, ContextHelper.getThreadYard().getLocId(), newSlotNum)
                unitFacilityVisit.move(locPosition, null)
                HibernateApi.getInstance().save(unitFacilityVisit)
                result = "{'Updae Heap Position': [{'UnitId':\"" + unitId + "\" }]}"

                LOG.info("updatePosition : " + result)

                Unit unit = unitFacilityVisit.getUfvUnit()
                newFieldChanges.setFieldChange(UNIT_SLOT, oldSlotNum, newSlotNum)

                String notes = "Unit heap position updated by tms:" + userName.toLowerCase()
                EventType eventType = EventType.findEventType("UNIT_POSITION_CORRECTION")
                unit.recordUnitEvent(eventType, newFieldChanges, notes)

            }
        } catch (Exception e) {
            LOG.error(e.printStackTrace())
            return   " {'Error': [{'ErrNbr': 90001 } ] } "
        }
        return result
    }

    private static String updateWheeledPosition(String ufgGkey, String newSlotNum, String chassisNum, String userName) {
        String result = ""
        try {

            UnitFacilityVisit unitFacilityVisit = UnitFacilityVisit.hydrate(Long.valueOf(ufgGkey))
            if (unitFacilityVisit != null) {
                Unit unit = unitFacilityVisit.getUfvUnit()
                Chassis chassis = null
                if (chassisNum != null && !chassisNum.trim().isEmpty()) {
                    boolean isMarried = isMarriedChassis(chassisNum)
                    if (isMarried) {
                        return " {'Error': [{'ErrNbr': 60840 } ] } "
                    }
                    boolean findChassis = findChassis(chassisNum)
                    if (!findChassis) {
                        return " {'Error': [{'ErrNbr': 60415 } ] } "
                    }

                    chassis = Chassis.findChassis(chassisNum)
                    Serializable equipPkey = chassis.getChsEqType()
                    EquipType equipType = EquipType.hydrate(equipPkey)
                    boolean is20Flag = equipType.isBasic20Allowed()
                    boolean is40Flag = equipType.isBasic40Allowed()

                    EquipBasicLengthEnum unitBasicLength = unit.getBasicLength()
                    if (EquipBasicLengthEnum.BASIC20 == unitBasicLength && !is20Flag ||
                            EquipBasicLengthEnum.BASIC40 == unitBasicLength && !is40Flag) {
                        return " {'Error': [{'ErrNbr': 60415 } ] } "
                    }
                }
                FieldChanges newFieldChanges = new FieldChanges()
                MetafieldId UNIT_SLOT = MetafieldIdFactory.valueOf("unitSlot")
                String unitId = unitFacilityVisit.getUfvUnit().getUnitId()
                LocPosition locPosition = unitFacilityVisit.getUfvLastKnownPosition()
                String oldSlotNum = locPosition.getPosSlot()
                locPosition.setPosSlot(newSlotNum)

                if (userName != null) {
                    ContextHelper.setThreadExternalUser("tms:" + userName.toLowerCase())
                }

                locPosition.updatePosName(LocTypeEnum.YARD, ContextHelper.getThreadYard().getLocId(), newSlotNum)
                unitFacilityVisit.move(locPosition, null)
                HibernateApi.getInstance().save(unitFacilityVisit)
                result = "{'Updae Wheeled Position': [{'UnitId':\"" + unitId + "\" }]}"

                LOG.info("updatePosition : " + result)

                newFieldChanges.setFieldChange(UNIT_SLOT, oldSlotNum, newSlotNum)
                String notes = "Unit wheeled position updated by tms:" + userName.toLowerCase()
                EventType eventType = EventType.findEventType("UNIT_POSITION_CORRECTION")
                unit.recordUnitEvent(eventType, newFieldChanges, notes)

                LOG.info("wheeld update chassisNum : " + chassisNum)
                if (chassis != null) {
                    unit.attachCarriage(chassis)
                }
            }
        } catch (Exception e) {
            LOG.error(e.printStackTrace())
            return   " {'Error': [{'ErrNbr': 90001 } ] } "
        }
        return result
    }

    private static String updateRecordYardMove(String ufgGkey, String newSlotNum, String chassisNum, String userName) {
        String result = ""
        LOG.info(" updateRecordYardMove....................")
        try {
            UnitFacilityVisit unitFacilityVisit = UnitFacilityVisit.hydrate(Long.valueOf(ufgGkey))
            if (unitFacilityVisit != null) {
                if (userName != null) {
                    ContextHelper.setThreadExternalUser("tms:" + userName.toLowerCase())
                }
                Yard yard = ContextHelper.getThreadYard()
                Unit unit = unitFacilityVisit.getUfvUnit()

                String updateSlotNum = newSlotNum.replace(".", "")
                updateSlotNum = updateSlotNum.substring(0, updateSlotNum.length() - 1) + "." + updateSlotNum.substring(updateSlotNum.length() - 1)
                LOG.info(" updateSlotNum .............................. " + updateSlotNum)
                LocPosition pos = LocPosition.createYardPosition(yard, updateSlotNum, null, unit.getBasicLength(), true)
                unitFacilityVisit.move(pos, (MoveInfoBean) null)
                HibernateApi.getInstance().save(unitFacilityVisit)
                result = "{'Position': [{'UnitId':\"" + unit.getUnitId() + "\" }]}"
                LOG.info(result)
                LOG.info("yardBlockTypeCd : " + yardBlockTypeCd)
                if (yardBlockTypeCd.equals("S")) {
                    UnitEquipment chassisEquip = unit.getUnitCarriageUnit()
                    LOG.info("chassisEquip : " + chassisEquip)

                    if (chassisEquip != null) {
                        LOG.info("unit has detachCarriage chassis")
                        unit.detachCarriage("chassis is detached from carriage")
                    }
                }
            }
        } catch (Exception e) {
            LOG.error(e.printStackTrace())
            return   " {'Error': [{'ErrNbr': 90001 } ] } "
        }
        return result
    }

    private static void slotValidate(String slotnum) {
        isSlotExist = false
        AcceptOver40xFlg = false
        isHeap = false
        isOverleap = false
        long slottier = 0
        final Yard yard = ContextHelper.getThreadYard()
        long yardAbnGkey = yard.yrdBinModel.getAbnGkey()
        AbstractBin yardBinModel = yard.getYrdBinModel()
        Serializable abmBlockGkey
        Map<String, Serializable> map
        BinContext stowageContext = BinContext.findBinContext(Yard.CONTAINER_STOWAGE_BIN_CONTEXT)
        map = yardBinModel.findDescendantBinFromPPos(slotnum, stowageContext)

        if (map.isEmpty()) {
            String findslotnum = slotnum.substring(0, slotnum.length() - 1)
            map = yardBinModel.findDescendantBinFromPPos(findslotnum, stowageContext)
            LOG.info("................map.isEmpty....................")
            if (slotnum.matches(".*\\d.*")) {
                slottier = Long.valueOf(slotnum.substring(slotnum.length() - 1))
            }
        }
        LOG.info(" slotnum     : " + slotnum)
        LOG.info(" slottier     : " + slottier)
        LOG.info(" map         : " + map.toString())
        abmBlockGkey = map.get("ABM_BLOCK")
        LOG.info(" abmBlockGkey :   " + abmBlockGkey)
        if (abmBlockGkey != null) {

            isHeap = map.get("ABM_STACK") == null
            LOG.info(" isHeap     : " + isHeap)
            DomainQuery dq = QueryUtils.createDomainQuery("AbstractYardBlock")
            dq.addDqPredicate(PredicateFactory.eq(BinField.ABN_GKEY, abmBlockGkey))
            dq.addDqPredicate(PredicateFactory.eq(BinField.ABN_PARENT_BIN, yardAbnGkey))
            dq.setFilter(ObsoletableFilterFactory.createShowActiveFilter())

            LOG.info(" abmBlockGkey  DomainQuery :   " + dq.toString())
            List<AbstractYardBlock> yardBlockList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
            IArgoYardUtils argoYardUtils = (IArgoYardUtils) Roastery.getBean("argoYardUtils")

            LOG.info(" yardBlockList size   :   " + yardBlockList.size())
            for (AbstractYardBlock abstractYardBlock : (yardBlockList as List<AbstractYardBlock>)) {

                String labelUIFullPosition = abstractYardBlock.getAyblkLabelUIFullPosition()
                int sumBRCT = 0
                if (!isHeap) {
                    String[] strBRCT = labelUIFullPosition.replaceAll("[^.0-9]", "").split("\\.")
                    for (int i = 0; i < strBRCT.length; i++) {
                        sumBRCT = sumBRCT + Integer.valueOf(strBRCT[i])
                    }
                }
                if (argoYardUtils.isBlockWheeledBlock(abstractYardBlock)) {
                    yardBlockTypeCd = "W"
                } else if (argoYardUtils.isBlockGroundedBlock(abstractYardBlock)) {
                    yardBlockTypeCd = "S"
                } else if (argoYardUtils.isBlockLogicalBlock(abstractYardBlock)) {
                    yardBlockTypeCd = "L"
                } else if (argoYardUtils.isBlockHeap(abstractYardBlock)) {
                    yardBlockTypeCd = "H"
                }
                LOG.info(" yardBlockTypeCd    :   " + yardBlockTypeCd)
                LOG.info(" sumBRCT      :   " + sumBRCT)
                if (slotnum.length() < sumBRCT && yardBlockTypeCd.equals("S")) {
                    isHeap = true
                    LOG.info(" isHeap     : " + isHeap)
                }
                if (map.get("ABM_BLOCK") != null && map.get("ABM_SECTION") != null && map.get("ABM_STACK") != null) {
                    if (slottier <= abstractYardBlock.getAbnZIndexMax()) {
                        isSlotExist = true
                    }

                } else if (map.get("ABM_BLOCK") != null && map.get("ABM_SECTION") != null) {
                    isSlotExist = true
                } else if (map.get("ABM_STACK") == null && map.get("ABM_SECTON") == null) {
                    if (abstractYardBlock.getAbnName().equals(slotnum)) {
                        isSlotExist = true
                    }
                }
                if (!isHeap && isSlotExist) {
                    int blocklength = abstractYardBlock.getAbnName().length()
                    int baylength = 0
                    int rowlength = 0  // column length
                    int tierlength = 0
                    if (labelUIFullPosition != null) {
                        String[] struiFullPosition = labelUIFullPosition.split("\\.")
                        LOG.info(" labelUIFullPosition split length :   " + struiFullPosition.length)
                        if (struiFullPosition.length > 2) {
                            baylength = Integer.valueOf(struiFullPosition[1].replaceAll("[^0-9]", ""))
                            rowlength = Integer.valueOf(struiFullPosition[2].replaceAll("[^0-9]", ""))
                            tierlength = Integer.valueOf(struiFullPosition[3].replaceAll("[^0-9]", ""))

                            LOG.info(" block       :   " + struiFullPosition[0])
                            LOG.info(" bay         :   " + struiFullPosition[1])
                            LOG.info(" row         :   " + struiFullPosition[2])
                            LOG.info(" tier        :   " + struiFullPosition[3])
                        }
                    }
                    String subType = abstractYardBlock.getAbnSubType()
                    if ("SBL".equals(subType) && argoYardUtils.isBlockGroundedBlock(abstractYardBlock)) {
                        String blockNum = abstractYardBlock.getAbnName()
                        String strNewSlot = slotnum.replace(blockNum, "")

                        LOG.info(" strNewSlot    :   " + strNewSlot)
                        String bayNum = strNewSlot.substring(0, baylength)
                        String rowNum = slotnum.substring((blocklength + baylength), (blocklength + baylength + rowlength))
                        String tierNum = slotnum.substring(slotnum.length() - 1)

                        LOG.info(" blockNum    :   " + blockNum)
                        LOG.info(" bayNum      :   " + bayNum)
                        LOG.info(" rowNum      :   " + rowNum)
                        LOG.info(" tierNum     :   " + tierNum)

                        long bayFrom = 1
                        long bayToNum = 1
                        String rownumStr = String.format("%0" + rowlength + "d", Integer.valueOf(rowNum))
                        List<String> findSlot = new ArrayList<String>()
                        String baynumStr = ""
                        if (Integer.valueOf(bayNum) % 2 == 0) {
                            // even slot
                            LOG.info("even slot ")
                            AcceptOver40xFlg = true
                            if (Integer.valueOf(bayNum) - 1 >= bayFrom && Integer.valueOf(bayNum) - 1 >= bayToNum) {
                                baynumStr = String.format("%0" + baylength + "d", (Integer.valueOf(bayNum) - 1))
                                findSlot.add(YardFacility + blockNum + "." + baynumStr + "." + rownumStr + "." + tierNum)
                            }
                            if (Integer.valueOf(bayNum) - 2 >= bayFrom && Integer.valueOf(bayNum) - 2 >= bayToNum) {
                                baynumStr = String.format("%0" + baylength + "d", (Integer.valueOf(bayNum) - 2))
                                findSlot.add(YardFacility + blockNum + "." + baynumStr + "." + rownumStr + "." + tierNum)
                            }
                            if (Integer.valueOf(bayNum) + 1 >= bayFrom && Integer.valueOf(bayNum) + 1 >= bayToNum) {
                                baynumStr = String.format("%0" + baylength + "d", (Integer.valueOf(bayNum) + 1))
                                findSlot.add(YardFacility + blockNum + "." + baynumStr + "." + rownumStr + "." + tierNum)
                            }
                            if (Integer.valueOf(bayNum) + 2 >= bayFrom && Integer.valueOf(bayNum) + 2 >= bayToNum) {
                                baynumStr = String.format("%0" + baylength + "d", (Integer.valueOf(bayNum) + 2))
                                findSlot.add(YardFacility + blockNum + "." + baynumStr + "." + rownumStr + "." + tierNum)
                            }
                        } else {
                            // odd slot
                            LOG.info("odd slot ")
                            AcceptOver40xFlg = false
                            if (Integer.valueOf(bayNum) - 1 >= bayFrom && Integer.valueOf(bayNum) - 1 >= bayToNum) {
                                baynumStr = String.format("%0" + baylength + "d", (Integer.valueOf(bayNum) - 1))
                                findSlot.add(YardFacility + blockNum + "." + baynumStr + "." + rownumStr + "." + tierNum)
                            }
                            if (Integer.valueOf(bayNum) + 1 >= bayFrom && Integer.valueOf(bayNum) + 1 >= bayToNum) {
                                baynumStr = String.format("%0" + baylength + "d", (Integer.valueOf(bayNum) + 1))
                                findSlot.add(YardFacility + blockNum + "." + baynumStr + "." + rownumStr + "." + tierNum)
                            }
                        }
                        LOG.info(" findSlot     :   " + findSlot.toString())
                        DomainQuery domainQuery = QueryUtils.createDomainQuery("UnitFacilityVisit")
                        domainQuery.addDqPredicate(PredicateFactory.in(UFV_LAST_KNOWN_POSITION_NAME, findSlot))
                        domainQuery.addDqPredicate(PredicateFactory.eq(UnitField.UFV_VISIT_STATE, UnitVisitStateEnum.ACTIVE))
                        domainQuery.addDqPredicate(PredicateFactory.eq(InventoryField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD))

                        LOG.info("DomainQuery overleap " + domainQuery.toString())
                        List<UnitFacilityVisit> ufvPosList = HibernateApi.getInstance().findEntitiesByDomainQuery(domainQuery)
                        LOG.info("UnitFacilityVisit size (overleap) " + ufvPosList.size())
                        if (ufvPosList.size() > 0) {
                            isOverleap = true
                            LOG.info("Set OverLeap is True ........" + isOverleap)
                        }
                    }
                }
            }
        }
        LOG.info(" AcceptOver40xFlg " + AcceptOver40xFlg)
        LOG.info(" yardBlockTypeCd  " + yardBlockTypeCd)
        LOG.info(" isSlotExist      " + isSlotExist)
        LOG.info(" is overleap      " + isOverleap)
        LOG.info(" is Heap          " + isHeap)
    }

    private static boolean isSlotOccupied(String Slotnum, String Slotnum2) {
        boolean result = false
        Disjunction slotPostionJunction = (Disjunction) PredicateFactory.disjunction()
                .add(PredicateFactory.eq(UFV_LAST_KNOWN_POSITION_NAME, YardFacility + Slotnum))
                .add(PredicateFactory.eq(UFV_LAST_KNOWN_POSITION_NAME, YardFacility + Slotnum2))

        DomainQuery domainQuery = QueryUtils.createDomainQuery("UnitFacilityVisit")
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_VISIT_STATE, UnitVisitStateEnum.ACTIVE))
                .addDqPredicate(PredicateFactory.eq(InventoryField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD))

        domainQuery.addDqPredicate(slotPostionJunction)

        LOG.info(" domainQuery " + domainQuery.toString())
        List<UnitFacilityVisit> list = HibernateApi.getInstance().findEntitiesByDomainQuery(domainQuery)
        LOG.info(" list " + list.toString())
        if (list.size() > 0) {
            result = true
        }
        LOG.info(" isSlotOccupied................... " + result)
        return result
    }

    private static boolean findChassis(String chassisNum) {
        boolean result = true
        if (yardBlockTypeCd.equals("W")) {
            LOG.info("wheeled update chassisNum : " + chassisNum)
            if (chassisNum != null) {
                Chassis chassis = Chassis.findChassis(chassisNum)
                if (chassis == null) {
                    result = false
                }
            }
        }
        LOG.info(" findChassis................... " + result)
        return result
    }

    private static boolean isMarriedChassis(String chassisNum) {
        boolean result = false
        if (yardBlockTypeCd.equals("W")) {
            LOG.info(" is Married chassisNum : " + chassisNum)
            if (chassisNum != null) {
                MetafieldId UNIT_CARRIAGE = MetafieldIdFactory.valueOf("ufvUnit.unitCarriageUnit.unitEquipment.eqIdFull")
                DomainQuery domainQuery = QueryUtils.createDomainQuery("UnitFacilityVisit")
                        .addDqPredicate(PredicateFactory.eq(UNIT_CARRIAGE, chassisNum))
                        .addDqPredicate(PredicateFactory.eq(UnitField.UFV_VISIT_STATE, UnitVisitStateEnum.ACTIVE))
                        .addDqPredicate(PredicateFactory.eq(InventoryField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD))

                List<UnitFacilityVisit> ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(domainQuery)
                if (ufvList.size() != 0) {
                    result = true
                }
            }
        }
        LOG.info(" isMarriedChassis................... " + result)
        return result
    }

    private static boolean acceptOver40xFlg(String ufgGkey) {
        boolean result = false
        UnitFacilityVisit unitFacilityVisit = UnitFacilityVisit.hydrate(Long.valueOf(ufgGkey))
        if (unitFacilityVisit != null) {
            Equipment equipment = unitFacilityVisit.getUfvUnit().getUnitEquipment()
            String containerSzCd = equipment.getEqEquipType().getEqtypNominalLength().getName().replace("NOM", "")
            LOG.info(" AcceptOver40xFlg  [" + AcceptOver40xFlg + "] containerSzCd [" + containerSzCd + "]")
            if (AcceptOver40xFlg && "40".equals(containerSzCd)) {
                result = true
            } else if (!AcceptOver40xFlg && "20".equals(containerSzCd)) {
                result = true
            }
        }
        LOG.info("acceptOver40xFlg.................. " + result)
        return result
    }

    private static boolean isSlotVacant(String ufgGkey) {
        boolean result = true
        UnitFacilityVisit unitFacilityVisit = UnitFacilityVisit.hydrate(Long.valueOf(ufgGkey))
        if (unitFacilityVisit != null) {
            Equipment equipment = unitFacilityVisit.getUfvUnit().getUnitEquipment()
            String containerSzCd = equipment.getEqEquipType().getEqtypNominalLength().getName().replace("NOM", "")
            if (AcceptOver40xFlg && "20".equals(containerSzCd)) {
                result = false
            }
        }
        LOG.info("isSlotVacant.................. " + result)
        return result
    }

    private static long getMinRow(Long abnGkey) {
        LOG.info("getMinRow abnGkey : " + abnGkey)
        DomainQuery dq = QueryUtils.createDomainQuery("AbstractSection")
        dq.addDqAggregateField(AggregateFunctionType.MIN, BlockField.ASN_ROW_PAIRED_INTO)
        dq.addDqPredicate(PredicateFactory.eq(BinField.ABN_PARENT_BIN, abnGkey))
        dq.setFilter(ObsoletableFilterFactory.createShowActiveFilter())
        QueryResult qr = HibernateApi.getInstance().findValuesByDomainQuery(dq)
        Long returnVal = 0L
        if (qr != null && qr.getTotalResultCount() > 0) {
            returnVal = (Long) qr.getValue(0, BlockField.ASN_ROW_PAIRED_INTO)
            if (returnVal == null) {
                returnVal = 0L
            }
        }
        return returnVal
    }

    private static final MetafieldId UFV_LAST_KNOWN_POSITION_NAME = MetafieldIdFactory.valueOf("ufvLastKnownPosition.posName")
    private static final Logger LOG = Logger.getLogger(ITSYardPostInEquipmentCallback.class)
    private static final String YardFacility = "Y-PIERG-"

    private static String yardBlockTypeCd = null
    private static boolean AcceptOver40xFlg = false
    private static boolean isOverleap = false
    private static boolean isHeap = false
    private static boolean isSlotExist = false

}

