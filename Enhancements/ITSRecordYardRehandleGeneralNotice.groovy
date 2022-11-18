/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


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
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import org.apache.log4j.Logger

/*
     *
     *  @Author: mailto:uaarthi@weservetech.com, Aarthi U; Date:17/08/2022
     *
     * Requirements : Billing 7-3 Yard rehandle fee.Record a event UNIT_YARD_REHANDLE when the Unit is moved from a Undeliverable spot to a Deliverable spot.
     *
     * @Inclusion Location	: Incorporated as a code extension of the type GENERAL_NOTICE_CODE_EXTENSION.
     *
     *  Load Code Extension to N4:
            1. Go to Administration --> System -->  Code Extension
            2. Click Add (+)
            3. Enter the values as below:
                Code Extension Name:  ITSRecordYardRehandleGeneralNotice
                Code Extension Type:  GENERAL_NOTICE_CODE_EXTENSION
               Groovy Code: Copy and paste the contents of groovy code.
            4. Click Save button

    * @Set up General Notice for event types "UNIT_YARD_MOVE,UNIT_POSITION_CORRECTION,UNIT_YARD_SHIFT" on Unit Entity then execute this code extension (ITSRecordYardRehandleGeneralNotice).
    *
    *  S.No    Modified Date   Modified By     Jira      Description
    *
 */


class ITSRecordYardRehandleGeneralNotice extends AbstractGeneralNoticeCodeExtension {
    @Override
    void execute(GroovyEvent inGroovyEvent) {
        super.execute(inGroovyEvent)

        Unit unit = (Unit) inGroovyEvent.getEntity()
        if (unit == null) {
            return;
        }

        String prevPosition = inGroovyEvent.getPreviousPropertyAsString("PositionFull")

        if (unit.getUnitActiveUfvNowActive() && prevPosition) {
            UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
            if (ufv == null) {
                return;
            }
            LocPosition position = ufv.getUfvLastKnownPosition()

            LocPosition prevPositionLoc = null
            if (prevPosition != null) {
                if (prevPosition.indexOf('Y-PIERG-') != -1) {
                    prevPosition = prevPosition.replaceAll('Y-PIERG-', '')
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
            if (blockName != null && isBlockDeliverable(blockName) && prevblockName && (!isBlockDeliverable(prevblockName))) {
                EventType yardRehandle = EventType.findEventType("UNIT_YARD_REHANDLE")
                if (yardRehandle != null) {
                    FieldChanges fc = new FieldChanges()
                    fc.setFieldChange(UnitField.POS_SLOT, prevPosition, position.getPosSlot())
                    unit.recordEvent(yardRehandle, fc, "Unit moved from Undeliverable to Deliverable location", ArgoUtils.timeNow())
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

    private static final Logger log = Logger.getLogger(this.class)
}
