/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageLevel
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author <a href="mailto:sanandaraj@servimostech.com">S Anandaraj</a>, 12/JUL/2022
 *
 * Requirements : This groovy is used to validate CARGO-cut-off field because EDI-cut-off field is mandatory.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *   1. Go to Administration --> System -->  Code Extension
 *   2. Click Add (+)
 *   3. Enter the values as below:
 Code Extension Name:  ITSVesselVisitFormSubmission
 Code Extension Type:  FORM_SUBMISSION_INTERCEPTOR
 Groovy Code: Copy and paste the contents of groovy code.
 *   4. Click Save button
 *
 * <formSubmissionCodeExtension name="ITSVesselVisitFormSubmission"/>
 *
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *   1      12-Nov-2022     Gopinath K      IP-9      B 5-1 Standardize Marine Invoices
 */

class ITSVesselVisitFormSubmission extends AbstractFormSubmissionCommand {

    private static final Logger LOGGER = Logger.getLogger(ITSVesselVisitFormSubmission.class)

    @Override
    void doBeforeSubmit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSVesselVisitFormSubmission Vessel Visit Validation")
        Map paramMap = new HashMap()
        Map results = new HashMap()
        paramMap.put("GKEYS", inGkeys)
        paramMap.put("FIELD_CHANGES", inOutFieldChanges)
        MessageCollector messageCollector = executeInTransaction("ITSVesselVisitBeforeSubmitCallback", paramMap, results)
        if (results.get("ERROR")) {
            registerError(results.get("ERROR"))
            return
        } else if (results.get("WARNING")) {
            getExtensionHelper().showMessageDialog(MessageLevel.WARNING, "WARNING", results.get("WARNING"))

        }



    }
}
