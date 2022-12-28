/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */
import com.navis.apex.business.api.ApexManager
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.reference.Chassis
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.FieldChanges
import com.navis.inventory.InventoryBizMetafield
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.api.EventManager
import com.navis.services.business.event.Event
import com.navis.services.business.rules.EventType
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 *
 * @Author: mailto:mharikumar@weservetech.com,Harikumar M; Date:16/09/2022
 *
 * Requirements : 6-11,This code extension is used to undo a move like Rail Ramp/DeRamp
 also stores the previous deleted move in a new UNDO event after successfully deleted of last move.
 *
 * @Inclusion Location	: Incorporated as a code extension of the type TRANSACTED_BUSINESS_FUNCTION
 *
 *  Load Code Extension to N4:
 1. Go to Administration --> System -->  Code Extension
 2. Click Add (+)
 3. Enter the values as below:
 Code Extension Name:  ITSUndoMoveCallBack
 Code Extension Type:  TRANSACTED_BUSINESS_FUNCTION
 Groovy Code: Copy and paste the contents of groovy code.
 4. Click Save button
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */


class ITSUndoMoveCallBack extends AbstractExtensionPersistenceCallback {
    private static Logger LOGGER = Logger.getLogger(ITSUndoMoveCallBack.class);

    @Override
    public void execute(Map inParams, Map inOutResults) {
        //LOGGER.setLevel(Level.DEBUG)
        int successCount = 0;
        String errorMessage = "";
        EventManager eventManager = (EventManager) Roastery.getBean(EventManager.BEAN_ID)

        if (inParams != null) {
            String action = inParams.get("action").toString()
            List<Serializable> inGkeys = (List<Serializable>) inParams.get("gkeys");
            if (action != null && inGkeys != null && inGkeys.size() > 0) {
                ApexManager apexManager = (ApexManager) Roastery.getBean("apexManager");
                FieldChanges fieldChanges = new FieldChanges();

                try {
                    fieldChanges.setFieldChange(UnitField.UNIT_VISIT_STATE, UnitVisitStateEnum.ACTIVE);
                    fieldChanges.setFieldChange(InventoryBizMetafield.ERASE_HISTORY,Boolean.TRUE)

                    if (action.equals("DeRamp")) {
                        fieldChanges.setFieldChange(UnitField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S20_INBOUND)

                        apexManager.undoDeRampedUfv(inGkeys.toArray(), fieldChanges)
                    } else if (action.equals("Ramp")) {
                        fieldChanges.setFieldChange(UnitField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD)
                        UnitFacilityVisit ufv = null;
                        UnitFacilityVisit chassisUfv =null;
                        Event event = null;
                        LOGGER.debug("Executed undo Ramp");
                        try {
                            ufv = UnitFacilityVisit.hydrate(inGkeys.get(0))
                            if (ufv != null) {
                                EventType unitDismountEvnt = EventType.findEventType(EventEnum.UNIT_DISMOUNT.getKey());
                                if (unitDismountEvnt != null) {
                                    event = eventManager.getMostRecentEventByType(unitDismountEvnt, ufv.getUfvUnit());
                                    if (event != null) {
                                        String notes = event?.getEventNote() != null ? event.getEventNote() : null
                                        if (notes != null) {
                                            String chassisNum = StringUtils.substringBefore(notes, 'dismounted')
                                            LOGGER.debug("Got chassis number "+chassisNum);
                                            Chassis chassis = Chassis.findChassis(chassisNum?.trim())
                                            if (chassis != null) {
                                                Unit unit = unitFinder.findActiveUnit(ContextHelper.getThreadComplex(), chassis)
                                                if (unit != null) {
                                                    chassisUfv = unit?.getUnitActiveUfvNowActive()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            apexManager.undoRampedUfv(inGkeys.toArray(), fieldChanges)
                            LOGGER.debug("Got chassis ufv "+chassisUfv);
                            if (chassisUfv != null && UfvTransitStateEnum.S40_YARD.equals(chassisUfv.getUfvTransitState())) {
                                try {
                                    ufv?.getUfvUnit()?.attachCarriage(chassisUfv?.getUfvUnit()?.getUnitEquipment())
                                    HibernateApi.getInstance().save(ufv.getUfvUnit());
                                    EventType unitMountEvnt = EventType.findEventType(EventEnum.UNIT_MOUNT.getKey());
                                    if (unitMountEvnt != null) {
                                        Event evnt = eventManager.getMostRecentEventByType(unitMountEvnt, ufv.getUfvUnit());
                                        if (evnt != null && event!=null) {
                                            evnt.setEventTypeIsBillable(false)
                                            evnt.purge()
                                            event.purge()
                                        }
                                    }
                                } catch (Exception e) {
                                    LOGGER.debug("error while attaching chassis to unit" + e)
                                }
                            }

                        }  catch (Exception e) {
                            errorMessage = errorMessage + "\n" + e.message
                        }

                    }
                    if (errorMessage == null || errorMessage.isEmpty()) {
                        successCount++;
                    }
                } catch (Exception e) {
                    errorMessage = errorMessage + "\n" + e.message
                }
            }
        }
        inOutResults.put("ErrorMsg", errorMessage);
        inOutResults.put("Success", successCount);
        LOGGER.debug("Success count "+ Integer.toString(successCount));
    }

    UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);

}