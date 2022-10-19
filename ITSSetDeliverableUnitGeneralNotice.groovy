package ITS

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.LogicalEntity
import com.navis.argo.business.api.Serviceable
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.LocPosition
import com.navis.cargo.business.model.BillOfLading
import com.navis.cargo.business.model.GoodsBl
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.portal.FieldChanges
import com.navis.framework.util.DateUtil
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import com.navis.services.business.rules.FlagType
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger

/**
 * Set Deliverable (unitFlexString03) to 'Y' or 'N' based on the General reference set-up
 * Set First Available Day (UnitFlexDate01)
 *
 * Billing 7-4 Container sorting.docx
 * Weserve - 08/22 Record a event Billable UNIT_DELIVERABLE_MOVE when the Unit is moved after 4 deliverable days.
 * Configured against all Discharge Event triggers. UNIT_YARD_MOVE/ UNIT_POSITION_CORRECTION
 *
 * 3-2 - Set Deliverable only if the List of Delivery holds are released for the Unit - '1H', '7H', '2H', '71', '72', '73'
 */
class ITSSetDeliverableUnitGeneralNotice extends AbstractGeneralNoticeCodeExtension {
    @Override
    void execute(GroovyEvent inGroovyEvent) {

        Event event = inGroovyEvent.getEvent()
        Unit unit = (Unit) inGroovyEvent.getEntity()
        String currPosition = inGroovyEvent.getPropertyAsString("PositionSlot")
        LOG.warn("currPosition " + currPosition)
        UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
        if (ufv != null) {

            if (UnitCategoryEnum.IMPORT == unit.getUnitCategory()) {
                LocPosition lastKnownPosition = ufv.getUfvLastKnownPosition()
                Date firstAvailableDay = ufv.getUfvFlexDate01()

                if (firstAvailableDay != null && DateUtil.differenceInDays(firstAvailableDay, ArgoUtils.timeNow(), ContextHelper.getThreadUserTimezone()) >= 4) {
                    EventType deliverableMove = EventType.findEventType("UNIT_DELIVERABLE_MOVE")
                    FieldChanges fc = new FieldChanges()
                    fc.setFieldChange(UnitField.POS_SLOT, currPosition != null ? currPosition : null, lastKnownPosition.getPosSlot())
                    unit.recordEvent(deliverableMove, fc, "Deliverable unit re-handled.", ArgoUtils.timeNow())
                }

                LocPosition position = null
                String blockName = null
                if (currPosition != null) {
                    position = LocPosition.resolvePosition(ContextHelper.getThreadFacility(), LocTypeEnum.YARD, ContextHelper.getThreadYardId(), currPosition, null, unit.getUnitEquipment().getEqEquipType().getEqtypBasicLength())
                }

                if (position != null && StringUtils.isNotEmpty(position.getPosSlot())) {
                    blockName = (position.getBlockName() != null) ? position.getBlockName() :
                            position.getPosSlot().indexOf('.') != -1 ? position.getPosSlot().split('\\.')[0] : null
                }


                GoodsBase goodsBase = unit.getUnitGoods()
                boolean isHoldReleased = true
                if (goodsBase) isHoldReleased = isDeliverableHoldsReleased(goodsBase)

                if (position != null && blockName != null && isBlockDeliverable(blockName) && isHoldReleased) {
                    if (ufv.getUfvFlexDate01() == null) {
                        EventType deliverableMoveFirst = EventType.findEventType("UNIT_DELIVERABLE_DISCHARGE")
                        unit.recordEvent(deliverableMoveFirst, null, "Deliverable unit.", ArgoUtils.timeNow())

                    }
                    unit.setUnitFlexString03("Y")
                    ufv.setUfvFlexDate01(ArgoUtils.timeNow())
                    //First available day - storage rule start time
                } else {
                    unit.setUnitFlexString03("N")
                    ufv.setUfvFlexDate01(null)
                }
            }
        }
    }

    boolean isDeliverableHoldsReleased(goodsBase) {
        def holdMap = ['1H', '7H', '2H', '71', '72', '73']
        GoodsBl goodsBl = GoodsBl.resolveGoodsBlFromGoodsBase(goodsBase)
        Set<BillOfLading> blSet = goodsBl.getGdsblBillsOfLading()

        blSet.each {
            bl ->

                holdMap.each {
                    if (isFlagActive(bl, it)) {
                        LOG.warn("Active flag " + it)

                        return false
                    }
                }
        }
        return true
    }

    private boolean isFlagActive(LogicalEntity logicalEntity, String holdId) {
        FlagType type = FlagType.findFlagType(holdId)
        if (type != null) {
            return type.isActiveFlagPresent(logicalEntity, null, (Serviceable) logicalEntity)
        }
        return false
    }

    boolean isBlockDeliverable(String blkId) {
        GeneralReference genRef = GeneralReference.findUniqueEntryById("ITS", "DELIVERABLE_BLOCK", blkId)

        if (genRef != null && genRef.getRefValue1().equalsIgnoreCase("Y")) {
            LOG.warn("Block deliverable " + blkId)
            return true
        }
        return false
    }


    private static final Logger LOG = Logger.getLogger(this.class)
}
