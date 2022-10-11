import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.WiMoveKindEnum
import com.navis.argo.business.model.LocPosition
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.moves.MoveEvent
import com.navis.inventory.business.units.MoveInfoBean
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.navis.services.business.rules.EventType
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
        if (inParams != null) {
            List<Serializable> inGkeys = (List<Serializable>) inParams.get("gkeys");
            try {
                if (inGkeys != null && inGkeys.size() > 0) {
                    for (Serializable ufvGkey : inGkeys) {
                        try {
                            UnitFacilityVisit ufv = UnitFacilityVisit.hydrate(ufvGkey)
                            EventType rampEvent = EventType.findEventType("UNIT_RAMP")
                            EventType deRampEvent = EventType.findEventType("UNIT_DERAMP")
                            if (rampEvent != null || deRampEvent != null) {
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
                                            LocPosition toPosition = moveEvent.getMveToPosition();
                                            LocPosition fromPosition = moveEvent.getMveFromPosition();
                                            Date currentTime = null;
                                            if (moveEvent != MoveEvent.getLastMoveEvent(ufv)) {
                                                throw new Exception(
                                                        "[FAILED] " + ufv.getUfvUnit().getUnitId() + " Last move doesn't match the UNDO move")
                                            }
                                            CarrierVisitPhaseEnum ibVisitPhase = ufv.getUfvActualIbCv().getCvVisitPhase()
                                            UfvTransitStateEnum transitState;
                                            UnitVisitStateEnum visitState;
                                            if (infoFromMoveEvent.getMoveKind() != null && infoFromMoveEvent.getMoveKind() != WiMoveKindEnum.Other) {
                                                String moveKind = infoFromMoveEvent.getMoveKind().getKey() + "_UNDONE";
                                                if (LocTypeEnum.YARD.equals(toPosition.getPosLocType()) && !LocTypeEnum.YARD.equals(fromPosition.getPosLocType())) {
                                                    if ([CarrierVisitPhaseEnum.ARCHIVED, CarrierVisitPhaseEnum.CLOSED, CarrierVisitPhaseEnum.DEPARTED].contains(ibVisitPhase)) {
                                                        throw new Exception("[FAILED] " + moveKind + ": " + ufv.getUfvUnit().getUnitId() + " Carrier already departed")
                                                    }
                                                }
                                                if (toPosition.getPosLocType() == LocTypeEnum.YARD && fromPosition.getPosLocType() !=
                                                        LocTypeEnum.YARD) {
                                                    if (ibVisitPhase == CarrierVisitPhaseEnum.CREATED) {
                                                        visitState = UnitVisitStateEnum.ADVISED
                                                        transitState = UfvTransitStateEnum.S10_ADVISED
                                                    }
                                                    if (ibVisitPhase == CarrierVisitPhaseEnum.INBOUND ||
                                                            [CarrierVisitPhaseEnum.ARRIVED, CarrierVisitPhaseEnum.WORKING].contains(ibVisitPhase)) {
                                                        visitState = UnitVisitStateEnum.ACTIVE
                                                        transitState = UfvTransitStateEnum.S20_INBOUND
                                                    }

                                                    ufv.setUfvVisitState(visitState)
                                                    ufv.setUfvTransitState(transitState)
                                                    ufv.setUfvTimeIn(null)
                                                    if (ufv.ufvVisitState == UnitVisitStateEnum.ACTIVE) {
                                                        ufv.setUfvVisibleInSparcs(true)
                                                    }

                                                }
                                                if (toPosition.getPosLocType() != LocTypeEnum.YARD && fromPosition.getPosLocType() == LocTypeEnum.YARD) {
                                                    ufv.setUfvTransitState(UfvTransitStateEnum.S40_YARD)
                                                    ufv.setUfvVisitState(UnitVisitStateEnum.ACTIVE)
                                                    ufv.setUfvTimeOut(null)
                                                    ufv.setUfvVisibleInSparcs(true)
                                                    ufv.getUfvUnit().setUnitVisitState(UnitVisitStateEnum.ACTIVE);
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
}