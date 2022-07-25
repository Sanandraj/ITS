import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.LocPosition
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Logger

/**
 * Set Deliverable (unitFlexString03) to 'Y' or 'N' based on the General reference set-up
 */
class ITSSetDeliverableUnitGeneralNotice extends AbstractGeneralNoticeCodeExtension {
    @Override
    void execute(GroovyEvent inGroovyEvent) {

        Event event = inGroovyEvent.getEvent()
        Unit unit = (Unit) inGroovyEvent.getEntity()
        String currPosition = inGroovyEvent.getPropertyAsString("PositionSlot")
        LOG.warn("currPosition "+currPosition)
        UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
        if (ufv != null) {

            LocPosition position = null
            if (currPosition != null) {
                position = LocPosition.resolvePosition(ContextHelper.getThreadFacility(), LocTypeEnum.YARD, ContextHelper.getThreadYardId(), currPosition, null, unit.getUnitEquipment().getEqEquipType().getEqtypBasicLength())
            }

            if (position != null && position.getBlockName() != null && isBlockDeliverable(position.getBlockName())) {
                unit.setUnitFlexString03("Y")
            } else {
                unit.setUnitFlexString03("N")
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


    private static final Logger LOG = Logger.getLogger(this.class)
}
