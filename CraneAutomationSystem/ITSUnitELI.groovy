/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.EquipClassEnum
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.reference.Equipment
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.business.Roastery
import com.navis.framework.portal.FieldChanges
import com.navis.inventory.InvField
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.rules.EventType
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.*

/**
 * @Author: mailto:sramasamy@weservetech.com, Ramasamy Sathappan; Date: 13/MAY/2022
 *
 * Requirements :For CAS - OnBoardUnitUpdate
 *
 * @Inclusion Location    : Incorporated as a code extension of the type ENTITY_LIFECYCLE_INTERCEPTION
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name:ITSUnitELI
 *     Code Extension Type:ENTITY_LIFECYCLE_INTERCEPTION
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *
 *  S.No    Modified Date        Modified By                                              Jira      Description
 *  1       25/OCT/2022          mailto:annalakshmig@weservetech.com AnnaLakshmi G        IP-14     update the flex field (dwell date) whenever there is change in First Deliverable Date/Line LFD
 */

class ITSUnitELI extends AbstractEntityLifecycleInterceptor {

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        LOGGER.setLevel(Level.DEBUG);
        Unit unit
        UnitFacilityVisit unitFacilityVisit
        if (inEntity._entity instanceof Unit) {
            unit = (Unit) inEntity._entity;
        } else {
            unitFacilityVisit = (UnitFacilityVisit) inEntity._entity;
            unit = unitFacilityVisit.getUfvUnit();
        }

        LOGGER.debug("ITSUnitELI inOriginalFieldChanges" + inOriginalFieldChanges)
        if (unit != null && unit.getUnitEquipment() != null && EquipClassEnum.CONTAINER.equals(unit.getUnitEquipment().getEqClass()) && inOriginalFieldChanges != null && inOriginalFieldChanges.hasFieldChange(InvField.UFV_LAST_KNOWN_POSITION)) {
            LocPosition locPositionOld = (LocPosition) inOriginalFieldChanges.findFieldChange(InvField.UFV_LAST_KNOWN_POSITION).getPriorValue()
            LocPosition locPositionNew = (LocPosition) inOriginalFieldChanges.findFieldChange(InvField.UFV_LAST_KNOWN_POSITION).getNewValue()
            FieldChanges fieldChanges = new FieldChanges();
            if (locPositionOld != null && locPositionNew != null) {
                if (locPositionOld.isWheeled() && locPositionNew.isGrounded()) {

                    EventType eventType = EventType.findEventType("DISMOUNT")

                    fieldChanges.setFieldChange(InvField.UFV_LAST_KNOWN_POSITION, inOriginalFieldChanges.findFieldChange(InvField.UFV_LAST_KNOWN_POSITION).getPriorValue(),
                            inOriginalFieldChanges.findFieldChange(InvField.UFV_LAST_KNOWN_POSITION).getNewValue())
                    servicesManager.recordEvent(eventType, "Recorded by groovy", null, null, unit, fieldChanges)
                } else if (locPositionOld.isGrounded() && locPositionNew.isWheeled()) {
                    EventType eventType = EventType.findEventType("MOUNT")
                    fieldChanges.setFieldChange(InvField.UFV_LAST_KNOWN_POSITION, inOriginalFieldChanges.findFieldChange(InvField.UFV_LAST_KNOWN_POSITION).getPriorValue(),
                            inOriginalFieldChanges.findFieldChange(InvField.UFV_LAST_KNOWN_POSITION).getNewValue())
                    Equipment equipment = null
                    if (unit.getUnitCurrentlyAttachedChassisId() != null) {

                        equipment = Equipment.findEquipment(unit.getUnitCurrentlyAttachedChassisId())
                    }
                    servicesManager.recordEvent(eventType, "Recorded by groovy", null, null, unit, fieldChanges, null, equipment)
                }
            }


        }
        // to update the flex field (dwell date) whenever there is change in First Deliverable Date/Line LFD
        if (unitFacilityVisit != null && inOriginalFieldChanges != null && (inOriginalFieldChanges.hasFieldChange(InvField.UFV_FLEX_DATE01) || inOriginalFieldChanges.hasFieldChange(InvField.UFV_LINE_LAST_FREE_DAY))) {
            Date date = null
            // Date firstDeliverableDate = (String) inOriginalFieldChanges.findFieldChange(InvField.UFV_FLEX_DATE01).getNewValue()
            String lineLFD = unitFacilityVisit.getUfvCalculatedLineStorageLastFreeDay()
            if (!StringUtils.isEmpty(lineLFD)) {
                date = getLfdDate(lineLFD)
                if (date) {
                    LocalDateTime lcDate = date.plus(1).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                    ZonedDateTime zonedDateTime = lcDate.withHour(3).withMinute(0).withSecond(0).atZone(ZoneOffset.systemDefault());
                    Instant instant = zonedDateTime.toInstant();
                    date = Date.from(instant);
                }
            }
            inMoreFieldChanges.setFieldChange(InvField.UFV_FLEX_DATE02, date)


        }

        try {
            Map map = new HashMap();
            map.put(T__IN_ENTITY, inEntity);
            map.put(T__IN_TRIGGER_TYPE, T__ON_UPDATE);
            map.put(T__IN_ORIGINAL_FIELD_CHANGES, inOriginalFieldChanges);
            map.put(T__IN_MORE_FIELD_CHANGES, inMoreFieldChanges);

            Object object = getLibrary(T__ON_BOARD_UNIT_UPDATE);
            object.execute(map);

        } catch (Exception e) {
            LOGGER.error("Exception in onUpdate: " + e.getMessage());
        }
    }

    private static Date getLfdDate(String dt) throws ParseException {
        Calendar cal = Calendar.getInstance()
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MMM-dd")
        if (dt.endsWith("!")) {
            dt = dt.replaceAll("!", "")
        }
        if (dt.contains("no")) {
            return null
        }
        cal.setTime(dateFormat.parse(dt))
        return cal.getTime()
    }
    private static final String T__IN_ENTITY = "inEntity";
    private static final String T__IN_TRIGGER_TYPE = "inTriggerType";
    private static final String T__IN_ORIGINAL_FIELD_CHANGES = "inOriginalFieldChanges";
    private static final String T__IN_MORE_FIELD_CHANGES = "inMoreFieldChanges";
    private static final String T__ON_UPDATE = "onUpdate";
    private final String T__ON_BOARD_UNIT_UPDATE = "OnboardUnitUpdate";

    private ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
    private static final String LIBRARY = "ITSAdaptor";
    private static  Logger LOGGER = Logger.getLogger(ITSUnitELI.class);

}
