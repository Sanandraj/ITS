/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/

import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChange
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.vessel.business.schedule.VesselVisitLine
import org.apache.log4j.Logger

/**
 * @Author <a href="mailto:sanandaraj@weservetech.com">S Anandaraj</a>, 12/JUL/2022
 *
 * Requirements : This groovy is used to validate LINE CARGO-cut-off field because EDI-cut-off field is mandatory.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *   1. Go to Administration --> System -->  Code Extension
 *   2. Click Add (+)
 *   3. Enter the values as below:
 Code Extension Name:  ITSVesselVisitLineFormSubmission
 Code Extension Type:  FORM_SUBMISSION_INTERCEPTOR
 Groovy Code: Copy and paste the contents of groovy code.
 *   4. Click Save button
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */


class ITSVesselVisitLineFormSubmission extends AbstractFormSubmissionCommand {
    @Override
    void doBeforeSubmit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        boolean isValid = false
        PersistenceTemplate persistenceTemplate = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext())
        persistenceTemplate.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                if (inGkeys != null && inGkeys.size() > 0) {
                    for (Serializable gkey : inGkeys) {
                        VesselVisitLine vesselVisitLine = VesselVisitLine.hydrate(gkey)

                        if (vesselVisitLine != null) {
                            EFieldChange eFieldChange_cargo = inOutFieldChanges.findFieldChange(CARGO_CUT_OFF)
                            EFieldChange eFieldChange_Edi = inOutFieldChanges.findFieldChange(CARGO_EDI_OFF)
                            if (eFieldChange_cargo != null) {
                                Date cargoCut = eFieldChange_cargo.getNewValue() as Date
                                if (vesselVisitLine.getVvlineTimeActivateYard() == null && cargoCut != null && eFieldChange_Edi == null) {
                                    isValid = true;
                                } else {
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


    private static Logger LOGGER = Logger.getLogger(ITSVesselVisitLineFormSubmission.class)
    private static final MetafieldId CARGO_CUT_OFF = MetafieldIdFactory.valueOf("vvlineTimeCargoCutoff")
    private static final MetafieldId CARGO_EDI_OFF = MetafieldIdFactory.valueOf("vvlineTimeActivateYard")
}

