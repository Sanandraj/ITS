package ITS


import com.navis.argo.business.atoms.LogicalEntityEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.inventory.business.units.Unit
import com.navis.services.ServicesField
import com.navis.services.business.event.Event
import org.apache.log4j.Level
import org.apache.log4j.Logger

/*
* @Author <a href="mailto:mnaresh@weservetech.com">Naresh Kumar M.R.</a>, 25/OCT/2022

* Requirements : This groovy is used to update  the event as non billable for conatiner types
*                 added in the General refernce .   .
*
* @Inclusion Location	: Incorporated as a code extension of the type ENTITY_LIFECYCLE_INTERCEPTION.groovy
*/

class ITSUpdateEventAsNonBillableELI extends AbstractEntityLifecycleInterceptor {
    @Override
    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        logger.setLevel(Level.DEBUG)
        logger.debug("ITSUpdateEventAsNonBillableELI is invoked ::")
        Event event = (Event)inEntity._entity
        logger.debug("event::" + event)
        if (LogicalEntityEnum.UNIT.equals(event.getEventAppliedToClass()) && event.getEventTypeIsBillable()) {
            logger.debug(event.getEventTypeId() + " is a billable event::")
            Unit unit = Unit.hydrate((Serializable) inOriginalFieldChanges.findFieldChange(ServicesField.EVNT_APPLIED_TO_PRIMARY_KEY).getNewValue())
            logger.debug("unit::"+unit)
            if (unit != null && unit.getUnitLineOperator() != null && unit.getUnitLineOperator()?.getBzuId()) {
                GeneralReference generalReference = GeneralReference.findUniqueEntryById(ITS, POWER_CONTAINERS, unit.getUnitLineOperator().getBzuId())
                logger.debug("general reference::" + generalReference)
                if (generalReference != null  && generalReference.getRefValue1() != null) {
                    if (unit.getPrimaryEq() != null && unit.getPrimaryEq().getEqEquipType() != null && generalReference.getRefValue1().toUpperCase().contains(unit.getPrimaryEq().getEqEquipType().getEqtypId())) {
                        inMoreFieldChanges.setFieldChange(ServicesField.EVNT_BILLING_EXTRACT_BATCH_ID, null)
                    }
                }
            }
        }
        logger.debug("ITSUpdateEventAsNonBillableELI completed ::")

    }
    private static final String ITS = "ITS";
    private static final String POWER_CONTAINERS = "POWER_CONTAINERS"
    private static final Logger logger = Logger.getLogger(this.class);

}
