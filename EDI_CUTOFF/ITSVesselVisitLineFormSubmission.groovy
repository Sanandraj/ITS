import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChange
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.inventory.business.api.InventoryCargoManager
import com.navis.vessel.business.schedule.VesselVisitLine
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.text.SimpleDateFormat

class ITSVesselVisitLineFormSubmission extends AbstractFormSubmissionCommand {
    @Override
    void doBeforeSubmit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSVesselVisitLineFormSubmission execution begins::::")
        boolean isValid = false
        PersistenceTemplate persistenceTemplate = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext())
        persistenceTemplate.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                if (inGkeys != null && inGkeys.size() > 0) {
                    InventoryCargoManager inventoryCargoManager = (InventoryCargoManager) Roastery.getBean(InventoryCargoManager.BEAN_ID);
                    for (Serializable gkey : inGkeys) {

                        VesselVisitLine vesselVisitLine = VesselVisitLine.hydrate(gkey)

                        if (vesselVisitLine != null) {
                            EFieldChange eFieldChange_cargo = inOutFieldChanges.findFieldChange(CARGO_CUT_OFF)
                            EFieldChange eFieldChange_Edi = inOutFieldChanges.findFieldChange(CARGO_EDI_OFF)
                            if (eFieldChange_cargo != null) {
                                Date cargoCut = eFieldChange_cargo.getNewValue() as Date

                                def sdf = new SimpleDateFormat("yyyy-MM-dd")
                                if (vesselVisitLine.getVvlineTimeActivateYard() == null && cargoCut != null && eFieldChange_Edi == null) {
                                    LOGGER.debug("ITSVesselVisitLineFormSubmission cargoCut is full and haz is mty ::::")
                                    isValid = true;
                                } else {
                                    LOGGER.debug("ITSVesselVisitLineFormSubmission cargoCut is mty and haz is mty ::::")
                                    isValid = false;
                                }
                            }

                        }

                    }
                    if (isValid) {
                        registerError("Cannot update CARGO-cut-off field because EDI-cut-off field is mandatory")
                    }


                }

            }
        });
    }


    private static Logger LOGGER = Logger.getLogger(ITSVesselVisitFormSubmission.class)
    private static final MetafieldId CARGO_CUT_OFF = MetafieldIdFactory.valueOf("vvlineTimeCargoCutoff")
    private static final MetafieldId CARGO_EDI_OFF = MetafieldIdFactory.valueOf("vvlineTimeActivateYard")
}

