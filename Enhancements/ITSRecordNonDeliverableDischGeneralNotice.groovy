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
 * Billing 7-5 OOS fee
 * Weserve - 08/17 Record a event UNIT_NON_DELIVERABLE_DISCH when the Unit is discharged to a Non deliverable spot.
 * Configured against all Discharge Event triggers. UNIT_DISCH/ UNIT_DERAMP /
 * TODO check if this is required on UNIT_RECEIVE
 */

class ITSRecordNonDeliverableDischGeneralNotice extends AbstractGeneralNoticeCodeExtension{

    @Override
    void execute(GroovyEvent inGroovyEvent) {
        Event event = inGroovyEvent.getEvent()
        Unit unit = (Unit) inGroovyEvent.getEntity()
        if(unit.getUnitActiveUfvNowActive()){
            UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
            LocPosition position = ufv.getUfvLastKnownPosition()
            log.warn("pos block name "+position.getBlockName())
            if(position.getBlockName() != null && !isBlockDeliverable(position.getBlockName())){
                EventType yardRehandle = EventType.findEventType("UNIT_NON_DELIVERABLE_DISCH")
                FieldChanges fc = new FieldChanges()
                fc.setFieldChange(UnitField.POS_SLOT, position.getPosSlot())
                unit.recordEvent(yardRehandle, fc, "Unit discharged to Deliverable location", ArgoUtils.timeNow())
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
