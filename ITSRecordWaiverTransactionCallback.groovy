/*
* Copyright (c) 2022 WeServe LLC. All Rights Reserved.
*
*/


import com.navis.argo.ArgoExtractField
import com.navis.argo.ArgoPropertyKeys
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.GuaranteeTypeEnum
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.*
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizViolation
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.internationalization.UserMessage
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.InventoryBizMetafield
import com.navis.inventory.business.InventoryFacade
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.units.UnitFacilityVisit
import org.apache.log4j.Level
import org.apache.log4j.Logger

/*
*
* @Author: mailto:kgopinath@weservetech.com, Gopinath Kannappan; Date: 29/12/2022
*
*  Requirements: 7-9 Apply Waiver to Multiple Units -- This groovy is used to record the guarantee for multiple units.
*
*  @Inclusion Location: Incorporated as a code extension of the type
*
*  Load Code Extension to N4:
*  1. Go to Administration --> System --> Code Extensions
*  2. Click Add (+)
*  3. Enter the values as below:
*     Code Extension Name: ITSRecordWaiverTransactionCallback
*     Code Extension Type: TRANSACTED_BUSINESS_FUNCTION
*     Groovy Code: Copy and paste the contents of groovy code.
*  4. Click Save button
*
*	@Setup Calling as transaction business function from ITSRecordWaiverSubmitFormCommand and execute it.
*
*
*  S.No    Modified Date   Modified By     Jira      Description
*
*/

class ITSRecordWaiverTransactionCallback extends AbstractExtensionPersistenceCallback {
    @Override
    void execute(Map inputParams, Map inOutResults) {

        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSRecordWaiverTransactionCallback started execution!!!!!!!")

        List<Serializable> gkeyList = (List<Serializable>) inputParams.get("GKEYS")
        FieldChanges fieldChanges = (FieldChanges) inputParams.get("FIELD_CHANGES")
        if (gkeyList == null || (gkeyList != null && gkeyList.isEmpty())) {
            if (gkeyList) {
                LOGGER.debug("ITSRecordWaiverTransactionCallback gkeyList size:" + gkeyList)
            }
            return;
        }
        for (int i = 0; i < gkeyList.size(); i++) {
            FieldChanges newFieldChanges = new FieldChanges(fieldChanges)
            Serializable gkey = gkeyList.get(i)
            UnitFacilityVisit unitFacilityVisit = UnitFacilityVisit.hydrate(gkey)
            ChargeableUnitEvent chargeableUnitEvent
            if (unitFacilityVisit != null) {
                String unitId = unitFacilityVisit.getUfvUnit()?.getUnitId()
                Serializable extractEventType = null
                if (newFieldChanges.hasFieldChange(InventoryBizMetafield.EXTRACT_EVENT_TYPE)) {
                    extractEventType = (Serializable) newFieldChanges.findFieldChange(InventoryBizMetafield.EXTRACT_EVENT_TYPE).getNewValue()
                }


                Date startDate = null
                if (newFieldChanges.hasFieldChange(ArgoExtractField.GNTE_GUARANTEE_START_DAY)) {
                    startDate = (Date) newFieldChanges.findFieldChange(ArgoExtractField.GNTE_GUARANTEE_START_DAY).getNewValue()
                }
                Date endDate = null
                if (newFieldChanges.hasFieldChange(ArgoExtractField.GNTE_GUARANTEE_END_DAY)) {
                    endDate = (Date) newFieldChanges.findFieldChange(ArgoExtractField.GNTE_GUARANTEE_END_DAY).getNewValue()
                }

                Date linePaidThruDay = unitFacilityVisit.getUfvLinePaidThruDay()
                if (linePaidThruDay != null && startDate <= linePaidThruDay) {
                    throw BizViolation.create(PropertyKeyFactory.valueOf(ArgoPropertyKeys.ENTRY_INVALID), null, " A Waiver start date should be ", "after the Line Paid Through Day.")
                }
                chargeableUnitEvent = extractEventType != null ? ChargeableUnitEvent.hydrate(extractEventType) : null;
                DomainQuery cueQuery = null;
                List<ChargeableUnitEvent> chargeableUnitEventList = null;
                if (chargeableUnitEvent != null) {
                    cueQuery = QueryUtils.createDomainQuery("ChargeableUnitEvent")
                    cueQuery.addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_UFV_GKEY, gkey))
                    cueQuery.addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_EVENT_TYPE, chargeableUnitEvent.getEventType()))
                    cueQuery.addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_STATUS, "PARTIAL", "QUEUED"));
                }


                chargeableUnitEventList = cueQuery != null ? HibernateApi.getInstance().findEntitiesByDomainQuery(cueQuery) : null;
                if (chargeableUnitEventList != null) {
                    chargeableUnitEvent = !chargeableUnitEventList.isEmpty() ? chargeableUnitEventList.get(0) : null;
                }

                if (chargeableUnitEvent != null) {
                    newFieldChanges.setFieldChange(InventoryBizMetafield.EXTRACT_EVENT_TYPE, chargeableUnitEvent.getPrimaryKey())
                    newFieldChanges.setFieldChange(UnitField.UFV_UNIT_ID, unitId)
                    newFieldChanges.setFieldChange(ArgoExtractField.GNTE_GUARANTEE_TYPE, GuaranteeTypeEnum.WAIVER)
                    newFieldChanges.setFieldChange(ArgoExtractField.GNTE_APPLIED_TO_NATURAL_KEY, unitId)
                    newFieldChanges.setFieldChange(ArgoExtractField.GNTE_APPLIED_TO_PRIMARY_KEY, chargeableUnitEvent.getPrimaryKey())

                    CrudOperation crud = new CrudOperation(null, 1, "UnitFacilityVisit", newFieldChanges, gkey)
                    BizRequest req = new BizRequest(ContextHelper.getThreadUserContext())
                    req.addCrudOperation(crud)
                    BizResponse response = new BizResponse()
                    InventoryFacade inventoryFacade = (InventoryFacade) Roastery.getBean(InventoryFacade.BEAN_ID)

                    inventoryFacade.recordWaiverForUfv(req, response)

                    if (response.getMessages(MessageLevel.SEVERE)) {
                        List<UserMessage> userMessageList = (List<UserMessage>) response.getMessages(MessageLevel.SEVERE)
                        for (UserMessage message : userMessageList) {
                            getMessageCollector().appendMessage(message)
                        }
                    }

                }

            }
        }
    }
    private static Logger LOGGER = Logger.getLogger(ITSRecordWaiverTransactionCallback.class)
}
