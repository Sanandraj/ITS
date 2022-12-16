/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.business.atoms.LogicalEntityEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.inventory.business.units.Unit
import com.navis.services.ServicesField
import com.navis.services.business.event.Event
import org.apache.log4j.Logger

/**
 *
 * @Author <a href="mailto:mnaresh@weservetech.com">Naresh Kumar M.R.</a>, 25/OCT/2022
 *
 * Requirements : This groovy is used to update  the event as non billable for conatiner types
 *                added in the General reference .
 *
 * @Inclusion Location	: Incorporated as a code extension of the type ENTITY_LIFECYCLE_INTERCEPTION
 *
 *  Load Code Extension to N4:
 1. Go to Administration --> System -->  Code Extension
 2. Click Add (+)
 3. Enter the values as below:
 Code Extension Name:  ITSUpdateEventAsNonBillableELI
 Code Extension Type:  ENTITY_LIFECYCLE_INTERCEPTION
 Groovy Code: Copy and paste the contents of groovy code.
 4. Click Save button
 *
 *  S.No      Modified Date                          Modified By               Jira      Description
 */

class ITSUpdateEventAsNonBillableELI extends AbstractEntityLifecycleInterceptor {
    @Override
    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        Event event = (Event) inEntity._entity
        if (LogicalEntityEnum.UNIT.equals(event.getEventAppliedToClass()) && event.getEventTypeIsBillable()) {
            Unit unit = Unit.hydrate((Serializable) inOriginalFieldChanges.findFieldChange(ServicesField.EVNT_APPLIED_TO_PRIMARY_KEY).getNewValue())
            if (unit != null && unit.getUnitLineOperator() != null && unit.getUnitLineOperator()?.getBzuId()) {
                GeneralReference generalReference = GeneralReference.findUniqueEntryById(ITS, POWER_CONTAINERS, unit.getUnitLineOperator().getBzuId())
                if (generalReference != null && generalReference.getRefValue1() != null) {
                    if (unit.getPrimaryEq() != null && unit.getPrimaryEq().getEqEquipType() != null && generalReference.getRefValue1().toUpperCase().contains(unit.getPrimaryEq().getEqEquipType().getEqtypId())) {
                        inMoreFieldChanges.setFieldChange(ServicesField.EVNT_BILLING_EXTRACT_BATCH_ID, null)
                    }
                }
            }
        }
    }
    private static final String ITS = "ITS";
    private static final String POWER_CONTAINERS = "POWER_CONTAINERS"
    private static final Logger logger = Logger.getLogger(this.class);

}
