/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.ArgoExtractField
import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.portal.FieldChanges
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageLevel
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 29/Dec/2022
 *
 * Requirements : 7-9 Apply Waiver to Multiple Units -- This groovy is used to record the guarantee for multiple units.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *   1. Go to Administration --> System -->  Code Extension
 *   2. Click Add (+)
 *   3. Enter the values as below:
 Code Extension Name:  ITSRecordGuaranteeSubmitFormCommand
 Code Extension Type:  FORM_SUBMISSION_INTERCEPTOR
 Groovy Code: Copy and paste the contents of groovy code.
 *   4. Click Save button
 *
 * <formSubmissionCodeExtension name="ITSRecordGuaranteeSubmitFormCommand"/>
 *
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 */


class ITSRecordGuaranteeSubmitFormCommand extends AbstractFormSubmissionCommand {


    private static final Logger LOGGER = Logger.getLogger(ITSRecordGuaranteeSubmitFormCommand.class);

    @Override
    void doBeforeSubmit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSRecordWaiverSubmitFormCommand started execution!!!!!!!!!!!!doBeforeSubmit method")

        /* code to check  whether Enddate is less than StartDate.if it is true ,code will will popup a error message.*/
        FieldChanges fieldChanges = (FieldChanges) inOutFieldChanges
        if (fieldChanges != null) {
            Date endDate = null
            Date startDate = null
            if (fieldChanges.hasFieldChange(ArgoExtractField.GNTE_GUARANTEE_START_DAY)) {
                startDate = (Date) fieldChanges.findFieldChange(ArgoExtractField.GNTE_GUARANTEE_START_DAY).getNewValue()
            }
            if (fieldChanges.hasFieldChange(ArgoExtractField.GNTE_GUARANTEE_END_DAY)) {
                endDate = (Date) fieldChanges.findFieldChange(ArgoExtractField.GNTE_GUARANTEE_END_DAY).getNewValue()
            }

            if (startDate != null && endDate != null && endDate.before(startDate)) {
                getMessageCollector().appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("INVALID_DATE"), null, null)
            }
        }


        LOGGER.debug("before submit inOutFieldChanges" + inOutFieldChanges)
    }

    @Override
    void submit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSRecordGuaranteeSubmitFormCommand started execution!!!!!!!submit method")

        Map paramMap = new HashMap();
        Map results = new HashMap();
        paramMap.put("FIELD_CHANGES", inOutFieldChanges)
        paramMap.put("GKEYS", inGkeys)

        EFieldChanges efc = inNonDbFieldChanges
        efc.setFieldChange(SKIP_AFTER_SUBMIT,"No")

        MessageCollector messageCollector = executeInTransaction("ITSRecordGuaranteeTransactionCallback", paramMap, results)
        if (messageCollector.hasError()) {
            efc.setFieldChange(SKIP_AFTER_SUBMIT,"Yes")
            registerMessageCollector(messageCollector)
        }
    }

    private static MetafieldId SKIP_AFTER_SUBMIT = MetafieldIdFactory.valueOf("skipaftersubmit");
}
