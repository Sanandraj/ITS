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
import com.navis.services.business.event.Event
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

        Event event = inGroovyEvent.getEvent()
        Unit unit = (Unit) inGroovyEvent.getEntity()
        String prevPosition = inGroovyEvent.getPropertyAsString("PositionSlot")
        log.warn("prevPosition "+prevPosition)
        if(unit.getUnitActiveUfvNowActive()){
            UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
            LocPosition position = ufv.getUfvLastKnownPosition()

            LocPosition prevPositionLoc = null
            if (prevPosition != null) {
                prevPositionLoc = LocPosition.resolvePosition(ContextHelper.getThreadFacility(), LocTypeEnum.YARD, ContextHelper.getThreadYardId(), prevPosition, null, unit.getUnitEquipment().getEqEquipType().getEqtypBasicLength())
            }
            log.warn("prevPositionLoc "+prevPositionLoc)
            log.warn("pos block name "+position.getBlockName())
            if(position.getBlockName() != null && isBlockDeliverable(position.getBlockName()) && (!isBlockDeliverable(prevPositionLoc.getBlockName()))){// CONFIRM prevPositionLoc == null ||
                EventType yardRehandle = EventType.findEventType("UNIT_YARD_REHANDLE")
                FieldChanges fc = new FieldChanges()
                fc.setFieldChange(UnitField.POS_SLOT, prevPosition, position.getPosSlot())
                unit.recordEvent(yardRehandle, fc, "Unit moved from Undeliverable to Deliverable location", ArgoUtils.timeNow())
            }
        }
    }

    boolean isBlockDeliverable(String blkId) {
        GeneralReference genRef = GeneralReference.findUniqueEntryById("ITS", "DELIVERABLE_BLOCK", blkId)
        log.warn("prevPositionLoc "+blkId)
        log.warn("genRef "+genRef.getRefValue1())

        if (genRef != null && genRef.getRefValue1().equalsIgnoreCase("Y")) {
            return true
        }
        return false
    }

    private static final Logger log  = Logger.getLogger(this.class)
}
