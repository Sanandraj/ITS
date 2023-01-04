/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/

package ITS

import com.navis.argo.ArgoField
import com.navis.argo.ContextHelper
import com.navis.argo.business.model.GeneralReference
import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.business.Roastery
import com.navis.framework.email.EmailManager
import com.navis.framework.email.EmailMessage
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.UserContext
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.framework.presentation.context.PresentationContextUtils
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author: mailto: annalakshmig@weservetech.com, Annalakshmi G; Date: 21/11/2022
 *
 *  Requirements: When Update the block/bay value in setup yard blocks need to trigger the email.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSMailOnDeliverableUpdateFormSubCommand
 *     Code Extension Type: FORM_SUBMISSION_INTERCEPTION
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup: Called From: Form - CUSTOM_UPDATE_YARD_BLOCK -   <formSubmissionCodeExtension name="ITSMailOnDeliverableUpdateFormSubCommand" />
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 * */
class ITSMailOnDeliverableUpdateFormSubCommand extends AbstractFormSubmissionCommand {

    @Override
    void doAfterSubmit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {

        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("doAfterSubmit ** started" + inFieldChanges)
        String emailFrom = null
        String emailTo = null
        boolean blockStatusChanged = false
        blockStatusChanged = inFieldChanges.hasFieldChange(ArgoField.REF_VALUE1)
        PersistenceTemplate pt = new PersistenceTemplate(getUserContext());
        pt.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                GeneralReference genRef = GeneralReference.findUniqueEntryById("ITS", "DELIVERABLE_UPDATE_MAIL")
                if (genRef != null && genRef.getRefValue1() != null && genRef.getRefValue2() != null) {
                    emailFrom = genRef.getRefValue1()
                    emailTo = genRef.getRefValue2()
                    GeneralReference generalReference = (GeneralReference) HibernateApi.getInstance().load(GeneralReference.class, inGkeys[0]);
                    if (generalReference) {
                        if (generalReference.getRefId1() != null) {
                            if (deliverable_block.equalsIgnoreCase(generalReference.getRefId1()) && blockStatusChanged) {
                                StringBuilder sb = new StringBuilder()
                                String newBlockStatus = (String) inFieldChanges.findFieldChange(ArgoField.REF_VALUE1).getNewValue()
                                if (!StringUtils.isEmpty(newBlockStatus)) {
                                    if (yes.equalsIgnoreCase(newBlockStatus)) {
                                        sb.append("Block ").append(generalReference.getRefId2()).append(" is updated as Deliverable block")
                                    } else if (no.equalsIgnoreCase(newBlockStatus)) {
                                        sb.append("Block ").append(generalReference.getRefId2()).append(" is updated as Non Deliverable block")
                                    }
                                }
                                sendEmail(emailFrom, emailTo, block_status, sb.toString())
                            } else if (deliverable_bay.equalsIgnoreCase(generalReference.getRefId1()) && generalReference.getRefId2() != null && generalReference.getRefId3() != null && generalReference.getRefValue1() != null) {
                                StringBuilder sb = new StringBuilder()
                                String blockId = generalReference.getRefId2()
                                String bayId = generalReference.getRefId3()
                                String bayStatus = generalReference.getRefValue1()
                                String startDate = generalReference.getRefValue2()
                                String endDate = generalReference.getRefValue3()
                                if (yes.equalsIgnoreCase(bayStatus)) {
                                    sb.append("Bay ${bayId} of block ${blockId} is updated as Deliverable with following fields. Start Date : ${startDate} and End date: ${endDate}")
                                } else if (no.equalsIgnoreCase(bayStatus)) {
                                    sb.append("Bay ${bayId} of block ${blockId} is updated as Non-Deliverable with following fields. Start Date : ${startDate} and End date: ${endDate}")
                                }
                                sendEmail(emailFrom, emailTo, bay_status, sb.toString())
                            }
                        }
                    }
                }


            }

        })
        super.doAfterSubmit(inVariformId, inEntityId, inGkeys, inFieldChanges, inNonDbFieldChanges, inParams)
    }


    public void sendEmail(String inFromAddress, String inToAddress, String inSubject, String inBody) {
        EmailMessage msg = new EmailMessage(getUserContext());
        msg.setSubject(inSubject);
        msg.setTo(inToAddress);
        msg.setText(inBody);
        msg.setReplyTo(inFromAddress);
        msg.setFrom(inFromAddress);

        try {
            getEmailManager().sendEmail(msg)
            LOGGER.warn("mailed successfully");
        } catch (Exception inEx) {
            LOGGER.warn("Exception while sending email " + inEx.toString());
        }

    }

    private static EmailManager getEmailManager() {
        return (EmailManager) Roastery.getBean("emailManager");
    }

    UserContext getUserContext() {
        if (PresentationContextUtils.getRequestContext()) {
            return PresentationContextUtils.getRequestContext().getUserContext();
        } else if (FrameworkPresentationUtils.getUserContext()) {
            return FrameworkPresentationUtils.getUserContext();
        } else {
            return ContextHelper.getThreadUserContext()
        }
    }

    private static final String deliverable_block = "DELIVERABLE_BLOCK"
    private static final String deliverable_bay = "DELIVERABLE_BAY"
    private static final String yes = "Y"
    private static final String no = "N"
    private static final String block_status = "Block Status changed"
    private static final String bay_status = "Bay Status changed"
    private static Logger LOGGER = Logger.getLogger(ITSMailOnDeliverableUpdateFormSubCommand.class);


}
