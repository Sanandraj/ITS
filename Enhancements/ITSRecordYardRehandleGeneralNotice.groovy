import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.LocPosition
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.portal.FieldChanges
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.orders.OrdersField
import com.navis.services.business.event.Event
import com.navis.services.business.event.EventFieldChange
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import org.apache.log4j.Logger

/**
 * Billing 7-3 Yard rehandle fee
 * Weserve - 08/17 Record a event UNIT_YARD_REHANDLE when the Unit is moved from a Undeliverable spot to a Deliverable spot.
 * Configured against all Yard move Event triggers.
 */
class ITSRecordYardRehandleGeneralNotice extends AbstractGeneralNoticeCodeExtension{
    @Override
    void execute(GroovyEvent inGroovyEvent) {
        super.execute(inGroovyEvent)

        log.warn("Fieldd changes "+inGroovyEvent.getEvent().getEvntFieldChanges())

        for (EventFieldChange efc : inGroovyEvent.getEvent().getEvntFieldChanges()) {
            log.warn("Field Id:" + efc.getMetafieldId());
            log.warn("Field Value:" + efc.getPrevVal());

        }
        //return
        Unit unit = (Unit) inGroovyEvent.getEntity()
        String prevPosition = inGroovyEvent.getPreviousPropertyAsString("PositionFull")
        log.warn("prevPosition "+prevPosition)


        if(unit.getUnitActiveUfvNowActive() && prevPosition){
            UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
            LocPosition position = ufv.getUfvLastKnownPosition()

            LocPosition prevPositionLoc = null
            if (prevPosition != null) {
                if(prevPosition.indexOf('Y-PIERG-') != -1){
                    prevPosition = prevPosition.replaceAll('Y-PIERG-','')
                }
                prevPositionLoc = LocPosition.resolvePosition(ContextHelper.getThreadFacility(), LocTypeEnum.YARD, ContextHelper.getThreadYardId(), prevPosition, null, unit.getUnitEquipment().getEqEquipType().getEqtypBasicLength())
            }

            String prevblockName = prevPositionLoc.getBlockName()
            if (prevblockName == null && prevPositionLoc.getPosSlot() != null && prevPositionLoc.getPosSlot().indexOf('.') != -1) {
                prevblockName = prevPositionLoc.getPosSlot().split('\\.')[0]
            }

            String blockName = position.getBlockName()
            if (blockName == null && position.getPosSlot() != null && position.getPosSlot().indexOf('.') != -1) {
                blockName = position.getPosSlot().split('\\.')[0]
            }
            if(blockName != null && isBlockDeliverable(blockName) && prevblockName && (!isBlockDeliverable(prevblockName))){// CONFIRM prevPositionLoc == null ||
                EventType yardRehandle = EventType.findEventType("UNIT_YARD_REHANDLE")
                FieldChanges fc = new FieldChanges()
                fc.setFieldChange(UnitField.POS_SLOT, prevPosition, position.getPosSlot())
                unit.recordEvent(yardRehandle, fc, "Unit moved from Undeliverable to Deliverable location", ArgoUtils.timeNow())
            }
        }
    }

    boolean isBlockDeliverable(String blkId) {
        GeneralReference genRef = GeneralReference.findUniqueEntryById("ITS", "DELIVERABLE_BLOCK", blkId)

        if (genRef != null && genRef.getRefValue1().equalsIgnoreCase("Y")) {
            return true
        }
        return false
    }

    private static final Logger log  = Logger.getLogger(this.class)
}
