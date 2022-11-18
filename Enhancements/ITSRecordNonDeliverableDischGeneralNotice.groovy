/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.LocPosition
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.portal.FieldChanges
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import org.apache.log4j.Logger

/**
 * @Author: mailto:uaarthi@weservetech.com, Aarthi U; Date: 17/08/2022
 *
 *  Requirements:Record a event UNIT_NON_DELIVERABLE_DISCH when the Unit is discharged to a Non deliverable spot.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *   1. Go to Administration --> System -->  Code Extension
 *   2. Click Add (+)
 *   3. Enter the values as below:
 Code Extension Name:  ITSRecordNonDeliverableDischGeneralNotice
 Code Extension Type:  GENERAL_NOTICE_CODE_EXTENSION
 Groovy Code: Copy and paste the contents of groovy code.
 *   4. Click Save button
 *
 * @Set up General Notice for event type "UNIT_DISCH/ UNIT_DERAMP" on Unit Entity then execute this code extension (ITSRecordNonDeliverableDischGeneralNotice).
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */


class ITSRecordNonDeliverableDischGeneralNotice extends AbstractGeneralNoticeCodeExtension {

    @Override
    void execute(GroovyEvent inGroovyEvent) {
        Unit unit = (Unit) inGroovyEvent.getEntity()
        if (!unit) {
            return;
        }
        if (unit.getUnitActiveUfvNowActive()) {
            UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
            if (!ufv) {
                return;
            }
            LocPosition position = ufv.getUfvLastKnownPosition()
            if (position != null && position.getBlockName() != null && !isBlockDeliverable(position.getBlockName())) {
                EventType yardRehandle = EventType.findEventType("UNIT_NON_DELIVERABLE_DISCH")
                if (yardRehandle != null) {
                    FieldChanges fc = new FieldChanges()
                    fc.setFieldChange(UnitField.POS_SLOT, position.getPosSlot())
                    unit.recordEvent(yardRehandle, fc, "Unit discharged to Deliverable location", ArgoUtils.timeNow())

                }
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

    private static Logger log = Logger.getLogger(ITSRecordNonDeliverableDischGeneralNotice.class)
}
