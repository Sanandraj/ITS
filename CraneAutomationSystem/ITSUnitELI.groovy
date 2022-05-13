package CraneAutomationSystem

import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView


/**
 * @author <a href="mailto:sramasamy@weservetech.com"> Ramasamy Sathappan</a>
 * @since 13-May-2022
 * For CAS - OnBoardUnitUpdate
 *
 * */
class ITSUnitELI extends AbstractEntityLifecycleInterceptor {

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        Map map = new HashMap();
        map.put(T__IN_ENTITY, inEntity);
        map.put(T__IN_TRIGGER_TYPE, T__ON_UPDATE);
        map.put(T__IN_ORIGINAL_FIELD_CHANGES, inOriginalFieldChanges);
        map.put(T__IN_MORE_FIELD_CHANGES, inMoreFieldChanges);

        Object object = getLibrary(T__ON_BOARD_UNIT_UPDATE);
        object.execute(map);
    }

    private static final String T__IN_ENTITY = "inEntity";
    private static final String T__IN_TRIGGER_TYPE = "inTriggerType";
    private static final String T__IN_ORIGINAL_FIELD_CHANGES = "inOriginalFieldChanges";
    private static final String T__IN_MORE_FIELD_CHANGES = "inMoreFieldChanges";
    private static final String T__ON_UPDATE = "onUpdate";
    private final String T__ON_BOARD_UNIT_UPDATE = "OnboardUnitUpdate";
}
