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

class ITSUndoMoveCallBack extends AbstractExtensionPersistenceCallback {
    private static Logger LOGGER = Logger.getLogger(ITSUndoMoveCallBack.class);

    @Override
    public void execute(Map inParams, Map inOutResults) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("inParams" + inParams)
        LOGGER.debug("inOutResults" + inOutResults)
        int successCount = 0;
        String errorMsg = "";
        MoveEvent moveEvent = null
        if (inParams != null) {
            List<Serializable> inGkeys = (List<Serializable>) inParams.get("gkeys");
            try {
                if (inGkeys != null && inGkeys.size() > 0) {
                    for (Serializable ufvGkey : inGkeys) {
                        try {
                            UnitFacilityVisit ufv = UnitFacilityVisit.hydrate(ufvGkey)
                            LOGGER.debug("unit" + ufv.getUfvUnit())
                            if (ufv != null) {
                                EventType rampEvent = EventType.findEventType("UNIT_RAMP")
                                EventType deRampEvent = EventType.findEventType("UNIT_DERAMP")
                                if (rampEvent != null || deRampEvent != null) {
                                    String action = inParams.get("action").toString()
                                    if (action != null) {
                                        LOGGER.debug("action" + action)
                                        if (action.equals("Ramp")) {
                                            moveEvent = MoveEvent.getLastMoveEvent(ufv, rampEvent)
                                        } else if (action.equals("DeRamp")) {
                                            moveEvent = MoveEvent.getLastMoveEvent(ufv, deRampEvent)
                                        }
                                    }
                                    if (moveEvent != null) {
                                        MoveInfoBean moveInfoBean = MoveInfoBean.extractMoveInfoFromMoveEvent(moveEvent);
                                        LocPosition oldPos = moveEvent.getMveToPosition();
                                        LocPosition newPos = moveEvent.getMveFromPosition();
                                        Date newDate = null;
                                        if (moveEvent != MoveEvent.getLastMoveEvent(ufv)) {
                                            throw new Exception(
                                                    "[FAILED] " + ufv.getUfvUnit().getUnitId() + " only the last move and be UNDONE")
                                        }
                                        CarrierVisitPhaseEnum ibPhase = ufv.getUfvActualIbCv().getCvVisitPhase()
                                        UfvTransitStateEnum tState;
                                        UnitVisitStateEnum vState;
                                        if (moveInfoBean.getMoveKind() != null && moveInfoBean.getMoveKind() != WiMoveKindEnum.Other) {
                                            String operation = moveInfoBean.getMoveKind().getKey() + "_UNDONE";
                                            if (moveEvent.evntFlexString02 == operation) {
                                                throw new Exception("[FAILED] " + operation + ": " + ufv.getUfvUnit().getUnitId()
                                                        + " as it is already undone")
                                            }
                                            if (LocTypeEnum.YARD.equals(oldPos.getPosLocType()) && !LocTypeEnum.YARD.equals(newPos.getPosLocType())) {
                                                if ([CarrierVisitPhaseEnum.ARCHIVED, CarrierVisitPhaseEnum.CLOSED, CarrierVisitPhaseEnum.DEPARTED].contains(ibPhase)) {
                                                    throw new Exception("[FAILED] " + operation + ": " + ufv.getUfvUnit().getUnitId() + " due to departed carrier")
                                                }
                                            }
                                            if (oldPos.getPosLocType() == LocTypeEnum.YARD && newPos.getPosLocType() !=
                                                    LocTypeEnum.YARD) {
                                                if (ibPhase == CarrierVisitPhaseEnum.CREATED) {
                                                    vState = UnitVisitStateEnum.ADVISED
                                                    tState = UfvTransitStateEnum.S10_ADVISED
                                                }
                                                if (ibPhase == CarrierVisitPhaseEnum.INBOUND ||
                                                        [CarrierVisitPhaseEnum.ARRIVED, CarrierVisitPhaseEnum.WORKING].contains(ibPhase)) {
                                                    vState = UnitVisitStateEnum.ACTIVE
                                                    tState = UfvTransitStateEnum.S20_INBOUND
                                                }

                                                ufv.ufvVisitState = vState
                                                ufv.ufvTransitState = tState
                                                ufv.ufvTimeIn = null
                                                ufv.ufvVisibleInSparcs = (ufv.ufvVisitState == UnitVisitStateEnum.ACTIVE)
                                            }
                                            if (oldPos.getPosLocType() != LocTypeEnum.YARD && newPos.getPosLocType() == LocTypeEnum.YARD) {
                                                ufv.ufvTransitState = UfvTransitStateEnum.S40_YARD
                                                ufv.ufvVisitState = UnitVisitStateEnum.ACTIVE
                                                ufv.ufvTimeOut = null
                                                ufv.ufvVisibleInSparcs = true
                                                ufv.getUfvUnit().setUnitVisitState(UnitVisitStateEnum.ACTIVE);
                                                HibernateApi.getInstance().save(ufv.getUfvUnit());
                                            }
                                            moveInfoBean.setMoveKind(WiMoveKindEnum.Other)
                                            Calendar calendar = Calendar.getInstance(ContextHelper.getThreadUserTimezone());
                                            newDate = calendar.getTime();
                                            moveInfoBean.setTimePut(newDate);

                                            MoveEvent newMoveEvent = MoveEvent.recordMoveEvent(ufv, oldPos, newPos, moveEvent.getMveCarrier(), moveInfoBean, EventEnum.UNIT_RECTIFY)
                                            newMoveEvent.evntFlexString01 = moveEvent.getPrimaryKey()
                                            Event.hydrate(moveEvent.getPrimaryKey()).purge();
                                        }
                                        ufv.ufvLastKnownPosition = newPos
                                        ufv.ufvTimeOfLastMove = newDate
                                        HibernateApi.getInstance().save(ufv);
                                        successCount++
                                    }
                                }
                            }
                        }
                        catch (Exception e) {
                            errorMsg = errorMsg + "\n" + e.message
                        }
                    }
                }
            }
            catch (Exception inEx) {
                LOGGER.debug("Exception " + inEx.toString())
            }
        }
        inOutResults.put("ErrorMsg", errorMsg);
        inOutResults.put("Success", successCount);
    }
}
