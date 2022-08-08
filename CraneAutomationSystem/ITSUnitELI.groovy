package CraneAutomationSystem

import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.inventory.InvField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @author <a href="mailto:sramasamy@weservetech.com"> Ramasamy Sathappan</a>
 * @since 13-May-2022
 * For CAS - OnBoardUnitUpdate
 *
 * */
class ITSUnitELI extends AbstractEntityLifecycleInterceptor {

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        LOGGER.setLevel(Level.DEBUG);

        try {
            Map map = new HashMap();
            map.put(T__IN_ENTITY, inEntity);
            map.put(T__IN_TRIGGER_TYPE, T__ON_UPDATE);
            map.put(T__IN_ORIGINAL_FIELD_CHANGES, inOriginalFieldChanges);
            map.put(T__IN_MORE_FIELD_CHANGES, inMoreFieldChanges);

            Object object = getLibrary(T__ON_BOARD_UNIT_UPDATE);
            object.execute(map);

            /*if (inOriginalFieldChanges.hasFieldChange(InvField.UFV_TRANSIT_STATE) &&
                    (UfvTransitStateEnum.S30_ECIN == inOriginalFieldChanges.findFieldChange(InvField.UFV_TRANSIT_STATE).getNewValue() ||
                            UfvTransitStateEnum.S70_DEPARTED == inOriginalFieldChanges.findFieldChange(InvField.UFV_TRANSIT_STATE).getNewValue())) {

                Unit unit;
                UnitFacilityVisit unitFacilityVisit;
                if (inEntity._entity instanceof Unit) {
                    unit = (Unit) inEntity._entity;
                    unitFacilityVisit = unit.getUnitActiveUfvNowActive();

                } else {
                    unitFacilityVisit = (UnitFacilityVisit) inEntity._entity;
                    unit = unitFacilityVisit.getUfvUnit();
                }

                if (UfvTransitStateEnum.S30_ECIN == inOriginalFieldChanges.findFieldChange(InvField.UFV_TRANSIT_STATE).getNewValue() ||
                        (UfvTransitStateEnum.S70_DEPARTED == inOriginalFieldChanges.findFieldChange(InvField.UFV_TRANSIT_STATE).getNewValue()
                                && unitFacilityVisit.getUfvObCv()
                                && LocTypeEnum.TRUCK == unitFacilityVisit.getUfvObCv().getCvCarrierMode())) {

                    LOGGER.debug("send drayman message for unit : " + unit);
                    Object library = getLibrary(LIBRARY);
                    library.prepareAndPushMessageForPositionChange(unit, unitFacilityVisit.getUfvLastKnownPosition());
                }
            }*/
        } catch (Exception e) {
            LOGGER.error("Exception in onUpdate: " + e.getMessage());
        }
    }


    private static final String T__IN_ENTITY = "inEntity";
    private static final String T__IN_TRIGGER_TYPE = "inTriggerType";
    private static final String T__IN_ORIGINAL_FIELD_CHANGES = "inOriginalFieldChanges";
    private static final String T__IN_MORE_FIELD_CHANGES = "inMoreFieldChanges";
    private static final String T__ON_UPDATE = "onUpdate";
    private final String T__ON_BOARD_UNIT_UPDATE = "OnboardUnitUpdate";


    private static final String LIBRARY = "ITSDraymanGateAdaptor";
    private static final Logger LOGGER = Logger.getLogger(ITSUnitELI.class);
}
