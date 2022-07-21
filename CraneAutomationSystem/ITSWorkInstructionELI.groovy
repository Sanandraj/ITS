package CraneAutomationSystem

import com.navis.argo.business.atoms.WiMoveStageEnum
import com.navis.argo.business.model.LocPosition
import com.navis.extension.handler.business.entityinterception.DefaultEntityView
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.persistence.Entity
import com.navis.framework.portal.FieldChanges
import com.navis.inventory.MovesField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.moves.WorkInstruction
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.business.units.UnitYardVisit
import org.apache.log4j.Level
import org.apache.log4j.Logger


/**
 * @author <a href="mailto:sramasamy@weservetech.com"> Ramasamy Sathappan</a>
 * @since 05-May-2022
 * For CAS - CraneWorkListUpdate
 * For Drayman gate message - added draymanTruckMessage() method
 *
 * */
class ITSWorkInstructionELI  extends AbstractEntityLifecycleInterceptor {

    @Override
    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        craneWorkList(inEntity, inOriginalFieldChanges, inMoreFieldChanges, T__ON_CREATE);
    }

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        craneWorkList(inEntity, inOriginalFieldChanges, inMoreFieldChanges, T__ON_UPDATE);
        draymanTruckMessage(inEntity, inOriginalFieldChanges, inMoreFieldChanges, T__ON_UPDATE);
    }

    @Override
    void preDelete(Entity inEntity) {
        FieldChanges fieldChanges = new FieldChanges();
        craneWorkList(new DefaultEntityView(inEntity), fieldChanges, fieldChanges, T__ON_DELETE);
    }

    void craneWorkList(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges, String inType) {
        Map map = new HashMap();
        map.put(T__IN_ENTITY, inEntity);
        map.put(T__IN_TRIGGER_TYPE, inType);
        map.put(T__IN_ORIGINAL_FIELD_CHANGES, inOriginalFieldChanges);
        map.put(T__IN_MORE_FIELD_CHANGES, inMoreFieldChanges);

        Object object = getLibrary(T__CRANE_WORK_LIST_UPDATE);
        object.execute(map);
    }


    void draymanTruckMessage(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges, String inType) {
        try {
            LOGGER.setLevel(Level.DEBUG)
            logMsg("inOriginalFieldChanges: "+inOriginalFieldChanges);

            Object library = getLibrary(LIBRARY);
            logMsg("library: "+library);
            if (library != null) {
                /*if (inOriginalFieldChanges.hasFieldChange(MovesField.WI_MOVE_STAGE) || inOriginalFieldChanges.hasFieldChange(MovesField.WI_POSITION)) {
                    UnitYardVisit unitYardVisit = inEntity.getField(MovesField.WI_UYV);
                    UnitFacilityVisit ufv = unitYardVisit? unitYardVisit.getUyvUfv() : null;
                    Unit unit = ufv? ufv.getUfvUnit() : null;
                    if (unit == null)
                        return;*/

                    // If move stage changed from PLANNED to COMPLETE
                    /*if (inOriginalFieldChanges.hasFieldChange(MovesField.WI_MOVE_STAGE)) {
                        WiMoveStageEnum moveStagePrior = inOriginalFieldChanges.findFieldChange(MovesField.WI_MOVE_STAGE).getPriorValue();
                        WiMoveStageEnum moveStageNew = inOriginalFieldChanges.findFieldChange(MovesField.WI_MOVE_STAGE).getNewValue();
                        logMsg("WI_MOVE_STAGE - moveStagePrior: " + moveStagePrior + ", moveStageNew: " + moveStageNew);

                        if (moveStagePrior == WiMoveStageEnum.PLANNED && moveStageNew == WiMoveStageEnum.COMPLETE) {
                            library.prepareAndPushMessageForPositionChange(unit, null);
                        }
                    }*/

                    // On WI position update for planned ufv. Send message if the transit state is either inbound, Ecin or Ecout
                    if (inOriginalFieldChanges.hasFieldChange(MovesField.WI_POSITION)) {
                        logMsg("WI_POSITION change ");
                        UnitYardVisit unitYardVisit = inEntity.getField(MovesField.WI_UYV);
                        UnitFacilityVisit ufv = unitYardVisit? unitYardVisit.getUyvUfv() : null;
                        Unit unit = ufv? ufv.getUfvUnit() : null;
                        if (unit == null)
                            return;

                        logMsg("UFV : "+ufv);
                        LocPosition locPosNew = inOriginalFieldChanges.findFieldChange(MovesField.WI_POSITION).getNewValue();
                        WorkInstruction wi = (WorkInstruction) inEntity._entity;
                        WiMoveStageEnum wiMoveStage = wi? wi.getWiMoveStage() : null;
                        logMsg("wiMoveStage: " + wiMoveStage);
                        if (WiMoveStageEnum.PLANNED == wiMoveStage) {
                            UfvTransitStateEnum transitState = ufv.getUfvTransitState();
                            if (UfvTransitStateEnum.S20_INBOUND == transitState
                                || UfvTransitStateEnum.S30_ECIN == transitState
                                || UfvTransitStateEnum.S50_ECOUT == transitState) {
                                library.prepareAndPushMessageForPositionChange(unit, locPosNew);
                            }
                        }
                    }
                //}
            }

        } catch (Exception e) {
            LOGGER.error("Exception in draymanTruckMessage : "+e.getMessage());
        }
    }

    private void logMsg(Object inMsg) {
        LOGGER.debug(inMsg);
    }

    private static final String T__IN_ENTITY = "inEntity";
    private static final String T__IN_TRIGGER_TYPE = "inTriggerType";
    private static final String T__IN_ORIGINAL_FIELD_CHANGES = "inOriginalFieldChanges";
    private static final String T__IN_MORE_FIELD_CHANGES = "inMoreFieldChanges";
    private static final String T__ON_CREATE = "onCreate";
    private static final String T__ON_UPDATE = "onUpdate";
    private static final String T__ON_DELETE = "onDelete";
    private final String T__CRANE_WORK_LIST_UPDATE = "CraneWorkListUpdate";

    private static final String LIBRARY = "ITSDraymanGateAdaptor";

    private static final Logger LOGGER = Logger.getLogger(ITSWorkInstructionELI.class);
}
