/*
* Copyright (c) 2022 WeServe LLC. All Rights Reserved.
*
*/


import com.navis.argo.ArgoExtractField
import com.navis.argo.business.atoms.GuaranteeOverrideTypeEnum
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.extract.Guarantee
import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.FieldChanges
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.framework.util.message.MessageCollector
import com.navis.inventory.InventoryBizMetafield
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.units.GuaranteeManager
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.web.InventoryGuiMetafield
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 29/Dec/2022
 *
 * Requirements : 7-9 Apply Waiver to Multiple Units -- This groovy is used to record the waiver for multiple units.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *   1. Go to Administration --> System -->  Code Extension
 *   2. Click Add (+)
 *   3. Enter the values as below:
 Code Extension Name:  ITSRecordWaiverSubmitFormCommand
 Code Extension Type:  FORM_SUBMISSION_INTERCEPTOR
 Groovy Code: Copy and paste the contents of groovy code.
 *   4. Click Save button
 *
 * @Set up in the database backed variform - INV_FORM_RECORD_WAIVER- adding action link to call this command and execute it.
 * <formSubmissionCodeExtension name="ITSRecordWaiverSubmitFormCommand"/>
 *
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 */

class ITSRecordWaiverSubmitFormCommand extends AbstractFormSubmissionCommand {

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
                //getMessageCollector().appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("INVALID_DATE"), null, null)
            }
        }


        LOGGER.debug("before submit inOutFieldChanges" + inOutFieldChanges)
    }

    @Override
    void submit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSRecordWaiverSubmitFormCommand started execution!!!!!!!submit method")

        Map paramMap = new HashMap()
        Map results = new HashMap()
        paramMap.put("FIELD_CHANGES", inOutFieldChanges)
        paramMap.put("GKEYS", inGkeys)

        EFieldChanges efc = inNonDbFieldChanges
        efc.setFieldChange(SKIP_AFTER_SUBMIT, "No")

        MessageCollector messageCollector = executeInTransaction("ITSRecordWaiverTransactionCallback", paramMap, results)
        if (messageCollector.hasError()) {
            efc.setFieldChange(SKIP_AFTER_SUBMIT, "Yes")
            registerMessageCollector(messageCollector)
        }
    }

    @Override
    void doAfterSubmit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        EFieldChanges eFieldChanges = inNonDbFieldChanges
        String skipSubmitPrior = eFieldChanges.findFieldChange(SKIP_AFTER_SUBMIT).getPriorValue()
        String skipSubmitNew = eFieldChanges.findFieldChange(SKIP_AFTER_SUBMIT).getNewValue()
        LOGGER.debug("ITSRecordGuaranteeSubmitFormCommand skipSubmitprior :: " + skipSubmitPrior + " skipSubmitNew : " + skipSubmitNew)
        if (skipSubmitNew != null && skipSubmitNew == "No") {
            PersistenceTemplate persistenceTemplate = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext())
            persistenceTemplate.invoke(new CarinaPersistenceCallback() {
                @Override
                protected void doInTransaction() {
                    for (Serializable gkey : inGkeys) {
                        UnitFacilityVisit unitFacilityVisit = UnitFacilityVisit.hydrate(gkey)
                        if (unitFacilityVisit != null) {
                            Serializable extractEventType = (Serializable) inFieldChanges.findFieldChange(InventoryBizMetafield.EXTRACT_EVENT_TYPE).getNewValue()
                            LOGGER.debug("extractEventType" + extractEventType)
                            GuaranteeOverrideTypeEnum gnteOverrideValueTypeValue = (GuaranteeOverrideTypeEnum) inFieldChanges.findFieldChange(ArgoExtractField.GNTE_OVERRIDE_VALUE_TYPE).getNewValue()
                            Date gnteEndDate = (Date) inFieldChanges.findFieldChange(ArgoExtractField.GNTE_GUARANTEE_END_DAY).getNewValue()
                            Serializable gnteCustomer = (Serializable) inFieldChanges.findFieldChange(ArgoExtractField.GNTE_GUARANTEE_CUSTOMER).getNewValue()
                            ChargeableUnitEvent chargeableUnitEvent = ChargeableUnitEvent.hydrate(extractEventType)
                            List<Guarantee> guaranteeList = Guarantee.findGuaranteesForCue(chargeableUnitEvent)
                            for (Guarantee guarantee : guaranteeList) {
                                if (guarantee != null) {
                                    if (gnteOverrideValueTypeValue.equals(GuaranteeOverrideTypeEnum.FREE_NOCHARGE)) {
                                        if (chargeableUnitEvent.getBexuEventType().equals("LINE_STORAGE")) {
                                            unitFacilityVisit.setFieldValue(UnitField.UFV_LINE_GUARANTEE_THRU_DAY, gnteEndDate)
                                            unitFacilityVisit.setFieldValue(InventoryGuiMetafield.UFV_LINE_GUARANTEE_PARTY, gnteCustomer)
                                        } else if (chargeableUnitEvent.getBexuEventType().equals("STORAGE")) {
                                            unitFacilityVisit.setFieldValue(InventoryGuiMetafield.UFV_GUARANTEE_THRU_DAY, gnteEndDate)
                                            unitFacilityVisit.setFieldValue(InventoryGuiMetafield.UFV_GUARANTEE_PARTY, gnteCustomer)
                                        }
                                    } else if (gnteOverrideValueTypeValue.equals(GuaranteeOverrideTypeEnum.FIXED_PRICE)) {
                                        Guarantee relatedGuaranteeWaiver = Guarantee.getRelatedGuaranteeForWaiver(guarantee)
                                        if (relatedGuaranteeWaiver != null) {
                                            GuaranteeManager.updateUfvGuaranteeThruDayAndParty(relatedGuaranteeWaiver)
                                        }
                                    }
                                }
                            }
                        }
                        // Save Reason Code Value
                        String resultFromSubmit = inParams.get("UIParamSubmitResult")
                        LOGGER.debug("Field Changes::" + inFieldChanges.toString())
                        LOGGER.debug("******* Field Changes inNonDbFieldChanges::" + inNonDbFieldChanges.toString())
                        if (SUCCESS.equalsIgnoreCase(resultFromSubmit)) {
                            LOGGER.debug("******* Success::" + inNonDbFieldChanges.toString())
                        }
                        HibernateApi.getInstance().save(unitFacilityVisit)
                    }
                }
            })
        }
    }

    private static MetafieldId SKIP_AFTER_SUBMIT = MetafieldIdFactory.valueOf("skipaftersubmit")
    private static final String SUCCESS = "Success"
    private static final Logger LOGGER = Logger.getLogger(ITSRecordWaiverSubmitFormCommand.class)
}



