/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.FieldChanges
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.road.RoadApptsField
import org.apache.log4j.Logger

/**
 * @Author: Kishore Kumar S <a href= skishore@weservetech.com / >, 04/11/2022
 *
 *  Requirements: ITS - 3-1 Container Exemption List - Setting Exemption List value in Gate Appt as true while generating appointment through exemption list form and
 *                allows generation only with present date value.
 *
 * @Inclusion Location: Incorporated as a code extension of the type FORM_SUBMISSION_COMMAND
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSExceptionApptSubmitFormCommand
 *     Code Extension Type: FORM_SUBMISSION_COMMAND
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */

class ITSExceptionApptSubmitFormCommand extends AbstractFormSubmissionCommand {
    private static Logger LOGGER = Logger.getLogger(ITSExceptionApptSubmitFormCommand.class)

    @Override
    void doBeforeSubmit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        super.doBeforeSubmit(inVariformId, inEntityId, inGkeys, inOutFieldChanges, inNonDbFieldChanges, inParams)
        FieldChanges fieldChange = (FieldChanges) inOutFieldChanges
        if (fieldChange != null) {
            PersistenceTemplate persistenceTemplate = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext())
            persistenceTemplate.invoke(new CarinaPersistenceCallback() {
                @Override
                protected void doInTransaction() {
                    String gapptInputDate = fieldChange?.getFieldChange(RoadApptsField.GAPPT_REQUESTED_DATE)?.getNewValue()
                    TimeZone timeZone = ContextHelper.getThreadUserTimezone()
                    String currentDate = (ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))
                    if (null != gapptInputDate) {
                        if (currentDate.substring(0, 10).equals(gapptInputDate.substring(0, 10))) {
                            fieldChange.setFieldChange(RoadApptsField.GAPPT_UFV_FLEX_STRING01, "true")
                        } else {
                            registerError("Kindly provide only present date")
                        }
                    }
                }
            })
        }
    }
}
