//package CraneAutomationSystem


import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.reference.Container
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.metafields.MetafieldIdFactory
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @author <a href="mailto:sramasamy@weservetech.com"> Ramasamy Sathappan</a>
 * @since 13-May-2022
 * For CAS - OnBoardUnitUpdate onUpdate
 * */

/*
 *
 * @author <a href="mailto:sanandaraj@weservetech.com">Anandaraj S</a>, 08/AUG/2022
 *
 * Requirements : This groovy is used to update the check digit while creating the container- to avoid "??".
 *
 * @Inclusion Location	: Incorporated as a code extension of the type ENTITY_LIFECYCLE_INTERCEPTION.
 *
 *  Load Code Extension to N4:
        1. Go to Administration --> System --> Code Extensions
        2. Click Add (+)
        3. Enter the values as below:
            Code Extension Name:  ITSContainerELI
            Code Extension Type:  ENTITY_LIFECYCLE_INTERCEPTION
            Groovy Code: Copy and paste the contents of groovy code.
        4. Click Save button

 Attach code extension to Extension Trigger:
        1. Go to Administration-->System-->Extension Trigger
        2. Add / Select the Container and right click on it
        3. Click on Edit
        4. Select the Trigger Entity Name as  in "Container" and Extension as ITSContainerELI.
        5. Click on save
 *
 */


class ITSContainerELI extends AbstractEntityLifecycleInterceptor {
    private static Logger LOGGER = Logger.getLogger(ITSContainerELI.class);

    @Override
    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        onCreateOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges)
    }

    void onCreateOrUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        LOGGER.setLevel(Level.DEBUG);
        LOGGER.debug("ITSContainerELI started execution!!!!:");
        if (inOriginalFieldChanges == null) {
            return;
        }
        Container inContainer = inEntity._entity;

        if (inContainer != null && !(DataSourceEnum.EDI_STOW.equals(inContainer.getEqDataSource()))) {
            LOGGER.debug("ITSContainerELI :" + inContainer);
            boolean isExcludeCheckDigitVal = false;
            LOGGER.debug("ITSContainerELI isExcludeCheckDigitVal::" + isExcludeCheckDigitVal);
            if (!isExcludeCheckDigitVal) {
                inContainer = checkDigitValidation(inContainer)
                inMoreFieldChanges.setFieldChange(MetafieldIdFactory.valueOf("eqIdFull"), inContainer.getEqIdFull())
                inMoreFieldChanges.setFieldChange(MetafieldIdFactory.valueOf("eqIdCheckDigit"), inContainer.getEqIdCheckDigit())
                inMoreFieldChanges.setFieldChange(MetafieldIdFactory.valueOf("eqIdNbrOnly"), inContainer.getEqIdNbrOnly())
            }
        }


        LOGGER.debug("ITSContainerELI execution completed:");
    }

    private Container checkDigitValidation(Container container) {
        String ctrNbr = container != null ? container.getEqIdFull() : null
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSContainerELI ctrNbr.length()::" + ctrNbr.length());
        LOGGER.debug("ITSContainerELI container.getEqIdCheckDigit() B4 ::" + container.getEqIdCheckDigit());
        if (ctrNbr.length() == 11 && "??".equalsIgnoreCase(container.getEqIdCheckDigit())) {
            container.setEqIdCheckDigit(ctrNbr.substring(ctrNbr.length() - 1))
            LOGGER.debug("ITSContainerELI container.getEqIdCheckDigit()::" + container.getEqIdCheckDigit());
        } else if (ctrNbr.length() == 10) {
            String eqCheckDigit = Container.calcCheckDigit(ctrNbr)
            LOGGER.debug("ITSContainerELI eqCheckDigit::" + eqCheckDigit);
            LOGGER.debug("ITSContainerELI container.getEqIdPrefix() ::" + container.getEqIdPrefix());
            LOGGER.debug("ITSContainerELI container.getEqIdPrefix().substring(3) ::" + container.getEqIdPrefix().substring(3));
            if ("U".equalsIgnoreCase(container.getEqIdPrefix().substring(3))) {
                container.setEqIdFull(ctrNbr.concat(eqCheckDigit))
                container.setEqIdNbrOnly(container.getEqIdFull().substring(4))
                container.setEqIdCheckDigit(eqCheckDigit)
                LOGGER.debug("ITSContainerELI container.getEqIdFull().substring(4) ::" + container.getEqIdFull().substring(4));
            }

        }
        return container;
    }

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