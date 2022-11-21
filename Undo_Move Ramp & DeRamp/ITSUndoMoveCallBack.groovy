import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.WiMoveKindEnum
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.reference.Chassis
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.api.RectifyParms
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.moves.MoveEvent
import com.navis.inventory.business.units.MoveInfoBean
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.api.EventManager
import com.navis.services.business.event.Event
import com.navis.services.business.rules.EventType
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author <ahref="mailto:mharikumar@weservetech.com"  >  Harikumar M</a>,
 * Date : 16/Sep/2022
 * Descreption: This code extension is used to undo a move like Rail Ramp/DeRamp
 also stores the previous deleted move in a new UNDO event after successfully deleted of last move.
 */


class ITSUndoMoveCallBack extends AbstractExtensionPersistenceCallback {
    private static Logger LOGGER = Logger.getLogger(ITSUndoMoveCallBack.class);

    @Override
    public void execute(Map inParams, Map inOutResults) {
        //LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("inParams" + inParams)
        int successCount = 0;
        String errorMessage = "";
        MoveEvent moveEvent = null
        EventManager eventManager = (EventManager) Roastery.getBean(EventManager.BEAN_ID)

        if (inParams != null) {
            List<Serializable> inGkeys = (List<Serializable>) inParams.get("gkeys");
            try {
                if (inGkeys != null && inGkeys.size() > 0) {
                    for (Serializable ufvGkey : inGkeys) {
                        try {
                            UnitFacilityVisit ufv = UnitFacilityVisit.hydrate(ufvGkey)
                            EventType rampEvent = EventType.findEventType("UNIT_RAMP")
                            EventType deRampEvent = EventType.findEventType("UNIT_DERAMP")
                            if (rampEvent != null && deRampEvent != null) {
                                String action = inParams.get("action").toString()
                                if (action != null) {
                                    if (action.equals("Ramp")) {
                                        moveEvent = MoveEvent.getLastMoveEvent(ufv, rampEvent)
                                    } else if (action.equals("DeRamp")) {
                                        moveEvent = MoveEvent.getLastMoveEvent(ufv, deRampEvent)
                                    }
                                }
                                if (ufv != null) {
                                    if ((ufv.getUfvTransitState().equals(UfvTransitStateEnum.S40_YARD) && action.equals("DeRamp"))
                                            || (ufv.getUfvTransitState().equals(UfvTransitStateEnum.S60_LOADED) && action.equals("Ramp"))) {

                                        if (moveEvent != null) {
                                            MoveInfoBean infoFromMoveEvent = MoveInfoBean.extractMoveInfoFromMoveEvent(moveEvent);
                                            LocPosition toPosition = moveEvent?.getMveToPosition();
                                            LocPosition fromPosition = moveEvent?.getMveFromPosition();
                                            Date currentTime = null;
                                            if (moveEvent != MoveEvent.getLastMoveEvent(ufv)) {
                                                throw new Exception(
                                                        "[FAILED] " + ufv.getUfvUnit().getUnitId() + " Last move doesn't match the UNDO move")
                                            }
                                            CarrierVisitPhaseEnum ibVisitPhase = ufv?.getUfvActualIbCv()?.getCvVisitPhase()
                                            CarrierVisitPhaseEnum obVisitPhase = ufv?.getUfvActualObCv()?.getCvVisitPhase()
                                            UfvTransitStateEnum transitState = null;
                                            UnitVisitStateEnum visitState = null;
                                            if (infoFromMoveEvent.getMoveKind() != null && infoFromMoveEvent.getMoveKind() != WiMoveKindEnum.Other) {
                                                String moveKind = infoFromMoveEvent.getMoveKind().getKey() + "_UNDONE";
                                                if (LocTypeEnum.YARD.equals(toPosition.getPosLocType()) && !LocTypeEnum.YARD.equals(fromPosition.getPosLocType())) {
                                                    if (ibVisitPhase != null) {
                                                        if ([CarrierVisitPhaseEnum.ARCHIVED, CarrierVisitPhaseEnum.CLOSED, CarrierVisitPhaseEnum.DEPARTED].contains(ibVisitPhase)) {
                                                            throw new Exception("[FAILED] " + moveKind + ": " + ufv.getUfvUnit().getUnitId() + " Carrier already departed")
                                                        }
                                                    }
                                                }
                                                if (toPosition.getPosLocType() == LocTypeEnum.YARD && fromPosition.getPosLocType() !=
                                                        LocTypeEnum.YARD) {
                                                    if (ibVisitPhase == CarrierVisitPhaseEnum.CREATED) {
                                                        visitState = UnitVisitStateEnum.ADVISED
                                                        transitState = UfvTransitStateEnum.S10_ADVISED
                                                    }
                                                    if (ibVisitPhase != null) {
                                                        if (ibVisitPhase == CarrierVisitPhaseEnum.INBOUND ||
                                                                [CarrierVisitPhaseEnum.ARRIVED, CarrierVisitPhaseEnum.WORKING].contains(ibVisitPhase)) {
                                                            visitState = UnitVisitStateEnum.ACTIVE
                                                            transitState = UfvTransitStateEnum.S20_INBOUND
                                                        }
                                                    }

                                                    ufv.setUfvVisitState(visitState)
                                                    ufv.setUfvTransitState(transitState)
                                                    ufv.setUfvTimeIn(null)
                                                    if (ufv.ufvVisitState == UnitVisitStateEnum.ACTIVE) {
                                                        ufv.setUfvVisibleInSparcs(true)
                                                    }
                                                    EventType unitRailEvnt = EventType.findEventType(EventEnum.UNIT_IN_RAIL.getKey());
                                                    if (unitRailEvnt != null) {
                                                        Event event = eventManager.getMostRecentEventByType(unitRailEvnt, ufv?.getUfvUnit());
                                                        if (event != null) {
                                                            event.purge();
                                                        }
                                                    }
                                                }
                                                if (!LocTypeEnum.YARD.equals(toPosition.getPosLocType()) && LocTypeEnum.YARD.equals(fromPosition.getPosLocType())) {
                                                    if (obVisitPhase != null) {
                                                        if ([CarrierVisitPhaseEnum.ARCHIVED, CarrierVisitPhaseEnum.CLOSED, CarrierVisitPhaseEnum.DEPARTED].contains(obVisitPhase)) {
                                                            throw new Exception("[FAILED] " + moveKind + ": " + ufv.getUfvUnit().getUnitId() + " Carrier already departed")
                                                        }
                                                    }
                                                }
                                                if (toPosition.getPosLocType() != LocTypeEnum.YARD && fromPosition.getPosLocType() == LocTypeEnum.YARD) {
                                                    ufv.setUfvTransitState(UfvTransitStateEnum.S40_YARD)
                                                    ufv.setUfvVisitState(UnitVisitStateEnum.ACTIVE)
                                                    ufv.setUfvTimeOut(null)
                                                    ufv.setUfvVisibleInSparcs(true)
                                                    EventType unitDismountEvnt = EventType.findEventType(EventEnum.UNIT_DISMOUNT.getKey());
                                                    if (unitDismountEvnt != null) {
                                                        Event event = eventManager.getMostRecentEventByType(unitDismountEvnt, ufv.getUfvUnit());
                                                        if (event != null) {
                                                            String notes = event?.getEventNote() != null ? event.getEventNote() : null
                                                            if (notes != null) {
                                                                String no = StringUtils.substringBefore(notes, 'dismounted')
                                                                Chassis chassis = Chassis.findChassis(no?.trim())
                                                                if (chassis != null) {
                                                                    Unit unit = unitFinder.findActiveUnit(ContextHelper.getThreadComplex(), chassis)
                                                                    if (unit != null) {
                                                                        UnitFacilityVisit chassisUfv = unit?.getUnitActiveUfvNowActive()
                                                                        if (chassisUfv != null && UfvTransitStateEnum.S40_YARD.equals(chassisUfv.getUfvTransitState())) {
                                                                            try {
                                                                                ufv?.getUfvUnit()?.attachCarriage(unit?.getUnitEquipment())
                                                                                //event.purge();
                                                                            } catch (Exception e) {
                                                                                LOGGER.debug("error while attaching chassis to unit" + e)
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    HibernateApi.getInstance().save(ufv.getUfvUnit());
                                                }
                                                infoFromMoveEvent.setMoveKind(WiMoveKindEnum.Other)
                                                Calendar calendar = Calendar.getInstance(ContextHelper.getThreadUserTimezone());
                                                currentTime = calendar.getTime();
                                                infoFromMoveEvent.setTimePut(currentTime);

                                                MoveEvent.recordMoveEvent(ufv, toPosition, fromPosition, moveEvent.getMveCarrier(), infoFromMoveEvent, EventEnum.UNIT_RECTIFY)
                                                Event.hydrate(moveEvent.getPrimaryKey()).purge();
                                            }
                                            ufv.setUfvLastKnownPosition(fromPosition)
                                            ufv.setUfvTimeOfLastMove(currentTime)
                                            HibernateApi.getInstance().save(ufv);
                                            successCount++
                                        }
                                    } else {
                                        throw new Exception(
                                                "[FAILED] Cannot UNDO event for unit " + ufv.getUfvUnit().getUnitId() + " with T-state " + ufv.getUfvTransitState().getKey().substring(4))
                                    }
                                }
                            }
                        }
                        catch (Exception e) {
                            errorMessage = errorMessage + "\n" + e.message
                        }
                    }
                }
            }
            catch (Exception inEx) {
                LOGGER.debug("Exception " + inEx.toString())
            }
        }
        inOutResults.put("ErrorMsg", errorMessage);
        inOutResults.put("Success", successCount);
    }

    UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);

}