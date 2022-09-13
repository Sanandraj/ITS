import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.LocPosition
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.portal.FieldChanges
import com.navis.framework.util.DateUtil
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import org.apache.log4j.Logger

/**
 * Set Deliverable (unitFlexString03) to 'Y' or 'N' based on the General reference set-up
 * Set First Available Day (UnitFlexDate01)
 *
 * Billing 7-4 Container sorting.docx
 * Weserve - 08/22 Record a event Billable UNIT_DELIVERABLE_MOVE when the Unit is moved after 4 deliverable days.
 * Configured against all Discharge Event triggers. UNIT_YARD_MOVE/ UNIT_POSITION_CORRECTION
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

            if (UnitCategoryEnum.IMPORT == unit.getUnitCategory()) {
                LocPosition position = ufv.getUfvLastKnownPosition()
                Date firstAvailableDay = ufv.getUfvFlexDate01()
                LOG.warn("firstAvailableDay " + firstAvailableDay)
                if(firstAvailableDay){
                    LOG.warn("Difference " + DateUtil.differenceInDays(ArgoUtils.timeNow(), firstAvailableDay, ContextHelper.getThreadUserTimezone()))
                }


                if (firstAvailableDay != null && DateUtil.differenceInDays(firstAvailableDay, ArgoUtils.timeNow(), ContextHelper.getThreadUserTimezone()) >= 4) {
                    EventType deliverableMove = EventType.findEventType("UNIT_DELIVERABLE_MOVE")
                    FieldChanges fc = new FieldChanges()
                    fc.setFieldChange(UnitField.POS_SLOT, currPosition != null ? currPosition : null, position.getPosSlot())
                    unit.recordEvent(deliverableMove, fc, "Deliverable unit re-handled.", ArgoUtils.timeNow())
                }
            }

            LocPosition position = null
            if (currPosition != null) {
                position = LocPosition.resolvePosition(ContextHelper.getThreadFacility(), LocTypeEnum.YARD, ContextHelper.getThreadYardId(), currPosition, null, unit.getUnitEquipment().getEqEquipType().getEqtypBasicLength())
            }

            if (position != null && position.getBlockName() != null && isBlockDeliverable(position.getBlockName())) {
                unit.setUnitFlexString03("Y")
                ufv.setUfvFlexDate01(ArgoUtils.timeNow())
            } else {
                unit.setUnitFlexString03("N")
                ufv.setUfvFlexDate01(null)
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
