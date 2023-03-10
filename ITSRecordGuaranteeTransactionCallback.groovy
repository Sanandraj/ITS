/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.ArgoExtractField
import com.navis.argo.ArgoPropertyKeys
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.GuaranteeTypeEnum
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.extract.Guarantee
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
import org.apache.commons.lang.time.DateUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

/*
* @Author: mailto:kgopinath@weservetech.com, Gopinath ; Date: 29/12/2022
*
*  Requirements: 7-9 Apply Waiver to Multiple Units -- This groovy is used to record the guarantee for multiple units.
*
*  @Inclusion Location: Incorporated as a code extension of the type
*
*  Load Code Extension to N4:
*  1. Go to Administration --> System --> Code Extensions
*  2. Click Add (+)
*  3. Enter the values as below:
*     Code Extension Name: ITSRecordGuaranteeTransactionCallback
*     Code Extension Type: TRANSACTED_BUSINESS_FUNCTION
*     Groovy Code: Copy and paste the contents of groovy code.
*  4. Click Save button
*
*	@Setup Calling as transaction business function from ITSRecordGuaranteeSubmitFormCommand and execute it.
*
*
*  S.No    Modified Date   Modified By     Jira      Description
*   1      02-02-2023       Gopinath K     IP-409    Adding the date validation for UNIT_EXTENDED_DWELL
*/


class ITSRecordGuaranteeTransactionCallback extends AbstractExtensionPersistenceCallback {

    @Override
    void execute(Map inputParams, Map inOutResults) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSRecordGuaranteeTransactionCallback started execution!!!!!!!")

        List<Serializable> gkeyList = (List<Serializable>) inputParams.get("GKEYS")
        FieldChanges fieldChanges = (FieldChanges) inputParams.get("FIELD_CHANGES")

        FieldChanges newFieldChanges = new FieldChanges(fieldChanges)

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

        LOGGER.debug("ITSRecordGuaranteeTransactionCallback extractEventType" + extractEventType)


