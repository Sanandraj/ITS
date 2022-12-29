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
* @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 29/Dec/2022
*
*  Requirements :  7-9 Apply Waiver to Multiple Units -- This groovy is used to record the guarantee for multiple units.
*
* @Inclusion Location	: Incorporated as a code extension of the type TRANSACTED_BUSINESS_FUNCTION .Copy --> Paste this code   (ITSRecordWaiverTransactionCallback.groovy)
*
* @Set up :- Calling as transaction business function from ITSRecordWaiverSubmitFormCommand and execute it.
*
*
*/

class ITSRecordWaiverTransactionCallback extends AbstractExtensionPersistenceCallback {
    @Override
    void execute(Map inputParams, Map inOutResults) {

        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSRecordWaiverTransactionCallback started execution!!!!!!!")

        List<Serializable> gkeyList = (List<Serializable>) inputParams.get("GKEYS")
        FieldChanges fieldChanges = (FieldChanges) inputParams.get("FIELD_CHANGES")
        for (int i = 0; i < gkeyList.size(); i++) {
            LOGGER.debug("ITSRecordWaiverTransactionCallback size : " + gkeyList.size())
            FieldChanges newFieldChanges = new FieldChanges(fieldChanges)
            Serializable gkey = gkeyList.get(i)
            UnitFacilityVisit unitFacilityVisit = UnitFacilityVisit.hydrate(gkey)
            ChargeableUnitEvent chargeableUnitEvent
            if (unitFacilityVisit != null) {
                String unitId = unitFacilityVisit.getUfvUnit().getUnitId()
                Serializable extractEventType = (Serializable) newFieldChanges.findFieldChange(InventoryBizMetafield.EXTRACT_EVENT_TYPE).getNewValue()
                LOGGER.debug("ITSRecordWaiverTransactionCallback extractEventType : " + extractEventType)

                Date startDate = (Date) newFieldChanges.findFieldChange(ArgoExtractField.GNTE_GUARANTEE_START_DAY).getNewValue()
                Date endDate = (Date) newFieldChanges.findFieldChange(ArgoExtractField.GNTE_GUARANTEE_END_DAY).getNewValue()

                Date linePaidThruDay = unitFacilityVisit.getUfvLinePaidThruDay()
                Date calculatedLfd = unitFacilityVisit.getUfvCalculatedLastFreeDayDate("LINE_STORAGE")
                if (linePaidThruDay != null && startDate <= linePaidThruDay) {
                    throw BizViolation.create(PropertyKeyFactory.valueOf(ArgoPropertyKeys.ENTRY_INVALID), null, " A Waiver start date should be ", "after the Line Paid Through Day.")
                }
                if (calculatedLfd != null && startDate != null && startDate.before(calculatedLfd)) {
                    LOGGER.debug("inside calculatedLfd    throw BizViolation  :: " + startDate)
                    throw BizViolation.create(PropertyKeyFactory.valueOf(ArgoPropertyKeys.ENTRY_INVALID), null, " A Waiver start date should be ", "after the line last free day.")
                }
                chargeableUnitEvent = ChargeableUnitEvent.hydrate(extractEventType)
                DomainQuery cueQuery = QueryUtils.createDomainQuery("ChargeableUnitEvent")
                        .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_UFV_GKEY, gkey))
                        .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_EVENT_TYPE, chargeableUnitEvent.getEventType()))
                        .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_STATUS, "PARTIAL", "QUEUED"))
                List<ChargeableUnitEvent> chargeableUnitEventList = HibernateApi.getInstance().findEntitiesByDomainQuery(cueQuery)
                chargeableUnitEvent = chargeableUnitEventList.get(0)

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
                LOGGER.debug("ITSRecordWaiverTransactionCallback message : " + response.getMessages())

                if (response.getMessages(MessageLevel.SEVERE)) {
                    List<UserMessage> userMessageList = (List<UserMessage>) response.getMessages(MessageLevel.SEVERE)
                    for (UserMessage message : userMessageList) {
                        getMessageCollector().appendMessage(message)
                    }
                    LOGGER.debug("ITSRecordWaiverTransactionCallback userMessageList : " + userMessageList.toString())
                }
            }
        }
    }
    private static final Logger LOGGER = Logger.getLogger(ITSRecordWaiverTransactionCallback.class)
}
