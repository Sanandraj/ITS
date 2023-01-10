/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/

package ITS

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.LogicalEntity
import com.navis.argo.business.api.Serviceable
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.model.Yard
import com.navis.cargo.business.model.BillOfLading
import com.navis.cargo.business.model.GoodsBl
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.FieldChanges
import com.navis.framework.util.DateUtil
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.api.EventManager
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import com.navis.services.business.rules.FlagType
import com.navis.spatial.business.model.block.AbstractBlock
import com.navis.yard.business.model.AbstractYardBlock
import com.navis.yard.business.model.YardBinModel
import org.apache.commons.lang.StringUtils

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat

/**
 * @Author: uaarthi@weservetech.com, Aarthi U; Date: 20-07-2022
 *
 *  Requirements: Set Deliverable (unitFlexString03) to 'Y' or 'N' based on the General reference set-up
 *  Set First Available Day (UnitFlexDate01)
 *  3-2 - Set Deliverable only if the List of Delivery holds are released for the Unit - '1H', '7H', '2H', '71', '72', '73'
 *
 * @Inclusion Location: Incorporated as a code extension of the type GENERAL_NOTICES_CODE_EXTENSION
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSSetDeliverableUnitGeneralNotice
 *     Code Extension Type: GENERAL_NOTICES_CODE_EXTENSION
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup Configure general notices “UNIT_YARD_MOVE” and “UNIT_YARD_SHIFT” against code extension name “ITSSetDeliverableUnitGeneralNotice
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *  1.      24-08-2022      Aarthi          IP-301    Record a event Billable UNIT_DELIVERABLE_MOVE when the Unit is moved after Last free day.
 *  2.      19-10-2022      Annalakshmi               Record an event UNIT_DELIVERABLE_DISCHARGE when the unit was first placed in deliverable block .
 *                                                    Configured against all Discharge Event triggers. UNIT_YARD_MOVE/ UNIT_POSITION_CORRECTION
 *
 */