        for (Serializable gkey : gkeyList) {
            UnitFacilityVisit unitFacilityVisit = UnitFacilityVisit.hydrate(gkey)
            ChargeableUnitEvent chargeableUnitEvent
            if (unitFacilityVisit != null) {
                String unitId = unitFacilityVisit?.getUfvUnit()?.getUnitId()
                chargeableUnitEvent = ChargeableUnitEvent.hydrate(extractEventType)


                String eventId = chargeableUnitEvent != null ? chargeableUnitEvent.getEventType() : null;
                if (eventId != null && "UNIT_EXTENDED_DWELL".equalsIgnoreCase(eventId)) {
                    if (startDate == null || endDate == null) {
                        throw BizViolation.create(PropertyKeyFactory.valueOf(ArgoPropertyKeys.ENTRY_INVALID), null, " Date :  ", "Start and End date is mandatory.")
                    }
                    if (startDate != null && endDate != null && endDate.before(startDate)) {
                        throw BizViolation.create(PropertyKeyFactory.valueOf(ArgoPropertyKeys.ENTRY_INVALID), null, " Date :  ", "End date should be less than Start date.")
                    }
                }

                Date linePaidThruDay = unitFacilityVisit.getUfvLinePaidThruDay()
                Date calculatedLfd = unitFacilityVisit.getUfvCalculatedLastFreeDayDate("LINE_STORAGE")
                if (linePaidThruDay != null && startDate != null && startDate <= linePaidThruDay) {
                    throw BizViolation.create(PropertyKeyFactory.valueOf(ArgoPropertyKeys.ENTRY_INVALID), null, " A Guarantee start date should be ", "after the Line Paid Through Day.")
                }
                if (calculatedLfd != null && startDate != null && startDate.before(calculatedLfd)) {
                    throw BizViolation.create(PropertyKeyFactory.valueOf(ArgoPropertyKeys.ENTRY_INVALID), null, " A Guarantee start date should be ", "after the line last free day.")
                }
                DomainQuery dq = QueryUtils.createDomainQuery("Guarantee")
                        .addDqPredicate(PredicateFactory.eq(ArgoExtractField.GNTE_APPLIED_TO_PRIMARY_KEY, extractEventType))
                        .addDqPredicate(PredicateFactory.eq(ArgoExtractField.GNTE_GUARANTEE_TYPE, GuaranteeTypeEnum.OAC))
                        .addDqPredicate(PredicateFactory.isNull(ArgoExtractField.GNTE_VOIDED_OR_EXPIRED_DATE))
                List<Guarantee> guaranteeList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
                if (!guaranteeList.isEmpty() && startDate != null && endDate != null) {
                    for (Guarantee guarantees : (guaranteeList as List<Guarantee>)) {
                        Date guaranteeStartDay = guarantees.getGnteGuaranteeStartDay()
                        Date guaranteeEndDay = guarantees.getGnteGuaranteeEndDay()
                        String gnteGuaranteeId = guarantees.getGnteGuaranteeId()

                        if (DateUtils.isSameDay(guaranteeStartDay, startDate) && DateUtils.isSameDay(guaranteeEndDay, endDate)) {
                            throw BizViolation.create(PropertyKeyFactory.valueOf(ArgoPropertyKeys.ENTRY_INVALID), null, " A Guarantee already exists for the  payment type", "On Account for the same start and end date : ID -> " + gnteGuaranteeId)
                        } else if (DateUtils.isSameDay(guaranteeStartDay, startDate)) {
                            throw BizViolation.create(PropertyKeyFactory.valueOf(ArgoPropertyKeys.ENTRY_INVALID), null, " A Guarantee already exists for the  payment type", "On Account for the same start date : ID -> " + gnteGuaranteeId)
                        } else if (DateUtils.isSameDay(guaranteeEndDay, endDate)) {
                            throw BizViolation.create(PropertyKeyFactory.valueOf(ArgoPropertyKeys.ENTRY_INVALID), null, " A Guarantee already exists for the  payment type", "On Account for the same end date : ID -> " + gnteGuaranteeId)
                        }
                        Date startgntdate = getDate(guaranteeStartDay)
                        Date endgntdate = getDate(guaranteeEndDay)

                        Date startuidate = getDate(startDate)
                        Date enduidate = getDate(endDate)

                        LOGGER.debug("startgntdate    :: " + startgntdate + "  :: " + startuidate + " endgntdate :: " + endgntdate + " enduidate  :: " + enduidate)
                        if (startuidate.after(startgntdate) && enduidate.before(endgntdate)) {
                            throw BizViolation.create(PropertyKeyFactory.valueOf(ArgoPropertyKeys.ENTRY_INVALID), null, " A Guarantee already exists for the  payment type", "On Account for the start end date is between the guarantee  ID -> " + gnteGuaranteeId)
                        }
                    }
                }
                DomainQuery cueQuery = QueryUtils.createDomainQuery("ChargeableUnitEvent")
                        .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_UFV_GKEY, gkey))
                        .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_EVENT_TYPE, chargeableUnitEvent?.getBexuEventType()))
                        .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_STATUS, "PARTIAL", "QUEUED"))
                        .addDqOrdering(Ordering.desc(ArgoExtractField.BEXU_GKEY))

                LOGGER.debug("event type    :: " + chargeableUnitEvent?.getBexuEventType())
                List<ChargeableUnitEvent> chargeableUnitEventList = HibernateApi.getInstance().findEntitiesByDomainQuery(cueQuery)
                if (chargeableUnitEventList != null && !chargeableUnitEventList.isEmpty()) {
                    chargeableUnitEvent = chargeableUnitEventList.get(0)
                    LOGGER.debug("chargeableUnitEvent    :: " + chargeableUnitEvent.getBexuEventType())
                    newFieldChanges.setFieldChange(InventoryBizMetafield.EXTRACT_EVENT_TYPE, chargeableUnitEvent.getPrimaryKey())
                    newFieldChanges.setFieldChange(UnitField.UFV_UNIT_ID, unitId)
                    newFieldChanges.setFieldChange(ArgoExtractField.GNTE_GUARANTEE_TYPE, GuaranteeTypeEnum.OAC)
                    newFieldChanges.setFieldChange(ArgoExtractField.GNTE_APPLIED_TO_NATURAL_KEY, unitId)
                    newFieldChanges.setFieldChange(ArgoExtractField.GNTE_APPLIED_TO_PRIMARY_KEY, chargeableUnitEvent.getPrimaryKey())

                    CrudOperation crud = new CrudOperation(null, 1, "UnitFacilityVisit", newFieldChanges, gkey)
                    BizRequest req = new BizRequest(ContextHelper.getThreadUserContext())
                    req.addCrudOperation(crud)
                    LOGGER.debug("start recording message : ")
                    BizResponse response = new BizResponse()
                    InventoryFacade inventoryFacade = (InventoryFacade) Roastery.getBean(InventoryFacade.BEAN_ID)
                    inventoryFacade.recordGuaranteeForUfv(req, response)
                    LOGGER.debug("ITSRecordGuaranteeTransactionCallback message : " + response.getMessages())
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

    private Date getDate(Date d) {
        Calendar cal = Calendar.getInstance()
        cal.setTime(d)
        cal.set(Calendar.HOUR_OF_DAY, 00)
        cal.set(Calendar.MINUTE, 00)
        cal.set(Calendar.SECOND, 00)
        cal.set(Calendar.MILLISECOND, 00)
        return cal.getTime()
    }
    private static Logger LOGGER = Logger.getLogger(ITSRecordGuaranteeTransactionCallback.class)

}
