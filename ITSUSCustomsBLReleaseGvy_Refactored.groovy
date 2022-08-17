import com.navis.argo.*
import com.navis.argo.business.api.*
import com.navis.argo.business.atoms.*
import com.navis.argo.business.model.Facility
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.LineOperator
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.business.util.QueryUtil
import com.navis.cargo.InventoryCargoEntity
import com.navis.cargo.InventoryCargoField
import com.navis.cargo.business.model.BillOfLading
import com.navis.cargo.business.model.BlRelease
import com.navis.edi.EdiEntity
import com.navis.edi.EdiField
import com.navis.edi.business.atoms.EdiStatusEnum
import com.navis.edi.business.edimodel.EdiConsts
import com.navis.edi.business.entity.EdiBatch
import com.navis.edi.business.util.XmlUtil
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.HibernatingEntity
import com.navis.framework.portal.FieldChanges
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.AggregateFunctionType
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.query.common.api.QueryResult
import com.navis.framework.util.BizViolation
import com.navis.framework.util.BizWarning
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.road.business.util.RoadBizUtil
import com.navis.services.business.rules.EventType
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject

import static java.util.Arrays.asList

/*
* Author : Gopal
*
 */

 class ITSUSCustomsBLReleaseGvy extends AbstractEdiPostInterceptor {

    /**
     * Method will be called from EDI engine
     */
    public void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        LOG.setLevel(Level.INFO)
        ReleaseTransactionsDocument releaseTransactionDocument = (ReleaseTransactionsDocument) inXmlTransactionDocument
        ReleaseTransactionsDocument.ReleaseTransactions releaseTrans = releaseTransactionDocument.getReleaseTransactions()
        ReleaseTransactionDocument.ReleaseTransaction[] releaseArray = releaseTrans.getReleaseTransactionArray(0)
        if (releaseArray != null || releaseArray.length != 0) {
            ReleaseTransactionDocument.ReleaseTransaction relTransaction = releaseArray[0]
            validateRoutingPoint(relTransaction, inParams)
            duplicateCustomMessageSkip(relTransaction, inParams)
            releaseMessageValidation(relTransaction, inParams, relTransaction.getEdiCode())
        }
        else {
            LOG.error("Release Array is NULL in before EDI post method")
            registerError("Error : Release Array is NULL in before EDI post method")
        }
    }

    @Override
     void afterEdiPost(XmlObject inXmlTransactionDocument, HibernatingEntity inHibernatingEntity, Map inParams) {

        if (Boolean.TRUE.equals(inParams.get(SKIP_POSTER))) {
            LOG.info("Skipped after edi post method")
            return
        }
        ReleaseTransactionsDocument releaseDocument = (ReleaseTransactionsDocument) inXmlTransactionDocument
        ReleaseTransactionsDocument.ReleaseTransactions releaseTrans = releaseDocument.getReleaseTransactions()
        ReleaseTransactionDocument.ReleaseTransaction[] releaseTransArray = releaseTrans.getReleaseTransactionArray(0)

        if (releaseTransArray != null || releaseTransArray.length != 0) {

            ReleaseTransactionDocument.ReleaseTransaction releaseTransaction = releaseTransArray[0]
            if (releaseTransaction.getEdiCode() != null) {
                HibernateApi.getInstance().flush()
                BillOfLading billOfLading = null
                EdiReleaseIdentifier releaseIdentifier = releaseTransaction.getEdiReleaseIdentifierArray(0)
                if (releaseIdentifier.getReleaseIdentifierNbr() != null) {
                    LineOperator lineOp = (LineOperator) findLineOperator(releaseTransaction)
                    billOfLading = BillOfLading.findBillOfLading(releaseIdentifier.getReleaseIdentifierNbr(), lineOp, null)
                }
                if (ArgoUtils.isEmpty(releaseIdentifier.getReleaseIdentifierNbr()) || billOfLading == null) {
                    LOG.error("Bill Of Lading not found for BlNbr: " + releaseIdentifier.getReleaseIdentifierNbr())
                    return
                }

                if ("95".equals(releaseTransaction.getEdiCode()) || "1J".equalsIgnoreCase(releaseTransaction.getEdiCode())) {
                    handle1JUsing95(releaseTransaction, billOfLading)
                    inbondStatusUpdate(billOfLading)
                }

                if ("83".equals(releaseTransaction.getEdiCode()) || "1W".equalsIgnoreCase(releaseTransaction.getEdiCode())) {
                    handling1W(releaseTransaction, billOfLading)
                    UpdateExamStatus(billOfLading, Boolean.FALSE, Boolean.FALSE)
                }
                if ("1X".equalsIgnoreCase(releaseTransaction.getEdiCode())) {
                    UpdateExamStatus(billOfLading, Boolean.TRUE, Boolean.FALSE)
                }
                else if ("84".equals(releaseTransaction.getEdiCode())) {
                    UpdateExamStatus(billOfLading, Boolean.TRUE, Boolean.TRUE)
                }

                if ("7H".equalsIgnoreCase(releaseTransaction.getEdiCode())) {
                    billOfLading.setBlFlexString03("7H")
                }
                if ("7I".equalsIgnoreCase(releaseTransaction.getEdiCode())) {
                    billOfLading.setBlFlexString03("")
                }
                updateBlCarrierVisit(billOfLading, inParams)
            }
        }
        else {
            LOG.info("Release Array is NULL in after EDI post method")
        }
    }

    //Private methods

    private void duplicateCustomMessageSkip(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, Map map) {
        EdiReleaseIdentifier releaseIdentifier = releaseTransaction?.getEdiReleaseIdentifierList()?.get(0)
        Serializable batchGkey = (Serializable) map.get(BATCH_GKEY)
        Serializable tranGkey = (Serializable) map.get(EdiConsts.TRANSACTION_GKEY)
        if (EdiBatch.hydrate(batchGkey) != null) {
            String msgRefNbr = EdiBatch.hydrate(batchGkey).getEdibatchMsgRefNbr()
            String interchangeNbr = EdiBatch.hydrate(batchGkey).getEdibatchInterchange()?.getEdiintInterchangeNbr()
            if (interchangeNbr != null && releaseTransaction?.getEdiCode() != null && msgRefNbr != null && releaseIdentifier?.getReleaseIdentifierNbr() != null) {
                LOG.info("code ::" + releaseTransaction?.getEdiCode() + "ediInterchange::" + EdiBatch.hydrate(batchGkey).getEdibatchInterchange() + "msgRefNbr::" + msgRefNbr + "blNbr:" + releaseIdentifier?.getReleaseIdentifierNbr() + "tranGkey :" + tranGkey)
                if (releaseTransaction.getReleasePostDate()?.toString() != null && hasInterchangeAndEdiCode(interchangeNbr, releaseTransaction?.getEdiCode(), msgRefNbr, releaseIdentifier?.getReleaseIdentifierNbr(), releaseTransaction.getReleasePostDate()?.toString(), tranGkey)) {
                    map.put(SKIP_POSTER, true)
                    getMessageCollector().appendMessage(BizWarning.create(ArgoPropertyKeys.INFO, null, "Posting skipped for ${releaseIdentifier?.getReleaseIdentifierNbr()} and edi Code ${releaseTransaction?.getEdiCode()}  - Transaction is duplicate."))
                }
            }
        }
    }

    /**
     * Release message validation whether is for the other facility
     */
    private void releaseMessageValidation(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, Map map, String ediCode) {
        GeneralReference genRef = GeneralReference.findUniqueEntryById("EDI", "350", "PORT_CODES")
        if (genRef!=null){
            List<String> portCodeList = getPortCodesFromGeneralReference(genRef)
            if (releaseTransaction.getEdiReleaseIdentifierList() != null && !releaseTransaction.getEdiReleaseIdentifierList().isEmpty()) {
                EdiReleaseIdentifier releaseIdentifier = releaseTransaction.getEdiReleaseIdentifierList().get(0)
                BillOfLading billOfLading = null
                if (releaseIdentifier.getReleaseIdentifierNbr() != null) {
                    LineOperator lineOp = (LineOperator) findLineOperator(releaseTransaction)
                    billOfLading = BillOfLading.findBillOfLading(releaseIdentifier.getReleaseIdentifierNbr(), lineOp, null)
                }

                if (releaseTransaction.getVesselCallFacility() != null && releaseTransaction.getVesselCallFacility().getFacilityPort() != null &&
                        releaseTransaction.getVesselCallFacility().getFacilityPort().getPortCodes() != null) {
                    String portCode = releaseTransaction.getVesselCallFacility().getFacilityPort().getPortCodes().getId()
                    if (portCode != null && !portCodeList.contains(portCode)) {
                        map.put(SKIP_POSTER, true)

                        if (billOfLading != null) {
                            Double releaseQty = Double.valueOf(releaseTransaction.getReleaseQty())
                            BlRelease blRelease = new BlRelease()
                            blRelease.setBlrelReferenceNbr(releaseTransaction.getReleaseReferenceId())
                            blRelease.setBlrelDispositionCode(releaseTransaction.getEdiCode())
                            blRelease.setBlrelQuantityType(EdiReleaseMapModifyQuantityEnum.InformationOnly)
                            blRelease.setBlrelNotes(releaseTransaction.getReleaseNotes())
                            Date releasePostDate = null
                            if (releaseTransaction.getReleasePostDate() != null && releaseTransaction.getReleasePostDate() instanceof Calendar) {
                                releasePostDate = ArgoEdiUtils.convertLocalToUtcDate(releaseTransaction.getReleasePostDate(), ContextHelper.getThreadEdiPostingContext().getTimeZone())
                            }
                            blRelease.setBlrelQuantity(0.0)
                            blRelease.setBlrelPostDate(releasePostDate)
                            if (releaseQty > 0 && (ediCode != null && (ediCode.equals("4E") || ediCode.equals("95")))) {
                                releaseQty = releaseQty * -1
                            }
                            blRelease.setFieldValue(MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherQty"), releaseQty)
                            blRelease.setFieldValue(MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherPort"), portCode)
                            blRelease.setBlrelBl(billOfLading)
                            HibernateApi.getInstance().save(blRelease)
                            RoadBizUtil.commit()
                            DomainQuery domainQuery = QueryUtils.createDomainQuery("BlRelease")
                                    .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, billOfLading.getPrimaryKey()))
                                    .addDqPredicate(PredicateFactory.isNotNull(MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherQty")))
                                    .addDqAggregateField(AggregateFunctionType.SUM, MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherQty"))
                            Double totalOtherQty = QueryUtil.getQuerySum(domainQuery)
                            billOfLading.setBlFlexDouble01(totalOtherQty)
                            HibernateApi.getInstance().save(billOfLading)
                            ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
                            if (totalOtherQty > 0) {
                                Serializable holdGkey = servicesManager.applyHold("OTHER_PORT_RELEASE", billOfLading, null, null, "Other port hold posted")
                            }
                            else {
                                Serializable vetoGkey = servicesManager.applyPermission("OTHER_PORT_RELEASE", billOfLading, null, null, "Other port hold release")
                            }
                            RoadBizUtil.commit()
                        }
                        else {
                            registerWarning(PropertyKeyFactory.valueOf("OTHER_PORT_MSG"),"No BL found, messages received is for other facility")
                        }
                    }
                }
            }
        }
        else {
            throw BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                    "Please configure port Code:" + ediCode + " in General Reference, Type:EDI, Identifier1:350 and Identifier2:PORT_CODES");
        }
    }

    /**
     * Get the port codes from General Reference.
     */
    private static List<String> getPortCodesFromGeneralReference(GeneralReference inGeneralReference) {
        List<String> portCodesList = new ArrayList()
        String[] dataValueArray = new String[6]
        dataValueArray[0] = inGeneralReference.getRefValue1()
        dataValueArray[1] = inGeneralReference.getRefValue2()
        dataValueArray[2] = inGeneralReference.getRefValue3()
        dataValueArray[3] = inGeneralReference.getRefValue4()
        dataValueArray[4] = inGeneralReference.getRefValue5()
        dataValueArray[5] = inGeneralReference.getRefValue6()
        for (String dataValue : dataValueArray) {
            if (dataValue != null) {
                String[] arrayOfStrings = dataValue.split(',')
                for (int i = 0; i < arrayOfStrings.length; i++) {
                    arrayOfStrings[i] = arrayOfStrings[i].trim()
                }
                portCodesList.addAll(new ArrayList(asList(arrayOfStrings)))
            }
        }
        return portCodesList
    }

    /**
     * Method to validate the routing point(Schedule D code) of the facility is same as the value of ediReleaseFlexString01 in message.
     */
    private void validateRoutingPoint(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, Map map) {
        if (releaseTransaction.getEdiReleaseFlexFields() != null && releaseTransaction.getEdiReleaseFlexFields().getEdiReleaseFlexString01() != null) {
            String facilityId = (String) ArgoEdiUtils.getConfigValue(ContextHelper.getThreadEdiPostingContext(), ArgoConfig.EDI_FACILITY_FOR_POSTING)

            if (!StringUtils.isEmpty(facilityId)) {
                Facility facility = Facility.findFacility(facilityId, ContextHelper.getThreadComplex())
                if (facility == null) {
                    map.put(SKIP_POSTER, true)
                    throw BizViolation.create(PropertyKeyFactory.valueOf("Facility cannot found for the Id: " + facilityId), null)
                }
                String scheduledDCode = facility.getFcyRoutingPoint().getPointScheduleDCode()
                String messageScheduleDCode = releaseTransaction.getEdiReleaseFlexFields().getEdiReleaseFlexString01()
                if (!messageScheduleDCode.equals(scheduledDCode)) {
                    map.put(SKIP_POSTER, true)
                    throw BizViolation
                            .create(PropertyKeyFactory.valueOf("EDI release message is not for this port:" + scheduledDCode +
                                    "  not matching with message schedule D code:" + messageScheduleDCode), null)
                }
            }
            else {
                map.put(SKIP_POSTER, true)
                throw BizViolation.create(PropertyKeyFactory.valueOf("EDI_FACILITY_FOR_POSTING has empty or null facility Id"), null)
            }
        }
    }

    /**
     * Update the inbond status to the BL and units
     */
    private void inbondStatusUpdate(BillOfLading billOfLading) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, billOfLading.getPrimaryKey()))
                .addDqPredicate(PredicateFactory.in(InventoryCargoField.BLREL_DISPOSITION_CODE, ["1J", "95"]))
                .addDqAggregateField(AggregateFunctionType.SUM, InventoryCargoField.BLREL_QUANTITY)
        Double totalInbondQty = QueryUtil.getQuerySum(dq)
        if (billOfLading.getBlManifestedQty() > 0) {
            if (totalInbondQty != null && totalInbondQty > 0 && totalInbondQty >= billOfLading.getBlManifestedQty()) {
                billOfLading.setBlInbond(InbondEnum.INBOND)
            } else {
                billOfLading.setBlInbond(null)
            }
            billOfLading.updateUnitInbondStatus()
        }
        billOfLading.setBlFlexDouble02(totalInbondQty)
        HibernateApi.getInstance().save(billOfLading)
    }

    /**
     * Update the Exam status to the BL and units
     */
    private void UpdateExamStatus(BillOfLading billOfLading, Boolean isSample, Boolean isSampleCancel) {
        if (isSample) {
            DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                    .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, billOfLading.getPrimaryKey()))
                    .addDqPredicate(PredicateFactory.in(InventoryCargoField.BLREL_DISPOSITION_CODE, ["1X"]))
                    .addDqAggregateField(AggregateFunctionType.SUM, InventoryCargoField.BLREL_QUANTITY)
            Double totalExamQty = QueryUtil.getQuerySum(dq)
            if (isSampleCancel) {
                billOfLading.setBlExam(null)
                billOfLading.setBlFlexDouble04(0.0);
                billOfLading.setBlFlexString01("")
            } else {
                billOfLading.setBlExam(ExamEnum.OFFSITE)
                billOfLading.setBlFlexDouble04(totalExamQty)
                billOfLading.setBlFlexString01("S")
            }
        } else {
            DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                    .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, billOfLading.getPrimaryKey()))
                    .addDqPredicate(PredicateFactory.in(InventoryCargoField.BLREL_DISPOSITION_CODE, ["1W", "83"]))
                    .addDqAggregateField(AggregateFunctionType.SUM, InventoryCargoField.BLREL_QUANTITY)
            Double totalExamQty = QueryUtil.getQuerySum(dq)
            if (billOfLading.getBlManifestedQty() > 0) {
                if (totalExamQty != null && totalExamQty > 0 && totalExamQty >= billOfLading.getBlManifestedQty()) {
                    billOfLading.setBlExam(ExamEnum.OFFSITE)
                }
                else {
                    billOfLading.setBlExam(null)
                }
                billOfLading.updateUnitExamStatus()
            }
            billOfLading.setBlFlexDouble03(totalExamQty)
        }
        HibernateApi.getInstance().save(billOfLading)
    }
    /**
     * Cancel active 1J using 95 disposition code
     */
    private void handle1JUsing95(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, BillOfLading billOfLading) throws BizViolation {
        String referenceId = releaseTransaction.getReleaseReferenceId()
        List nintyFiveBlReleases = findBlReleases(billOfLading.getBlGkey(), "95")


        if (!nintyFiveBlReleases.isEmpty()) {
            BlRelease nintyFiveBlRelease = null
            for (BlRelease release : nintyFiveBlReleases) {
                if (isBlReleaseCanceled(billOfLading.getBlGkey(), release.getBlrelGkey())) {
                    continue
                }
                nintyFiveBlRelease = release;
                break
            }
            if (nintyFiveBlRelease == null) {
                return
            }
            boolean isMatchByRef = isQtyMatchByReference(releaseTransaction)
            if ("1J".equalsIgnoreCase(releaseTransaction.getEdiCode())) {
                referenceId = nintyFiveBlRelease.getBlrelReferenceNbr()
            }

            if (referenceId == null && isMatchByRef) {
                LOG.error("Active 1J could not be cancelled because the reference Id is null and MatchQtyByReference is selected in the release map LOV")
                return
            }
            List<BlRelease> blRel = active1JFinding(releaseTransaction, billOfLading.getBlGkey(), referenceId)
            for (BlRelease release1J : blRel) {
                release1J.setFieldValue(InventoryCargoField.BLREL_REFERENCE, nintyFiveBlRelease)
                nintyFiveBlRelease.setFieldValue(InventoryCargoField.BLREL_REFERENCE, release1J)
                HibernateApi.getInstance().save(release1J)
                HibernateApi.getInstance().save(nintyFiveBlRelease)
            }
            if (!blRel.isEmpty()) {
                rcdSrvEvent(billOfLading, CANCELED_EVENT_1J_STR)
            }
        }
    }

    private void handling1W(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, BillOfLading billOfLading) throws BizViolation {
        String referenceId = releaseTransaction.getReleaseReferenceId()
        Double ediRelQty = (releaseTransaction.getReleaseQty() != null) ? Double.valueOf(releaseTransaction.getReleaseQty()) : 0.0
        List blReleases83 = findBlReleases(billOfLading.getBlGkey(), "83")

        if (!blReleases83.isEmpty()) {
            BlRelease blRelease83 = null
            for (BlRelease release : blReleases83) {
                if (isBlReleaseCanceled(billOfLading.getBlGkey(), release.getBlrelGkey())) {
                    continue
                }
                blRelease83 = release
                break
            }
            if (blRelease83 == null) {
                return
            }

            boolean isMatchByRef = isQtyMatchByReference(releaseTransaction)
            if ("1W".equalsIgnoreCase(releaseTransaction.getEdiCode())) {
                referenceId = blRelease83.getBlrelReferenceNbr()
            }

            if (referenceId == null && isMatchByRef) {
                LOG.error("Could not cancel Active 1W since reference Id is null and MatchQtyByReference is selected in release map LOV.")
                return
            }

            List<BlRelease> blRel = active1WFinding(releaseTransaction, billOfLading.getBlGkey(), referenceId)
            for (BlRelease release1W : blRel) {
                if (!blRel.isEmpty()) {
                    rcdSrvEvent(billOfLading, CANCELED_EVENT_1W_STR)
                    if ("83".equalsIgnoreCase(releaseTransaction.getEdiCode())) {
                        if (ediRelQty > 0) {
                            ediRelQty = ediRelQty - (ediRelQty * 2)
                        }
                        blRelease83.setFieldValue(InventoryCargoField.BLREL_QUANTITY, ediRelQty)
                        HibernateApi.getInstance().save(blRelease83)
                    }
                }
                release1W.setFieldValue(InventoryCargoField.BLREL_REFERENCE, blRelease83)
                HibernateApi.getInstance().save(release1W)
            }
        }
    }

    /**
     * Find Line Operator
     */
    private ScopedBizUnit findLineOperator(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction) {
        if (releaseTransaction.getEdiShippingLine() != null) {
            String lineCode = releaseTransaction.getEdiShippingLine().getShippingLineCode()
            String lineCodeAgency = releaseTransaction.getEdiShippingLine().getShippingLineCodeAgency()
            return ScopedBizUnit.resolveScopedBizUnit(lineCode, lineCodeAgency, BizRoleEnum.LINEOP)
        }
        return null
    }

    /**
     * Check BlRelease is canceled by another Bl release
     */
    private boolean isBlReleaseCanceled(Serializable blGkey, Serializable blRelGkey) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, blGkey))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_REFERENCE, blRelGkey))
        return HibernateApi.getInstance().existsByDomainQuery(dq)
    }
    /**
     * Record BOL Event
     */

    private void rcdSrvEvent(BillOfLading billOfLading, String eventId) {
        EventType eventType = EventType.findOrCreateEventType(eventId, "Customs Event", LogicalEntityEnum.BL, null)
        FieldChanges fld = new FieldChanges()
        fld.setFieldChange(InventoryCargoField.BL_GKEY, billOfLading.getBlGkey())
        fld.setFieldChange(InventoryCargoField.BL_NBR, billOfLading.getBlNbr())
        fld.setFieldChange(InventoryCargoField.BL_INBOND, billOfLading.getBlInbond())
        if (eventType != null) {
            billOfLading.recordBlEvent(eventType, fld, "recorded through groovy", null)
        }
    }

    /**
     * Find bl releases using posting date and disposition code
     */
    private List<BlRelease> findBlReleases(Serializable blGkey, String dispositionCode) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, blGkey))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, dispositionCode))
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
    }

    /**
     * Find bl releases using posting date and disposition code
     */
    private List<BlRelease> findBlReleases(Serializable blGkey, String dispositionCode, Date postedDate) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, blGkey))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, dispositionCode))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_POST_DATE, postedDate))
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
    }
    /**
     * find active 1J BlReleases
     */
    private List<BlRelease> active1JFinding(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, Serializable blGkey,
                                            String referenceId) throws BizViolation {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, blGkey))
                .addDqPredicate(PredicateFactory.isNull(InventoryCargoField.BLREL_REFERENCE))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, "1J"))
        boolean isQtyMatchByReferenceNbr = isQtyMatchByReference(releaseTransaction)
        if (isQtyMatchByReferenceNbr) {
            dq.addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_REFERENCE_NBR, referenceId))
        }
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
    }

    /**
     * find active 1J BlReleases
     */
    private List<BlRelease> active1WFinding(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, Serializable blGkey,
                                            String referenceId) throws BizViolation {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, blGkey))
                .addDqPredicate(PredicateFactory.isNull(InventoryCargoField.BLREL_REFERENCE))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, "1W"))
        boolean isQtyMatchByReferenceNbr = isQtyMatchByReference(releaseTransaction)
        if (isQtyMatchByReferenceNbr) {
            dq.addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_REFERENCE_NBR, referenceId))
        }
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
    }

    /**
     * return true if match qty is "Match Qty By Reference" ion release map configuration
     */
    private boolean isQtyMatchByReference(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction) throws BizViolation {
        IReleaseMap releaseMap = releaseMapsFor95(releaseTransaction)
        return releaseMap == null ? false : EdiReleaseMapQuantityMatchEnum.MatchQtyByReference.equals(releaseMap.getEdirelmapMatchQty())
    }

    /**
     * find all release map for given disposition code and message type. Extract the release by BL hold/perm.
     */
    private IReleaseMap releaseMapsFor95(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction) throws BizViolation {
        ArgoEdiFacade ediFacade = (ArgoEdiFacade) Roastery.getBean(ArgoEdiFacade.BEAN_ID)
        Set ediCodeSet = new HashSet()
        ediCodeSet.add("95")
        List<IReleaseMap> releaseMaps =
                ediFacade.findEdiReleaseMapsForEdiCodes(releaseTransaction.getMsgTypeId(), ediCodeSet, releaseTransaction.getMsgVersion(), releaseTransaction.getMsgReleaseNbr(), LogicalEntityEnum.BL)
        String msg = "Map Code: " + releaseTransaction.getEdiCode() + " Message Id: " + releaseTransaction.getMsgTypeId() + ", Message Version: " + releaseTransaction.getMsgVersion() +
                ", Release Number: " + releaseTransaction.getMsgReleaseNbr()
        if (releaseMaps.isEmpty()) {
            throw BizViolation.create(PropertyKeyFactory.valueOf("Release map for the condition cannot found: " + msg), null)
        }

        if (releaseMaps.size() > 1) {
            throw BizViolation.create(PropertyKeyFactory.valueOf("Multiple release maps found for the condition: " + msg), null)
        }
        return releaseMaps.get(0)
    }


    /** Update the vessel visit of the BL if it is linked to GEN_VESSEL and Release has a valid vessel visit.
     */
    private void updateBlCarrierVisit(BillOfLading billOfLading, Map inParams) {
        if (billOfLading.getBlCarrierVisit().isGenericCv()) {
            try {
                Serializable batchGkey = (Serializable) inParams.get(EdiConsts.BATCH_GKEY)
                LOG.warn("BL has generic Carrier visit ")
                if (batchGkey != null) {
                    EdiBatch ediBatch = EdiBatch.hydrate(batchGkey)
                    if (ediBatch != null && ediBatch.getEdibatchCarrierVisit() != null &&
                            !ediBatch.getEdibatchCarrierVisit().isGenericCv()) {
                        LOG.warn("Updating BL Carrier visit ")
                        billOfLading.setBlCarrierVisit(ediBatch.getEdibatchCarrierVisit())
                        HibernateApi.getInstance().save(billOfLading)
                    }
                }
            } catch (Exception e) {
                LOG.warn("Error while trying to update BL Carrier visit")
            }
        }
    }
    /**
     * Checking same ediCode,interchangeNumber,release postdate,msgReferenceNbr exist already
     */
    private boolean hasInterchangeAndEdiCode(String nbr, String code, String msgRefNbr, String blNbr, String postdate, Serializable tranGkey) {

        DomainQuery dq = QueryUtils.createDomainQuery(EdiEntity.EDI_TRANSACTION)
                .addDqPredicate(PredicateFactory.eq(BATCH_INTERCHANGE_NBR, nbr))
                .addDqPredicate(PredicateFactory.eq(EdiField.EDITRAN_KEYWORD_VALUE4, code))
                .addDqPredicate(PredicateFactory.eq(EDITRAN_MSG_REF_NBR, msgRefNbr))
                .addDqPredicate(PredicateFactory.eq(EdiField.EDITRAN_PRIMARY_KEYWORD_VALUE, blNbr))
                .addDqPredicate(PredicateFactory.in(EdiField.EDITRAN_STATUS, [EdiStatusEnum.COMPLETE, EdiStatusEnum.WARNINGS]))
                .addDqPredicate(PredicateFactory.ne(EdiField.EDITRAN_GKEY, tranGkey))
                .addDqField(EdiField.EDITRAN_DOC)
        QueryResult result = HibernateApi.getInstance().findValuesByDomainQuery(dq)
        if (result != null && result.getTotalResultCount() > 0) {
            for (int i = 0; i < result.getTotalResultCount(); i++) {
                String tranDoc = (String) result.getValue(i, EdiField.EDITRAN_DOC);
                String releasePostdate = XmlUtil.extractAttributeValueFromXml("releasePostDate", tranDoc)
                if (releasePostdate != null && postdate.equals(releasePostdate)) {
                    return true
                }

            }
        }
        return false
    }

    private void registerWarning(PropertyKey propertyKey, String inMsg){
        if (ContextHelper.getThreadMessageCollector()!= null) {
            ContextHelper.getThreadMessageCollector().appendMessage(MessageLevel.WARNING, propertyKey, inMsg , null)
        }
    }
    private final String CANCELED_EVENT_1J_STR = "CANCELLED_1J"
    private final String CANCELED_EVENT_1W_STR = "CANCELLED_1W"
    {
        EventType.findOrCreateEventType(CANCELED_EVENT_1J_STR, "1J Cancelled Event", LogicalEntityEnum.BL, null)
    }
    private final String BATCH_GKEY = "BATCH_GKEY"
    private static MetafieldId BATCH_INTERCHANGE_NBR = MetafieldIdFactory.valueOf("editranBatch.edibatchInterchange.ediintInterchangeNbr")
    private static MetafieldId EDITRAN_MSG_REF_NBR = MetafieldIdFactory.valueOf("editranBatch.edibatchMsgRefNbr")
    private final String SKIP_POSTER = "SKIP_POSTER"
    private static final Logger LOG = Logger.getLogger(ITSUSCustomsBLReleaseGvy.class)
}
