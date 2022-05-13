package CraneAutomationSystem

import com.navis.extension.handler.business.entityinterception.DefaultEntityView
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.persistence.Entity
import com.navis.framework.portal.FieldChanges


/**
 * @author <a href="mailto:sramasamy@weservetech.com"> Ramasamy Sathappan</a>
 * @since 13-May-2022
 * For CAS - CraneWorkListUpdate
 *
 * */
class ITSWorkQueueELI extends AbstractEntityLifecycleInterceptor {

    @Override
    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        craneWorkList(inEntity, inOriginalFieldChanges, inMoreFieldChanges, T__ON_CREATE);
    }

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        craneWorkList(inEntity, inOriginalFieldChanges, inMoreFieldChanges, T__ON_UPDATE);
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

    private static final String T__IN_ENTITY = "inEntity";
    private static final String T__IN_TRIGGER_TYPE = "inTriggerType";
    private static final String T__IN_ORIGINAL_FIELD_CHANGES = "inOriginalFieldChanges";
    private static final String T__IN_MORE_FIELD_CHANGES = "inMoreFieldChanges";
    private static final String T__ON_CREATE = "onCreate";
    private static final String T__ON_UPDATE = "onUpdate";
    private static final String T__ON_DELETE = "onDelete";
    private final String T__CRANE_WORK_LIST_UPDATE = "CraneWorkListUpdate";
}
