/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/

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
import org.apache.log4j.Logger

/*
 * @Author: mailto:mharikumar@weservetech.com, Harikumar M; Date: 22/09/2022
 *
 *  Requirements: To detach the original chassis from unit when truck received at ingate with own chassis.
 *
 *  @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSValidateFlipChassisTaskInterceptor
 *     Code Extension Type: GATE_TASK_INTERCEPTOR
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *	@Setup Customize the code to RejectTruckBanned biz task in Ingate stage Tran type DI
 *
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */

class ITSValidateFlipChassisTaskInterceptor extends AbstractGateTaskInterceptor {

    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        TruckTransaction tran = inWfCtx.getTran()
        Container ctrId
        Unit unit
        if (tran != null) {
            String chsNbr = tran.getTranChsNbr() != null ? tran.getTranChsNbr() : tran.getTranChsNbrAssigned()
            String containerNbr = tran.getTranCtrNbr() != null ? tran.getTranCtrNbr() : tran.getTranCtrNbrAssigned()
            UnitFinder unitFinder = Roastery.getBean(UnitFinder.BEAN_ID)
            if (containerNbr != null && tran.getTranChsIsOwners()) {
                ctrId = Container.findContainer(containerNbr)
                if (ctrId != null) {
                    unit = unitFinder.findActiveUnit(ContextHelper.getThreadComplex(), ctrId)
                    if (unit != null && chsNbr != null) {
                        String relatedUnit = unit?.getUnitRelatedUnit()?.getUnitId()
                        if (relatedUnit != null && !chsNbr.equals(relatedUnit)) {
                            try {
                                unit.detachCarriage("Carriage detached ${relatedUnit}")
                                EventType eventType = EventType.findEventType("UNIT_SWITCH_CHASSIS")
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
    private static final Logger LOGGER = Logger.getLogger(ITSValidateFlipChassisTaskInterceptor.class)
}
