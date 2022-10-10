import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.reference.Container
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.portal.FieldChanges
import com.navis.framework.util.BizViolation
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.services.business.rules.EventType
import org.apache.log4j.Level
import org.apache.log4j.Logger

/*
 * @Author <ahref="mailto:mharikumar@weservetech.com"  >  Harikumar M</a>, 22/SEP/2022
 *
 * Requirement: To detach the original chassis from unit when truck received at ingate with own chassis.
 * @Inclusion Location	: Incorporated as a code extension of the type GATE_TASK_INTERCEPTOR .Copy --> Paste this code (ITSValidateFlipChassisTaskInterceptor.groovy)
 * @Set up :- in  Biz task need to configure this code to validate - RejectTruckBanned.
 *
 */

class ITSValidateFlipChassisTaskInterceptor extends AbstractGateTaskInterceptor {
    private static final Logger LOGGER = Logger.getLogger(ITSValidateFlipChassisTaskInterceptor.class)

    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        //super.execute(inWfCtx)
        //LOGGER.setLevel(Level.DEBUG)
        TruckTransaction tran = inWfCtx.getTran()
        Container ctrId;
        Unit unit;
        if (tran != null) {
            String chsNbr = tran.getTranChsNbr() != null ? tran.getTranChsNbr() : tran.getTranChsNbrAssigned()
            String containerNbr = tran.getTranCtrNbr() != null ? tran.getTranCtrNbr() : tran.getTranCtrNbrAssigned()
            UnitFinder unitFinder = Roastery.getBean(UnitFinder.BEAN_ID);
            if (containerNbr != null && tran.getTranChsIsOwners()) {
                ctrId = Container.findContainer(containerNbr);
                if (ctrId != null) {
                    unit = unitFinder.findActiveUnit(ContextHelper.getThreadComplex(), ctrId);
                    if (unit != null && chsNbr != null) {
                        String relatedUnit = unit?.getUnitRelatedUnit()?.getUnitId()
                        if (relatedUnit != null && !chsNbr.equals(relatedUnit)) {
                            try {
                                unit.detachCarriage("Carriage detached ${relatedUnit}")
                                EventType eventType = EventType.findEventType("UNIT_SWITCH_CHASSIS");
                                FieldChanges changes = new FieldChanges()
                                changes.setFieldChange(UnitField.UFV_RELATED_UNIT_ID, relatedUnit, chsNbr)
                                unit.recordEvent(eventType, changes, "Switch chassis", ArgoUtils.timeNow())
                            }
                            catch (BizViolation bv) {
                                LOGGER.error("Violation occured for Unit.. " + bv)
                            }
                        }
                    }
                }
            }
        }
    }
}