class ITSSetDeliverableUnitGeneralNotice extends AbstractGeneralNoticeCodeExtension {
    @Override
    void execute(GroovyEvent inGroovyEvent) {
        Unit unit = (Unit) inGroovyEvent.getEntity()
        String currPosition = inGroovyEvent.getPropertyAsString("PositionSlot")
        UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
        if (ufv != null) {

            if (UnitCategoryEnum.IMPORT == unit.getUnitCategory()) {
                LocPosition lastKnownPosition = ufv.getUfvLastKnownPosition()

                LocPosition position = null
                String blockName = null
                String bayId = null

                if (currPosition != null && unit.getUnitEquipment().getEqEquipType() != null) {
                    position = LocPosition.resolvePosition(ContextHelper.getThreadFacility(), LocTypeEnum.YARD, ContextHelper.getThreadYardId(), currPosition, null, unit.getUnitEquipment().getEqEquipType().getEqtypBasicLength())
                }

                if (position != null && StringUtils.isNotEmpty(position.getPosSlot())) {
                    blockName = (position.getBlockName() != null) ? position.getBlockName() :
                            position.getPosSlot().indexOf('.') != -1 ? position.getPosSlot().split('\\.')[0] : null

                    if (lastKnownPosition != null && lastKnownPosition.getPosBin() != null && lastKnownPosition.getPosBin().getAbnName()) {
                        Yard thisYard = ContextHelper.getThreadYard();
                        YardBinModel yardModel =
                                (YardBinModel) HibernateApi.getInstance().downcast(thisYard.getYrdBinModel(), YardBinModel.class)
                        AbstractBlock ayBlock = AbstractYardBlock.findYardBlockByCode(yardModel.getPrimaryKey(), ufv.ufvLastKnownPosition.getPosSlot().substring(0, 2))

                        if (ayBlock != null && ayBlock.getAbnName() != null) {
                            LocPosition blockPos = LocPosition.createYardPosition(ContextHelper.threadYard, ayBlock.getAbnName(), null, unit.getUnitEquipment().getEqEquipType().getEqtypBasicLength(), false);
                            AbstractYardBlock finalAyBlock = (blockPos.getPosBin() != null) ? (AbstractYardBlock) HibernateApi.getInstance().downcast(blockPos.getPosBin(), AbstractYardBlock.class) : null;


                            if (lastKnownPosition.getPosSlot() != null && lastKnownPosition.getPosSlot().replaceAll("\\.", "").length() >= 5) {


                                if (finalAyBlock.getAyblkLabelSchemeHost().equalsIgnoreCase('B2R2C2')) {
                                    bayId = lastKnownPosition.getPosSlot().replaceAll('\\.', "")
                                    bayId = bayId.substring(2, 4)
                                } else if (finalAyBlock.getAyblkLabelSchemeHost().equalsIgnoreCase('B2R3C2')) {
                                    bayId = lastKnownPosition.getPosSlot().replaceAll('\\.', "")
                                    bayId = bayId.substring(2, 5)

                                }

                            }
                        }
                    }
                }


                if (lastKnownPosition.isWheeled() || lastKnownPosition.isWheeledHeap() || (ufv.getUfvActualObCv() != null
                        && LocTypeEnum.TRAIN == ufv.getUfvActualObCv().getCvCarrierMode()) || (unit.getUnitRouting() != null && unit.getUnitRouting().getRtgGroup() != null
                        && StringUtils.isNotEmpty(unit.getUnitRouting().getRtgGroup().getGrpId()))) {
                    unit.setUnitFlexString03("Y")
                    unit.setUnitFlexString06("N")
                    if (ufv.getUfvFlexDate01() == null) { // DO not clear the FAD - [Container sorting Fee]
                        ufv.setUfvFlexDate01(ArgoUtils.timeNow())
                    }
                    if (ufv.getUfvFlexDate03() == null) {
                        ufv.setUfvFlexDate03(ArgoUtils.timeNow())
                    }
                    return
                }

                boolean wasDeliverable = unit.getUnitFlexString03() != null && "Y".equalsIgnoreCase(unit.getUnitFlexString03())
                GoodsBase goodsBase = unit.getUnitGoods()
                boolean isHoldReleased = true
                if (goodsBase) isHoldReleased = isDeliverableHoldsReleased(goodsBase)
                if (position != null && blockName != null && isBlockDeliverable(blockName, bayId)) {
                    if (isHoldReleased) {
                        EventType deliverableMoveFirst = EventType.findEventType("UNIT_DELIVERABLE_DISCHARGE")
                        if (deliverableMoveFirst != null) {
                            EventManager eventManager = (EventManager) Roastery.getBean(EventManager.BEAN_ID)
                            Event deliverableMoveEvent = eventManager.getMostRecentEventByType(deliverableMoveFirst, unit);
                            if (deliverableMoveEvent == null) {
                                unit.recordEvent(deliverableMoveFirst, null, "Deliverable unit.", ArgoUtils.timeNow())
                            }

                        }
                        unit.setUnitFlexString06("N")
                        if (ufv.getUfvFlexDate01() == null) { // DO not clear the FAD - [Container sorting Fee]
                            ufv.setUfvFlexDate01(ArgoUtils.timeNow())
                        }
                        //First available day - storage rule start time
                    }
                    unit.setUnitFlexString03("Y")
                    // to understand that fdd is set for deliverable block move and not for hold release
                    if (ufv.getUfvFlexDate03() == null) { // First Discharged to a Deliverable block. FDD
                        ufv.setUfvFlexDate03(ArgoUtils.timeNow())
                    }
                } else {
                    unit.setUnitFlexString03("N")
                    unit.setUnitFlexString06("N")
                    ufv.setUfvFlexDate01(null) //TODO confirm?
                }


                // Billing 7-4 Container sorting Fee
                if (wasDeliverable && "Y".equalsIgnoreCase(unit.getUnitFlexString03()) && ufv.getUfvCalculatedLineStorageLastFreeDay() != null && ArgoUtils.timeNow() > DateUtil.parseStringToDate(ufv.getUfvCalculatedLineStorageLastFreeDay(), getUserContext())) {
                    EventType deliverableMove = EventType.findEventType("UNIT_DELIVERABLE_MOVE")
                    FieldChanges fc = new FieldChanges()
                    fc.setFieldChange(UnitField.POS_SLOT, currPosition != null ? currPosition : null, lastKnownPosition.getPosSlot())
                    unit.recordEvent(deliverableMove, fc, "Deliverable unit re-handled.", ArgoUtils.timeNow())
                }
            }
        }
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

    boolean isBlockDeliverable(String blkId, String bayId) {
        GeneralReference genRef = GeneralReference.findUniqueEntryById("ITS", "DELIVERABLE_BLOCK", blkId)

        if (genRef != null && genRef.getRefValue1().equalsIgnoreCase("Y")) {
            return true
        }

        if (StringUtils.isNotEmpty(bayId)) {
            return isBayDeliverable(blkId, bayId)
        }

        return false
    }

    boolean isBayDeliverable(String blkId, String bayId) {
        GeneralReference genRef = GeneralReference.findUniqueEntryById("ITS", "DELIVERABLE_BAY", blkId, bayId)


        if (genRef != null && genRef.getRefValue1().equalsIgnoreCase("Y")) {

            if (StringUtils.isNotEmpty(genRef.getRefValue2()) || StringUtils.isNotEmpty(genRef.getRefValue3())) {
                if (isDateWithinRange(genRef)) {
                    return true
                }
            } else {
                return true
            }
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

    private static Date getDate(String dt) throws ParseException {
        if (!StringUtils.isEmpty(dt)) {
            Calendar cal = Calendar.getInstance()
            cal.setTime(dateFormat.parse(dt))
            return cal.getTime()
        }
        return null
    }

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

}
