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
import com.navis.edi.business.entity.EdiInterchange
import com.navis.edi.business.entity.EdiReleaseMap
import com.navis.edi.business.util.XmlUtil
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.HibernatingEntity
import com.navis.framework.portal.FieldChanges
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.AggregateFunctionType
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.query.common.api.QueryResult
import com.navis.framework.util.BizViolation
import com.navis.framework.util.BizWarning
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageLevel
import com.navis.road.business.util.RoadBizUtil
import com.navis.services.ServicesField
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
public class ITSUSCustomsBLReleaseGvy extends AbstractEdiPostInterceptor {

    public void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        LOGGER.setLevel(Level.INFO)
        if (inXmlTransactionDocument == null || !ReleaseTransactionsDocument.class.isAssignableFrom(inXmlTransactionDocument.getClass())) {
            return
        }
        ReleaseTransactionsDocument releaseDoc = (ReleaseTransactionsDocument) inXmlTransactionDocument
        ReleaseTransactionsDocument.ReleaseTransactions releaseTransactions = releaseDoc.getReleaseTransactions()
        ReleaseTransactionDocument.ReleaseTransaction[] releaseArray = releaseTransactions.getReleaseTransactionArray(0)

        if (releaseArray == null || releaseArray.length == 0) {
            LOGGER.error("Release Array value is null")
            return;
        }

        ReleaseTransactionDocument.ReleaseTransaction releaseTransact = releaseArray[0]
        validateRoutingPoint(releaseTransact, inParams)
        skipDuplicateCustomsMsg(releaseTransact, inParams)
        isReleaseForOtherCode(releaseTransact, inParams, releaseTransact.getEdiCode())

        Object postdate = releaseTransact.getReleasePostDate()
        EdiReleaseIdentifier releaseIdentifier = releaseTransact?.getEdiReleaseIdentifierList()?.get(0)
        String blNbr = releaseIdentifier?.getReleaseIdentifierNbr()
        Serializable batchGkey = (Serializable) inParams.get(BATCH_GKEY)
        Serializable transGkey = (Serializable) inParams.get(EdiConsts.TRANSACTION_GKEY)
        EdiBatch batch = EdiBatch.hydrate(batchGkey)
        if (batch != null) {
            EdiInterchange ediBatchInterchange = batch.getEdibatchInterchange()
            if (ediBatchInterchange?.getEdiintInterchangeNbr() != null && releaseTransact?.getEdiCode() != null && batch.getEdibatchMsgRefNbr() != null && blNbr != null) {
                LOGGER.info("code::" + releaseTransact?.getEdiCode() + "ediInterchange::" + ediBatchInterchange + "msgRefNbr::" + batch.getEdibatchMsgRefNbr() + "blNbr:" + blNbr + "tranGkey :"+transGkey)
                if (postdate.toString() != null && hasInterchangeAndEdiCode(ediBatchInterchange?.getEdiintInterchangeNbr(), releaseTransact?.getEdiCode(), batch.getEdibatchMsgRefNbr(), blNbr, postdate.toString(), transGkey)) {
                    inParams.put(SKIP_POSTER, true)
                    BizViolation warning = BizWarning.create(ArgoPropertyKeys.INFO, null, "Skipped posting for ${blNbr} and edi Code as the Transaction is a Duplicate.")
                    getMessageCollector().appendMessage(warning)
                }
            }
        }
        /**
         If 5H is not only an information message, uncomment the following method.
         You must also uncomment the handle5H5I4ALogicAfterPost() method call in afterEdiPost() if you uncomment this method call.
         */

        if ("5H".equalsIgnoreCase(releaseTransact.getEdiCode()) || "5I".equalsIgnoreCase(releaseTransact.getEdiCode()) || "4A".equalsIgnoreCase(releaseTransact.getEdiCode())) {
            handle5H5I4ABeforePost(releaseTransact, releaseTransact.getEdiCode())
        }
        /**
         If the information message, for instance 1h, 2h, 7h..., lacks a guaranteed unique reference number and your message is anticipated to apply many holds, uncomment the technique below.
         Please take note of the following:
         1) If you uncomment this method call, you must also uncomment the call to setBackTransactionReferenceId () in afterEdiPost().

         2) If the customer is running 2.3-rel or an earlier version, this should be left uncommented.
         */

        if (DISPOSITION_CODES_FOR_UNIQUE_ID.indexOf(releaseTransact.getEdiCode()) >= 0) {
            settingUniqueReferenceId(releaseTransact, releaseTransact.getEdiCode())
        }

        if ("1J".equalsIgnoreCase(releaseTransact.getEdiCode()) || "1C".equalsIgnoreCase(releaseTransact.getEdiCode()) || "95".equalsIgnoreCase(releaseTransact.getEdiCode()) || "4E".equalsIgnoreCase(releaseTransact.getEdiCode())) {
            isReleaseForTrans(releaseTransact, inParams, releaseTransact.getEdiCode())
        }
    }
    /**
     * Verify to see if the release notice is for another facility.
     */
    private void isReleaseForTrans(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, Map inParams, String inEdiCode) {
        GeneralReference genRef = GeneralReference.findUniqueEntryById("EDI", "350", "PORT_CODES")
        if (genRef == null) {
            throw BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                    "Please configure port Code:" + inEdiCode + " in General Reference, Type:EDI, Identifier1:350 and Identifier2:PORT_CODES")
        }
        List<String> portCodeList = getPortCodes(genRef)
        if (releaseTransaction.getEdiReleaseIdentifierList() != null && !releaseTransaction.getEdiReleaseIdentifierList().isEmpty()) {
            EdiReleaseIdentifier releaseIdentifier = releaseTransaction.getEdiReleaseIdentifierList().get(0)
            BillOfLading billOfLading = null
            if (releaseIdentifier.getReleaseIdentifierNbr() != null) {
                LineOperator lineOp = (LineOperator) findLineOperator(releaseTransaction)
                billOfLading = BillOfLading.findBillOfLading(releaseIdentifier.getReleaseIdentifierNbr(), lineOp, null)
            }
            if (billOfLading != null && releaseTransaction.getVesselCallFacility() != null && releaseTransaction.getVesselCallFacility().getFacilityPort() != null &&
                    releaseTransaction.getVesselCallFacility().getFacilityPort().getPortCodes() != null) {
                String portCode = releaseTransaction.getVesselCallFacility().getFacilityPort().getPortCodes().getId()
                if (!portCodeList.contains(portCode)) {
                    inParams.put(SKIP_POSTER, true)
                    Double releaseQty = Double.valueOf(releaseTransaction.getReleaseQty())
                    BlRelease blRelease = new BlRelease()
                    blRelease.setBlrelReferenceNbr(releaseTransaction.getReleaseReferenceId())
                    blRelease.setBlrelDispositionCode(releaseTransaction.getEdiCode())
                    blRelease.setBlrelQuantityType(EdiReleaseMapModifyQuantityEnum.InformationOnly)
                    blRelease.setBlrelNotes(releaseTransaction.getReleaseNotes())
                    Object postDate = releaseTransaction.getReleasePostDate()
                    Date releasePostDate = null
                    if (postDate != null && postDate instanceof Calendar) {
                        releasePostDate = ArgoEdiUtils.convertLocalToUtcDate(postDate, ContextHelper.getThreadEdiPostingContext().getTimeZone())
                    }
                    blRelease.setBlrelQuantity(0.0)
                    blRelease.setBlrelPostDate(releasePostDate)
                    if (releaseQty > 0 && (inEdiCode != null && (inEdiCode.equals("4E") || inEdiCode.equals("95")))) {
                        releaseQty = releaseQty *-1
                    }
                    blRelease.setFieldValue(MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherQty"), releaseQty)
                    blRelease.setFieldValue(MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherPort"), portCode)
                    blRelease.setBlrelBl(billOfLading)
                    HibernateApi.getInstance().save(blRelease)
                    RoadBizUtil.commit()
                    DomainQuery domainQuery = QueryUtils.createDomainQuery("BlRelease")
                            .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, billOfLading.getPrimaryKey()))
                            .addDqPredicate(PredicateFactory.isNotNull(MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherQty")))
                            .addDqAggregateField(AggregateFunctionType.SUM,MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherQty"))
                    Double totalOtherQty = QueryUtil.getQuerySum(domainQuery)
                    billOfLading.setBlFlexDouble01(totalOtherQty)
                    HibernateApi.getInstance().save(billOfLading)
                    ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
                    if (totalOtherQty >0) {
                        servicesManager.applyHold(OTHER_PORT_RELEASE, billOfLading, null, null, "Other port hold posted")
                    } else {
                        servicesManager.applyPermission(OTHER_PORT_RELEASE, billOfLading,null, null, "Other port hold release")
                    }
                    RoadBizUtil.commit()
                }
            }
        }
    }
    /**
     * Consult the General Reference for the port codes
     */
    private static List<String> getPortCodes(GeneralReference generalReference) {
        List<String> codesList = new ArrayList()
        String[] dataArray = new String[6]
        dataArray[0] = generalReference.getRefValue1()
        dataArray[1] = generalReference.getRefValue2()
        dataArray[2] = generalReference.getRefValue3()
        dataArray[3] = generalReference.getRefValue4()
        dataArray[4] = generalReference.getRefValue5()
        dataArray[5] = generalReference.getRefValue6()
        for (String data : dataArray) {
            if (data == null) {
                continue
            }
            String[] stringArray = data.split(',')
            for (int i = 0; i < stringArray.length; i++) {
                stringArray[i] = stringArray[i].trim()
            }
            codesList.addAll(new ArrayList(asList(stringArray)))
        }
        return codesList
    }
    /**
     * This method is being used in the beforeEdiPost method
     * The method to validate the routing point(Schedule D code) of the facility is the same as the value of ediReleaseFlexString01 in the message.
     */
    private void validateRoutingPoint(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, Map inParams) {
        EdiReleaseFlexFields releaseFlexFields = releaseTransaction.getEdiReleaseFlexFields()
        if (releaseFlexFields != null && releaseFlexFields.getEdiReleaseFlexString01() != null) {

            String facilityId = (String) ArgoEdiUtils.getConfigValue(ContextHelper.getThreadEdiPostingContext(), ArgoConfig.EDI_FACILITY_FOR_POSTING)
            if (StringUtils.isEmpty(facilityId)) {
                inParams.put(SKIP_POSTER, true)
                throw BizViolation.create(PropertyKeyFactory.valueOf("When setting, FacilityId is empty or null. EDI_FACILITY_FOR_POSTING"), null)
            }
            Facility facility = Facility.findFacility(facilityId, ContextHelper.getThreadComplex())
            if (facility == null) {
                inParams.put(SKIP_POSTER, true)
                throw BizViolation.create(PropertyKeyFactory.valueOf("Unable to find the Facility for Id: " + facilityId), null)
            }
            String messageScheduleDCode = releaseFlexFields.getEdiReleaseFlexString01()
            if (!messageScheduleDCode.equals(facility.getFcyRoutingPoint().getPointScheduleDCode())) {
                inParams.put(SKIP_POSTER, true)
                throw BizViolation
                        .create(PropertyKeyFactory.valueOf("EDI release message is not for this port, port schedule Dcode:" + facility.getFcyRoutingPoint().getPointScheduleDCode() +
                                "  does not match with message schedule D code:" + messageScheduleDCode), null)
            }
        }
    }

    @Override
    void afterEdiPost(XmlObject inXmlTransactionDocument, HibernatingEntity inHibernatingEntity, Map inParams) {

        if (Boolean.TRUE.equals(inParams.get(SKIP_POSTER))) {
            LOGGER.info("Skipped after EDI_POST method.")
            return
        }
        if (inXmlTransactionDocument == null || !ReleaseTransactionsDocument.class.isAssignableFrom(inXmlTransactionDocument.getClass())) {
            return
        }
        ReleaseTransactionsDocument transactionsDocument = (ReleaseTransactionsDocument) inXmlTransactionDocument
        ReleaseTransactionsDocument.ReleaseTransactions releaseTrans = transactionsDocument.getReleaseTransactions()
        ReleaseTransactionDocument.ReleaseTransaction[] releaseTransArray = releaseTrans.getReleaseTransactionArray(0)
        if (releaseTransArray == null || releaseTransArray.length == 0) {
            LOGGER.info("The Release Array is NULL after the EDI_POST method.")
            return
        }
        ReleaseTransactionDocument.ReleaseTransaction releaseTransaction = releaseTransArray[0]
        String ediCode = releaseTransaction.getEdiCode()
        LOGGER.info("EDI CODE: " + ediCode)
        if (ediCode == null) {
            return
        }

        HibernateApi.getInstance().flush()
        BillOfLading billOfLading = null
        String blNbr = releaseTransaction.getEdiReleaseIdentifierArray(0).getReleaseIdentifierNbr()
        if (blNbr != null) {
            LineOperator lineOp = (LineOperator) findLineOperator(releaseTransaction)
            billOfLading = BillOfLading.findBillOfLading(blNbr, lineOp, null)
        }
        if (ArgoUtils.isEmpty(blNbr) || billOfLading == null) {
            LOGGER.error("BlNbr Bill of Lading not found: " + blNbr)
            return
        }

        if ("55".equals(ediCode)) {
            handling55(releaseTransaction, billOfLading)
        }
        inboundStatusUpdating(ediCode, billOfLading)

        if ("95".equals(ediCode) || "1J".equalsIgnoreCase(ediCode)) {
            if ("1J".equalsIgnoreCase(ediCode)) {
                rcdSrvEvent(billOfLading, ADD_EVENT_1J_STR)
                inboundStatusUpdating(billOfLading)
            }
            handling1JUsing95(releaseTransaction, billOfLading)
            UpdateInbondStatus(billOfLading)
        }

        if ("83".equals(ediCode) || "1W".equalsIgnoreCase(ediCode)) {
            if ("1W".equalsIgnoreCase(ediCode)) {
                rcdSrvEvent(billOfLading, ADD_EVENT_1W_STR)
                inboundStatusUpdating(billOfLading)
            }
            handlingOf1WUsing83(releaseTransaction, billOfLading)
            UpdateExamStatus(billOfLading, Boolean.FALSE, Boolean.FALSE)
        }
        if ("1X".equalsIgnoreCase(ediCode)) {
            UpdateExamStatus(billOfLading, Boolean.TRUE, Boolean.FALSE)
        } else if ("84".equals(ediCode)) {
            UpdateExamStatus(billOfLading, Boolean.TRUE, Boolean.TRUE)
        }

        if ("7H".equalsIgnoreCase(ediCode)) {
            billOfLading.setBlFlexString03("7H")
        }
        if ("7I".equalsIgnoreCase(ediCode)) {
            billOfLading.setBlFlexString03("")
        }
        updateBlCarrierVisit(billOfLading, inParams)

        if (("1C".equalsIgnoreCase(ediCode) || "1W".equalsIgnoreCase(ediCode)) && hasActive1J(billOfLading.getBlGkey())) {
            inboundStatusUpdating(billOfLading)
        }

        /**
         Before uncommenting this method call, please see the comments that are given for handle5H5ILogic BeforePost() is called beforeEdiPost().
         * */
        if ("5H".equalsIgnoreCase(ediCode) || "5I".equalsIgnoreCase(ediCode) || "4A".equalsIgnoreCase(ediCode)) {
            handling5H5I4ALogicAfterPost(releaseTransaction, ediCode)
        }
        if ("4E".equalsIgnoreCase(ediCode)) {
            handling4E(billOfLading, releaseTransaction.getReleaseReferenceId())
        }
        LOGGER.info(
                "MoveFlexToRemarks() :Class of inHibernatingEntity is " + inHibernatingEntity.getClass().name)
        inboundStatusUpdating(ediCode, billOfLading)
        applyFlexToRemarks(releaseTransaction, billOfLading, ediCode)

        if (DISPOSITION_CODES_FOR_UNIQUE_ID.indexOf(ediCode) >= 0) {
            HibernateApi.getInstance().flush()
            setBackTransactionReferenceId(releaseTransaction, billOfLading, ediCode)
        }

        /**
         * Run the BL hold release process.
         */

        if (releaseTransaction.getVesselCallFacility()) {
            releaseGeneralReferenceHolds(ediCode, releaseTransaction.getVesselCallFacility(), billOfLading, releaseTransaction.getReleaseReferenceId())
        }

        updateBlCarrierVisit(billOfLading,inParams)
        if (["1W","83"].contains(ediCode.toUpperCase())) {
            billOfLading.blFlexString01 = (ediCode == "83" ? null : "Yes")
            HibernateApi.getInstance().save(billOfLading)
        }
    }

    //Private Methods

    private void skipDuplicateCustomsMsg(ReleaseTransactionDocument.ReleaseTransaction inReleaseTransaction, Map inParams) {
        String blNbr = inReleaseTransaction?.getEdiReleaseIdentifierList()?.get(0)?.getReleaseIdentifierNbr()
        Serializable batchGKey = (Serializable) inParams.get(BATCH_GKEY)
        Serializable tranGKey = (Serializable) inParams.get(EdiConsts.TRANSACTION_GKEY)
        EdiBatch batch = EdiBatch.hydrate(batchGKey)
        if (batch != null) {
            String msgRefNbr = batch.getEdibatchMsgRefNbr()
            EdiInterchange ediInterchange = batch.getEdibatchInterchange()
            String interchangeNbr = ediInterchange?.getEdiintInterchangeNbr()
            String code = inReleaseTransaction?.getEdiCode()
            if (interchangeNbr != null && code != null && msgRefNbr != null && blNbr != null) {
                LOGGER.info("code::" + code + "ediInterchange::" + ediInterchange + "msgRefNbr::" + msgRefNbr + "blNbr:" + blNbr + "tranGKey :" + tranGKey)
                if (inReleaseTransaction.getReleasePostDate()?.toString() != null && hasInterchangeAndEdiCode(interchangeNbr, code, msgRefNbr, blNbr, inReleaseTransaction.getReleasePostDate()?.toString(), tranGKey)) {
                    inParams.put(SKIP_POSTER, true)
                    BizViolation warning = BizWarning.create(ArgoPropertyKeys.INFO, null, "Skipped posting for ${blNbr} and edi Code ${code} as the Transaction is a Duplicate.")
                    getMessageCollector().appendMessage(warning)
                }
            }
        }
    }

    private void isReleaseForOtherCode(ReleaseTransactionDocument.ReleaseTransaction inReleaseTransaction, Map inParams, String inEdiCode) {
        GeneralReference generalReference = GeneralReference.findUniqueEntryById("EDI", "350", "PORT_CODES")
        if (generalReference == null) {
            throw BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                    "Please configure port Code:" + inEdiCode + " in General Reference, Type:EDI, Identifier1:350 and Identifier2:PORT_CODES")
        }

        List<String> portCodeList = getPortCodesFromGeneralReference(generalReference);
        if (inReleaseTransaction.getEdiReleaseIdentifierList() != null && !inReleaseTransaction.getEdiReleaseIdentifierList().isEmpty()) {
            EdiReleaseIdentifier releaseIdentifier = inReleaseTransaction.getEdiReleaseIdentifierList().get(0)
            BillOfLading bl = null
            if (releaseIdentifier.getReleaseIdentifierNbr() != null) {
                LineOperator lineOp = (LineOperator) findLineOperator(inReleaseTransaction)
                bl = BillOfLading.findBillOfLading(releaseIdentifier.getReleaseIdentifierNbr(), lineOp, null)

            }

            if (inReleaseTransaction.getVesselCallFacility() != null && inReleaseTransaction.getVesselCallFacility().getFacilityPort() != null &&
                    inReleaseTransaction.getVesselCallFacility().getFacilityPort().getPortCodes() != null) {
                String portCode = inReleaseTransaction.getVesselCallFacility().getFacilityPort().getPortCodes().getId()
                if (portCode != null && !portCodeList.contains(portCode)) {
                    inParams.put(SKIP_POSTER, true)
                    if (bl == null) {
                        registerWarning(PropertyKeyFactory.valueOf("OTHER_PORT_MSG"),"Message received for other port, no BL exists")
                    }
                    if (bl != null) {
                        Double releaseQty = Double.valueOf(inReleaseTransaction.getReleaseQty())
                        BlRelease blRelease = new BlRelease()
                        blRelease.setBlrelReferenceNbr(inReleaseTransaction.getReleaseReferenceId())
                        blRelease.setBlrelDispositionCode(inReleaseTransaction.getEdiCode())
                        blRelease.setBlrelQuantityType(EdiReleaseMapModifyQuantityEnum.InformationOnly)
                        blRelease.setBlrelNotes(inReleaseTransaction.getReleaseNotes())
                        Object postDate = inReleaseTransaction.getReleasePostDate()
                        Date releasePostDate = null
                        if (postDate != null && postDate instanceof Calendar) {
                            releasePostDate = ArgoEdiUtils.convertLocalToUtcDate(postDate, ContextHelper.getThreadEdiPostingContext().getTimeZone())
                        }
                        blRelease.setBlrelQuantity(0.0)
                        blRelease.setBlrelPostDate(releasePostDate)
                        if (releaseQty > 0 && (inEdiCode != null && (inEdiCode.equals("4E") || inEdiCode.equals("95")))) {
                            releaseQty = releaseQty * -1
                        }
                        blRelease.setFieldValue(MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherQty"), releaseQty)
                        blRelease.setFieldValue(MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherPort"), portCode)
                        blRelease.setBlrelBl(bl)
                        HibernateApi.getInstance().save(blRelease)
                        RoadBizUtil.commit()
                        DomainQuery domainQuery = QueryUtils.createDomainQuery("BlRelease")
                                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, bl.getPrimaryKey()))
                                .addDqPredicate(PredicateFactory.isNotNull(MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherQty")))
                                .addDqAggregateField(AggregateFunctionType.SUM, MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherQty"))
                        Double totalOtherQty = QueryUtil.getQuerySum(domainQuery)
                        bl.setBlFlexDouble01(totalOtherQty)
                        HibernateApi.getInstance().save(bl)
                        ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
                        if (totalOtherQty > 0) {
                            Serializable holdGKey = servicesManager.applyHold("OTHER_PORT_RELEASE", bl, null, null, "Other port hold posted")
                        } else {
                            Serializable vetoGKey = servicesManager.applyPermission("OTHER_PORT_RELEASE", bl, null, null, "Other port hold release")
                        }
                        RoadBizUtil.commit()
                    }
                }
            }
        }
    }

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
            if (dataValue == null) {
                continue
            }
            String[] arrayOfStrings = dataValue.split(',')
            for (int i = 0; i < arrayOfStrings.length; i++) {
                arrayOfStrings[i] = arrayOfStrings[i].trim()
            }
            portCodesList.addAll(new ArrayList(asList(arrayOfStrings)))
        }
        return portCodesList
    }

    private void registerWarning(PropertyKey inPropertykey, String inMsg){
        MessageCollector messageCollector = ContextHelper.getThreadMessageCollector()
        if (messageCollector!= null) {
            messageCollector.appendMessage(MessageLevel.WARNING, inPropertykey, inMsg , null)
        }
    }


    private void inboundStatusUpdating(String dispCode, BillOfLading Bl) {
        if (dispCode == null || Bl == null) {
            return
        }
        def offSite = ["1W","1A", "2H", "1H", "71", "72", "73", "4A", "A3"]
        switch (dispCode){
            case "95" :
                Bl.setBlExam(null)
                Bl.setBlInbond(null)
                break
            case "1W":
                Bl.setBlExam(ExamEnum.OFFSITE)
                Bl.setBlInbond(null)
                break
            case "83":
                Bl.setBlExam(null)
                Bl.setBlInbond(null)
                break
            case "84":
                Bl.setBlExam(null)
                Bl.setBlInbond(null)
                break
            default :
                if (offSite.contains(dispCode)){
                    Bl.setBlExam(ExamEnum.OFFSITE)
                    Bl.setBlInbond(null)
                }
                break
        }
        Bl.updateUnitExamStatus()
        Bl.updateUnitInbondStatus()
    }
    /**
     * This method will be executed if disposition code 95 or 1J(out of order message) is received.
     * Cancel active 1J using 95 disposition code
     */
    private void handling1JUsing95(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, BillOfLading billOfLading) throws BizViolation {
        String referenceId = releaseTransaction.getReleaseReferenceId()
        String ediCode = releaseTransaction.getEdiCode()
        List blReleases = findBlRelease(billOfLading.getBlGkey(), "95")
        if (blReleases.isEmpty()) {
            return
        }

        BlRelease blRelease = null
        for (BlRelease release : blReleases) {
            if (isBlReleaseCanceled(billOfLading.getBlGkey(), release.getBlrelGkey())) {
                continue
            }
            blRelease = release
            break
        }

        if (blRelease == null) {
            return;
        }
        boolean matchByRef = isQtyMatchByReference(releaseTransaction)

        if ("1J".equalsIgnoreCase(ediCode)) {
            referenceId = blRelease.getBlrelReferenceNbr()
        }
        if (referenceId == null && matchByRef) {
            LOGGER.error("Active 1J cannot be cancelled because reference_Id is null and MatchQtyByReference is selected in release map LOV.")
            return
        }
        List<BlRelease> listOfReleases = findActive1J(releaseTransaction, billOfLading.getBlGkey(), referenceId)
        for (BlRelease release1J : listOfReleases) {
            release1J.setFieldValue(InventoryCargoField.BLREL_REFERENCE, blRelease)
            blRelease.setFieldValue(InventoryCargoField.BLREL_REFERENCE,release1J)
            HibernateApi.getInstance().save(release1J)
            HibernateApi.getInstance().save(blRelease)
        }
        if (!listOfReleases.isEmpty()) {
            rcdSrvEvent(billOfLading, CANCELED_EVENT_1J_STR)
            inboundStatusUpdating(billOfLading)
        }
    }
    private void UpdateInbondStatus(BillOfLading inBl) {
        DomainQuery domainQuery = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBl.getPrimaryKey()))
                .addDqPredicate(PredicateFactory.in(InventoryCargoField.BLREL_DISPOSITION_CODE, ["1J", "95"]))
                .addDqAggregateField(AggregateFunctionType.SUM, InventoryCargoField.BLREL_QUANTITY)
        Double totalInbondQty = QueryUtil.getQuerySum(domainQuery)
        if (inBl.getBlManifestedQty() > 0) {
            if (totalInbondQty != null && totalInbondQty > 0 && totalInbondQty >= inBl.getBlManifestedQty()) {
                inBl.setBlInbond(InbondEnum.INBOND)
            } else {
                inBl.setBlInbond(null)
            }
            inBl.updateUnitInbondStatus()
        }
        inBl.setBlFlexDouble02(totalInbondQty)
        HibernateApi.getInstance().save(inBl)
    }

    private void UpdateExamStatus(BillOfLading inBl, Boolean isSample, Boolean isSampleCancel) {
        if (isSample) {
            DomainQuery domainQuery = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                    .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBl.getPrimaryKey()))
                    .addDqPredicate(PredicateFactory.in(InventoryCargoField.BLREL_DISPOSITION_CODE, ["1X"]))
                    .addDqAggregateField(AggregateFunctionType.SUM, InventoryCargoField.BLREL_QUANTITY)
            Double totalExamQty = QueryUtil.getQuerySum(domainQuery)
            if (isSampleCancel) {
                inBl.setBlExam(null)
                inBl.setBlFlexDouble04(0.0)
                inBl.setBlFlexString01("")
            } else {
                inBl.setBlExam(ExamEnum.OFFSITE)
                inBl.setBlFlexDouble04(totalExamQty)
                inBl.setBlFlexString01("S")
            }
        } else {
            DomainQuery domainQuery = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                    .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBl.getPrimaryKey()))
                    .addDqPredicate(PredicateFactory.in(InventoryCargoField.BLREL_DISPOSITION_CODE, ["1W", "83"]))
                    .addDqAggregateField(AggregateFunctionType.SUM, InventoryCargoField.BLREL_QUANTITY)
            Double totalExamQty = QueryUtil.getQuerySum(domainQuery)
            if (inBl.getBlManifestedQty() > 0) {
                if (totalExamQty != null && totalExamQty > 0 && totalExamQty >= inBl.getBlManifestedQty()) {
                    inBl.setBlExam(ExamEnum.OFFSITE)
                } else {
                    inBl.setBlExam(null)
                }
                inBl.updateUnitExamStatus()
            }
            inBl.setBlFlexDouble03(totalExamQty)
        }
        HibernateApi.getInstance().save(inBl)
    }



    private void handlingOf1WUsing83(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, BillOfLading billOfLading) throws BizViolation {
        String refId = releaseTransaction.getReleaseReferenceId()
        String ediCode = releaseTransaction.getEdiCode()
        Double ediReleaseQty = (releaseTransaction.getReleaseQty() != null) ? Double.valueOf(releaseTransaction.getReleaseQty()) : 0.0
        List blReleases = findBlRelease(billOfLading.getBlGkey(), "83")
        if (blReleases.isEmpty()) {
            return
        }
        BlRelease blRelease = null
        for (BlRelease release : blReleases) {
            if (isBlReleaseCanceled(billOfLading.getBlGkey(), release.getBlrelGkey())) {
                continue
            }
            blRelease = release
            break
        }
        if (blRelease == null) {
            return
        }
        boolean matchByRef = isQtyMatchByReference(releaseTransaction)
        if ("1W".equalsIgnoreCase(ediCode)) {
            refId = blRelease.getBlrelReferenceNbr()
        }
        if (refId == null && matchByRef) {
            LOGGER.error("Cannot cancel Active 1W because reference_Id is null and MatchQtyByReference is enabled in the release map LOV..")
            return
        }
        List<BlRelease> listOfReleases = findActive1W(releaseTransaction, billOfLading.getBlGkey(), refId)
        for (BlRelease release1W : listOfReleases) {
            if (!listOfReleases.isEmpty()) {
                rcdSrvEvent(billOfLading, CANCELED_EVENT_1W_STR)
                inboundStatusUpdating(billOfLading)
                if ("83".equalsIgnoreCase(ediCode)) {
                    ediReleaseQty = ediReleaseQty - (ediReleaseQty * 2)
                    blRelease.setFieldValue(InventoryCargoField.BLREL_QUANTITY, ediReleaseQty)
                    HibernateApi.getInstance().save(blRelease)
                }
            }
            release1W.setFieldValue(InventoryCargoField.BLREL_REFERENCE, blRelease)
            HibernateApi.getInstance().save(release1W)
        }
    }
    private void handling4E(BillOfLading billOfLading, String refNbr) {
        //the 4E disposition code is written to the database.
        HibernateApi.getInstance().flush()

        /** There are no active 1A, 1B, or 1C disposition codes for the given 4E reference nbr. If a 1A, 1B, or 1C is already cancelled by a 4E,
         then the BL Release Reference Entity for that 1A, 1B, or 1C BLRelease will be set to the gkey of the 4E that cancelled it,
         and the BL Release Reference Entity for the 4E will be the gkey of the BLRelease that it cancels.*/

        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, billOfLading.getPrimaryKey()))
                .addDqPredicate(PredicateFactory.in(InventoryCargoField.BLREL_DISPOSITION_CODE, [DISP_CODE_1A, DISP_CODE_1B, DISP_CODE_1C]))
                .addDqPredicate(PredicateFactory.isNull(InventoryCargoField.BLREL_REFERENCE))
        /** Customs holds 1A, 1B, and 1C. The reason is that when the 4E was posted, it was cancelling the 1C(information) instead of the 1C(release),
         which ended up not decreasing the release qty.*/

                .addDqPredicate(PredicateFactory.eq(BLRELEASE_FLAG_TYPE_ID, DISP_CODE_1C_HOLD_ID))
                .addDqOrdering(Ordering.desc(InventoryCargoField.BLREL_POST_DATE))
                .addDqOrdering(Ordering.desc(InventoryCargoField.BLREL_GKEY))
        if (refNbr != null) {
            dq.addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_REFERENCE_NBR, refNbr))
        }
        List<BlRelease> blRelList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        DomainQuery releaseDq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, billOfLading.getPrimaryKey()))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, DISP_CODE_4E))
                .addDqPredicate(PredicateFactory.isNull(InventoryCargoField.BLREL_REFERENCE))
                .addDqOrdering(Ordering.asc(InventoryCargoField.BLREL_POST_DATE));

        if (refNbr != null) {
            releaseDq.addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_REFERENCE_NBR, refNbr))
        }
        List<BlRelease> blReleasesList4E = HibernateApi.getInstance().findEntitiesByDomainQuery(releaseDq)
        LOGGER.info("BL 4ERelease size : " + blReleasesList4E.size())
        if (blRelList != null && blRelList.size() > 0) {
            BlRelease blRelease = blRelList?.get(0)
            if (DISP_CODE_1C.equalsIgnoreCase(blRelease.getBlrelDispositionCode())) {
                LOGGER.info("DISP 1C is found as the first record in the list, so only 4E cancels 1C disp code alone and the other 4E entries will be nullified. ")
                BlRelease release4EFor1CDisp = get4EReleaseForHoldId(blReleasesList4E, DISP_CODE_1C_HOLD_ID)
                release4EFor1CDisp.setFieldValue(InventoryCargoField.BLREL_QUANTITY_TYPE, blRelease.getBlrelQuantityType())
                release4EFor1CDisp.setFieldValue(InventoryCargoField.BLREL_REFERENCE, blRelease)
                blRelease.setFieldValue(InventoryCargoField.BLREL_REFERENCE, release4EFor1CDisp)

                if (blRelList.size() > 1) {
                    BlRelease release1ADisp = blRelList.get(1)
                    if (DISP_CODE_1A.equalsIgnoreCase(release1ADisp.getBlrelDispositionCode())) {
                        serviceManager.applyHold(release1ADisp.getBlrelFlagType().getFlgtypId().trim(), billOfLading, null, refNbr, release1ADisp.getBlrelFlagType().getFlgtypId().trim())
                    }
                }
                setBlReleaseQtyToNullToAllBlReleases(blReleasesList4E, release4EFor1CDisp)
            } else if (DISP_CODE_1A.equalsIgnoreCase(blRelease.getBlrelDispositionCode())) {
                LOGGER.info("Disp 1A is found as the first record in the list, so 4E cancels 1A disp code alone and the other 4E entries will be nullified. ")
                BlRelease release4EFor1ADisp = get4EReleaseForHoldId(blReleasesList4E, blRelease.getBlrelFlagType().getFlgtypId())
                release4EFor1ADisp.setFieldValue(InventoryCargoField.BLREL_QUANTITY_TYPE, blRelease.getBlrelQuantityType())
                release4EFor1ADisp.setFieldValue(InventoryCargoField.BLREL_REFERENCE, blRelease)
                blRelease.setFieldValue(InventoryCargoField.BLREL_REFERENCE, release4EFor1ADisp)
                setBlReleaseQtyToNullToAllBlReleases(blReleasesList4E, release4EFor1ADisp)
            } else if (DISP_CODE_1B.equalsIgnoreCase(blRelease.getBlrelDispositionCode())) {
                boolean isDisp1AFollowedBy1B = false
                for (BlRelease blRel : blRelList) {
                    if (DISP_CODE_1A.equalsIgnoreCase(blRel.getBlrelDispositionCode())) {
                        LOGGER.info("1A and 1B are found as first and second records in list so 4E cancels both 1A and 1B disp codes")
                        BlRelease release4EFor1ADisp = get4EReleaseForHoldId(blReleasesList4E, blRel.getBlrelFlagType().getFlgtypId())
                        release4EFor1ADisp.setFieldValue(InventoryCargoField.BLREL_QUANTITY_TYPE, blRel.getBlrelQuantityType())
                        release4EFor1ADisp.setFieldValue(InventoryCargoField.BLREL_REFERENCE, blRel)
                        blRel.setFieldValue(InventoryCargoField.BLREL_REFERENCE, release4EFor1ADisp)
                        if (blReleasesList4E.size() == 1) {
                            BlRelease release4EFor1B = new BlRelease()
                            release4EFor1B.setFieldValue(InventoryCargoField.BLREL_BL, billOfLading)
                            release4EFor1B.setFieldValue(InventoryCargoField.BLREL_QUANTITY, release4EFor1ADisp.getBlrelQuantity())
                            release4EFor1B.setFieldValue(InventoryCargoField.BLREL_FLAG_TYPE, release4EFor1ADisp.getBlrelFlagType())
                            release4EFor1B.setFieldValue(InventoryCargoField.BLREL_QUANTITY_TYPE, EdiReleaseMapModifyQuantityEnum.ReleasedQuantity)
                            release4EFor1B.setFieldValue(InventoryCargoField.BLREL_REFERENCE_NBR, release4EFor1ADisp.getBlrelReferenceNbr())
                            release4EFor1B.setFieldValue(InventoryCargoField.BLREL_DISPOSITION_CODE, release4EFor1ADisp.getBlrelDispositionCode())
                            release4EFor1B.setFieldValue(InventoryCargoField.BLREL_NOTES, release4EFor1ADisp.getBlrelNotes())
                            release4EFor1B.setFieldValue(InventoryCargoField.BLREL_POST_DATE, release4EFor1ADisp.getBlrelPostDate())
                            release4EFor1B.setFieldValue(InventoryCargoField.BLREL_REFERENCE, blRelease)
                            HibernateApi.getInstance().save(release4EFor1B)
                        } else {
                            BlRelease release4EFor1BDisp = get4EReleaseForHoldId(blReleasesList4E, DISP_CODE_1C_HOLD_ID)
                            release4EFor1BDisp.setFieldValue(InventoryCargoField.BLREL_QUANTITY_TYPE, blRelease.getBlrelQuantityType())
                            release4EFor1BDisp.setFieldValue(InventoryCargoField.BLREL_REFERENCE, blRelease)
                            blRelease.setFieldValue(InventoryCargoField.BLREL_REFERENCE, release4EFor1BDisp)
                        }
                        isDisp1AFollowedBy1B = true
                    }
                }
                if (!isDisp1AFollowedBy1B) {
                    LOGGER.info("1B alone exists without 1A, which is practically not correct, so nullifying all 4E entries. ")
                    setBlReleaseQtyToNullToAllBlReleases(blReleasesList4E, null)
                }
            } else {
                LOGGER.info("Either 1A, 1B, or 1C is expected, but BL_release with DISP code" + blRelease.getBlrelDispositionCode() +
                        "is presented wrongly, so nullifying all 4E entries.");
                setBlReleaseQtyToNullToAllBlReleases(blReleasesList4E, null)
            }
        } else {
            LOGGER.info("Neither 1A, 1B, or 1C were found, so nullifying all 4E entries.")
            setBlReleaseQtyToNullToAllBlReleases(blReleasesList4E, null)
        }

        HibernateApi.getInstance().flush()
    }
    private void setBlReleaseQtyToNullToAllBlReleases(List<BlRelease> blReleases, BlRelease blRelease) {
        for (BlRelease release4E : blReleases) {
            if (blRelease != null && blRelease.equals(release4E)) {
                continue
            }
            release4E.setFieldValue(InventoryCargoField.BLREL_QUANTITY_TYPE, null)
            release4E.setFieldValue(InventoryCargoField.BLREL_REFERENCE, release4E)
        }
    }
    private void handling55(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, BillOfLading billOfLading) {
        Double quantity = getQty(releaseTransaction)
        billOfLading.setFieldValue(InventoryCargoField.BL_MANIFESTED_QTY, quantity)
        rcdSrvEvent(billOfLading, RECEIVED_55_STR)
        HibernateApi.getInstance().save(billOfLading)
    }
    private BlRelease get4EReleaseForHoldId(List<BlRelease> inblReleases, String inHoldId) {
        if (inblReleases != null) {
            for (BlRelease blrel : inblReleases) {
                String holdId = blrel.getBlrelFlagType().getFlgtypId().trim()
                if (inHoldId.trim().equalsIgnoreCase(holdId)) {
                    return blrel
                }
            }
        }
        return null
    }
    /**
     Update the Inbound status to CANCEL/INBOUND. If manifest qty is less than or equal to inbound qty, then inbound status should be INBOUND,
     otherwise the status should be CANCEL.
     */
    private void inboundStatusUpdating(BillOfLading inBl) {
        Serializable blGkey = inBl.getBlGkey()
        HibernateApi.getInstance().flush()

        List<BlRelease> blReleases = findActiveInbound(blGkey)
        Double inbondQtySum = 0
        List referenceIdList = new ArrayList()
        for (BlRelease blRelease : blReleases) {
            String refId = blRelease.getBlrelReferenceNbr()
            if ("1J".equalsIgnoreCase(blRelease.getBlrelDispositionCode()) || "1W".equalsIgnoreCase(blRelease.getBlrelDispositionCode())) {
                referenceIdList.add(refId)
            }
            Double qty = blRelease.getBlrelQuantity()
            if (qty != null) {
                inbondQtySum = inbondQtySum + qty
                LOGGER.info("inboundQtySum=" + inbondQtySum)
            }
        }
        Double blManifestQty = inBl.getBlManifestedQty()
        if (blManifestQty != null) {
            if (blManifestQty <= inbondQtySum) {
                inBl.updateInbond(InbondEnum.INBOND)
            } else {
                inBl.updateInbond(InbondEnum.CANCEL)
            }
            LOGGER.info("inboundQtySum=" + inbondQtySum + " blManifestQty=" + blManifestQty)
        }
        HibernateApi.getInstance().save(inBl)
    }

    private void applyFlexToRemarks(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, BillOfLading billOfLading, String ediCode) {
        EdiReleaseFlexFields flexField = releaseTransaction.getEdiReleaseFlexFields()
        if (flexField == null) {
            LOGGER.info("applyFlexToRemarks() : value of  inRelease.getEdiReleaseFlexFields() is empty")
            return
        }
        String flexField02 = flexField.getEdiReleaseFlexString02()
        if (flexField02 == null) {
            LOGGER.info("applyFlexToRemarks() : value of  flexField02 is null")
            return
        }
        if (flexField02.isEmpty()) {
            LOGGER.info("applyFlexToRemarks() : value of  flexField02 is empty")
            return
        }
        if (flexField02.length() > 4000)
        {
            flexField02 = flexField02.substring(0, 3999)
        }

        BlRelease blRelease = findLatestBlReleaseForDisp(billOfLading.getBlGkey(), ediCode)
        if (blRelease != null) {
            if (blRelease.getCustomFlexFields() == null) {
                blRelease.setCustomFlexFields(new HashMap<String, Object>())
            }
            blRelease.setFieldValue(MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFF_REMARKS"), flexField02)
            HibernateApi.getInstance().save(blRelease)
            LOGGER.info("applyFlexToRemarks :" + flexField.getEdiReleaseFlexString02() + "is set for ediCode:  " + releaseTransaction.getEdiCode() +
                    " flexField02 " + flexField02)
        }
    }

    private List<BlRelease> findActiveInbound(Serializable inBlGkey) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBlGkey))
                .addDqPredicate(PredicateFactory.in(InventoryCargoField.BLREL_DISPOSITION_CODE, ["1J", "1C", "1W"]))
                .addDqOrdering(Ordering.asc(InventoryCargoField.BLREL_CREATED))
                .addDqPredicate(PredicateFactory.isNull(InventoryCargoField.BLREL_REFERENCE))
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
    }

    private ScopedBizUnit findLineOperator(ReleaseTransactionDocument.ReleaseTransaction inRelease) {
        ShippingLine ediLine = inRelease.getEdiShippingLine()
        if (ediLine != null) {
            String lineCode = ediLine.getShippingLineCode()
            String lineCodeAgency = ediLine.getShippingLineCodeAgency()
            return ScopedBizUnit.resolveScopedBizUnit(lineCode, lineCodeAgency, BizRoleEnum.LINEOP)
        }
        return null
    }

    private boolean isBlReleaseCanceled(Serializable blGkey, Serializable blReleaseGkey) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, blGkey))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_REFERENCE, blReleaseGkey))
        return HibernateApi.getInstance().existsByDomainQuery(dq)
    }

    private Double getQty(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction) {
        Double qty = safeDouble(releaseTransaction.getQty())
        if (qty == null) {
            String releaseQtyString = releaseTransaction.getReleaseQty()
            qty = safeDouble(releaseQtyString)
        }
        if (qty == null) {
            qty = 0.0
        }
        return qty
    }

    private Double safeDouble(String inNumberString) {
        Double doubleObject = null
        if (!StringUtils.isEmpty(inNumberString)) {
            try {
                doubleObject = new Double(inNumberString)
            } catch (NumberFormatException e) {
                throw e
            }
        }
        return doubleObject;
    }

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

    private List<BlRelease> findBlRelease(Serializable inBlGkey, String inDispositionCode) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBlGkey))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, inDispositionCode))
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
    }

    private List<BlRelease> findBlRelease(Serializable blGkey, String dispositionCode, Date postedDate) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, blGkey))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, dispositionCode))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_POST_DATE, postedDate))
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
    }

    private List<BlRelease> findActive1J(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, Serializable blGkey,
                                         String inReferenceId) throws BizViolation {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, blGkey))
                .addDqPredicate(PredicateFactory.isNull(InventoryCargoField.BLREL_REFERENCE))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, "1J"))
        boolean isQtyMatchByReferenceNbr = isQtyMatchByReference(releaseTransaction)
        if (isQtyMatchByReferenceNbr) {
            dq.addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_REFERENCE_NBR, inReferenceId))
        }
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
    }

    private List<BlRelease> findActive1W(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, Serializable blGkey,
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

    private boolean hasActive1J(Serializable blGkey) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, blGkey))
                .addDqPredicate(PredicateFactory.isNull(InventoryCargoField.BLREL_REFERENCE))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, "1J"))
        return HibernateApi.getInstance().existsByDomainQuery(dq)
    }

    private boolean hasActive1W(Serializable blGkey) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, blGkey))
                .addDqPredicate(PredicateFactory.isNull(InventoryCargoField.BLREL_REFERENCE))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, "1W"))
        return HibernateApi.getInstance().existsByDomainQuery(dq)
    }

    /**
     * find all release map for given disposition code and message type. Extract the release by BL hold/permission.
     */
    private IReleaseMap findReleaseMapsFor95(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction) throws BizViolation {
        ArgoEdiFacade ediFacade = (ArgoEdiFacade) Roastery.getBean(ArgoEdiFacade.BEAN_ID)
        String msgId = releaseTransaction.getMsgTypeId()
        String msgVersion = releaseTransaction.getMsgVersion()
        String msgReleaseNumber = releaseTransaction.getMsgReleaseNbr()
        Set ediCodeSet = new HashSet()
        ediCodeSet.add("95")

        List<IReleaseMap> releaseMaps =
                ediFacade.findEdiReleaseMapsForEdiCodes(msgId, ediCodeSet, msgVersion, msgReleaseNumber, LogicalEntityEnum.BL);
        String msg = "Map Code: " + releaseTransaction.getEdiCode() + " Message Id: " + msgId + ", Message Version: " + msgVersion +
                ", Release Number: " + msgReleaseNumber
        if (releaseMaps.isEmpty()) {
            throw BizViolation.create(PropertyKeyFactory.valueOf("Unable to find the release map for the condition: " + msg), null)
        }
        if (releaseMaps.size() > 1) {
            throw BizViolation.create(PropertyKeyFactory.valueOf("Found multiple release map for the condition: " + msg), null)
        }
        return releaseMaps.get(0)
    }
    /**
     return true if match qty is "Match Qty By Reference" ion release map configuration
     */
    private boolean isQtyMatchByReference(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction) throws BizViolation {
        IReleaseMap releaseMap = findReleaseMapsFor95(releaseTransaction)
        return releaseMap == null ? false : EdiReleaseMapQuantityMatchEnum.MatchQtyByReference.equals(releaseMap.getEdirelmapMatchQty())
    }

    private void settingUniqueReferenceId(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, String ediCode) {
        if (StringUtils.isNotEmpty(releaseTransaction.getReleaseReferenceId())) {
            EdiReleaseFlexFields ediReleaseFlexFields = releaseTransaction.getEdiReleaseFlexFields()
            if (ediReleaseFlexFields == null) {
                ediReleaseFlexFields = releaseTransaction.addNewEdiReleaseFlexFields()
            }
            ediReleaseFlexFields.setEdiReleaseFlexString01(releaseTransaction.getReleaseReferenceId())
        }
        releaseTransaction.setReleaseReferenceId(UUID.randomUUID().toString())
    }

    private void setBackTransactionReferenceId(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, BillOfLading billOfLading,
                                               String ediCode) {
        EdiReleaseFlexFields flexFields = releaseTransaction.getEdiReleaseFlexFields()
        if (flexFields != null && flexFields.getEdiReleaseFlexString01() != null) {
            BlRelease blrel = findLatestBlReleaseForDisp(billOfLading.getBlGkey(), ediCode)
            if (blrel != null) {
                blrel.setFieldValue(InventoryCargoField.BLREL_REFERENCE_NBR, flexFields.getEdiReleaseFlexString01())
                LOGGER.info("setBackTransactionReferenceId() :" + flexFields.getEdiReleaseFlexString01() + "is set back to ediCode: " + ediCode)
            } else {
                LOGGER.info("setBackTransactionReferenceId() : blRelease is null !")
            }
        } else {
            LOGGER.info(
                    "setBackTransactionReferenceId() : value of flexFields.getEdiReleaseFlexString01() is empaty so systed did not revert back the unique reference id");
        }
    }

    private BlRelease findLatestBlReleaseForDisp(Serializable blGkey, String dispositionCode) {

        LOGGER.info("findLatestBlReleaseForDISPCodeAndBL inBlGkey" + blGkey + "inDispositionCode :" + dispositionCode)
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
        dq.addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, blGkey))
        dq.addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, dispositionCode))
        dq.addDqOrdering(Ordering.desc(InventoryCargoField.BLREL_CREATED))
        dq.addDqOrdering(Ordering.desc(InventoryCargoField.BLREL_GKEY))
        List<BlRelease> blreleaseList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        return !blreleaseList.isEmpty() ? blreleaseList.get(0) : null as BlRelease
    }

    private void handle5H5I4ABeforePost(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, String ediCode) {
        LOGGER.info(" handle5H5I4ABeforePost(): starts for edi code " + ediCode)
        BillOfLading bl = null
        if (releaseTransaction.getEdiReleaseIdentifierList() != null && releaseTransaction.getEdiReleaseIdentifierList().size() > 0) {
            EdiReleaseIdentifier releaseIdentifier = releaseTransaction.getEdiReleaseIdentifierArray(0)
            String blNbr = releaseIdentifier.getReleaseIdentifierNbr()
            if (blNbr != null) {
                LineOperator lineOp = (LineOperator) findLineOperator(releaseTransaction)
                bl = BillOfLading.findBillOfLading(blNbr, lineOp, null)
            }
        }
        if ((bl == null) || (StringUtils.isNotBlank(releaseTransaction.getReleaseReferenceId()) &&
                !find1C1BForBLAndRefNbr(bl.getBlGkey(), releaseTransaction.getReleaseReferenceId()))) {
            Set<String> ediCodeSet = new HashSet<String>()
            ediCodeSet.add(ediCode)
            ArgoEdiFacade ediFacade = (ArgoEdiFacade) Roastery.getBean(ArgoEdiFacade.BEAN_ID)
            List<IReleaseMap> releaseMaps = ediFacade
                    .findEdiReleaseMapsForEdiCodes(releaseTransaction.getMsgTypeId(), ediCodeSet, releaseTransaction.getMsgVersion(),
                            releaseTransaction.getMsgReleaseNbr(), LogicalEntityEnum.BL)

            for (IReleaseMap releaseMap : releaseMaps) {
                EdiReleaseMap map = (EdiReleaseMap) releaseMap
                map.setFieldValue(EdiField.EDIRELMAP_MODIFY_QTY, null)
            }
        }
        LOGGER.info("handle5H5I4ALogicBeforePost(): ends ")
    }

    private void handling5H5I4ALogicAfterPost(ReleaseTransactionDocument.ReleaseTransaction releaseTransaction, String ediCode) {
        LOGGER.info(" handling5H5I4ALogicAfterPost()::  " + ediCode)
        ArgoEdiFacade ediFacade = (ArgoEdiFacade) Roastery.getBean(ArgoEdiFacade.BEAN_ID)
        Set<String> ediCodeSet = new HashSet<String>()
        ediCodeSet.add(ediCode)
        List<IReleaseMap> releaseMaps =
                ediFacade.findEdiReleaseMapsForEdiCodes(releaseTransaction.getMsgTypeId(), ediCodeSet,
                        releaseTransaction.getMsgVersion(), releaseTransaction.getMsgReleaseNbr(), LogicalEntityEnum.BL)
        for (IReleaseMap releaseMap : (releaseMaps as List<IReleaseMap>)) {
            EdiReleaseMap map = (EdiReleaseMap) releaseMap
            map.setFieldValue(EdiField.EDIRELMAP_MODIFY_QTY, EdiReleaseMapModifyQuantityEnum.ReleasedQuantity)
        }
        LOGGER.info(" handling5H5I4ALogicAfterPost(): ends")
    }
    private boolean find1C1BForBLAndRefNbr(Serializable inBlGkey, String inReferenceId) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBlGkey))
                .addDqPredicate(PredicateFactory.in(InventoryCargoField.BLREL_DISPOSITION_CODE, ["1C", "1B"]))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_REFERENCE_NBR, inReferenceId))
        return HibernateApi.getInstance().existsByDomainQuery(dq)
    }
    /**
     ediCode must exist in General References for the 350_QUALIFIER Type
     portId must not exist in General References for the 350_PORT_CODE Type
     */
    private void releaseGeneralReferenceHolds(String inEdiCode, EdiFacility ediFacility, BillOfLading billOfLading, String inReleaseRefId) {
        try {
            GeneralReference groupQualifier = GeneralReference.findUniqueEntryById("350_QUALIFIER", inEdiCode)
            GeneralReference groupPort = GeneralReference.findUniqueEntryById("350_PORT_CODE", ediFacility.getFacilityPort().getPortCodes().getId())
            if (groupQualifier != null && groupPort == null) {
                LOGGER.info("attempt to release " + inEdiCode + " hold from bl " + billOfLading.getBlNbr())
                ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
                servicesManager.applyPermission(inEdiCode, billOfLading, null, inReleaseRefId, null)
            }
        } catch (Exception e) {
            LOGGER.info("Error applying permission " + e.getMessage())
        }
    }
    /** Update the vessel visit of the BL if it is linked to GEN_VESSEL and Release has a valid vessel visit.
     */
    private void updateBlCarrierVisit(BillOfLading billOfLading,Map inParams) {
        if (billOfLading.getBlCarrierVisit().isGenericCv()) {
            try {
                Serializable batchGkey = (Serializable) inParams.get(EdiConsts.BATCH_GKEY);
                LOGGER.warn("BL has generic Carrier visit ")
                if (batchGkey != null) {
                    EdiBatch ediBatch = EdiBatch.hydrate(batchGkey)
                    if (ediBatch != null && ediBatch.getEdibatchCarrierVisit() != null &&
                            !ediBatch.getEdibatchCarrierVisit().isGenericCv()) {
                        LOGGER.warn("Updating BL Carrier visit ")
                        billOfLading.setBlCarrierVisit(ediBatch.getEdibatchCarrierVisit())
                        HibernateApi.getInstance().save(billOfLading)
                    }
                }
            } catch (Exception ignored) {
                LOGGER.warn("Error while trying to update BL Carrier visit")
            }
        }
    }

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
                String releasePostdate = XmlUtil.extractAttributeValueFromXml("releasePostDate", result.getValue(i, EdiField.EDITRAN_DOC).toString())
                if (releasePostdate != null && postdate.equals(releasePostdate)) {
                    return true
                }
            }
        }
        return false
    }
    private final String DISP_CODE_1C_HOLD_ID = "350_INFORMATION_DISPOSITION"
    private final String DISP_CODE_1A = "1A"
    private final String DISP_CODE_1B = "1B"
    private final String DISP_CODE_1C = "1C"
    private final String DISP_CODE_4E = "4E"
    private final String ADD_EVENT_1J_STR = "RECEIVED_1J"
    private final String ADD_EVENT_1W_STR = "RECEIVED_1W"
    private final String SKIP_POSTER = "SKIP_POSTER"
    private final String CANCELED_EVENT_1J_STR = "CANCELED_1J"
    private final String CANCELED_EVENT_1W_STR = "CANCELED_1W"
    private final String RECEIVED_54_STR = "RECEIVED_54"
    private final String RECEIVED_55_STR = "RECEIVED_55"
    private final String BATCH_GKEY = "BATCH_GKEY"
    private final String OTHER_PORT_RELEASE = "OTHER_PORT_RELEASE"
    private static MetafieldId BATCH_INTERCHANGE_NBR = MetafieldIdFactory.valueOf("editranBatch.edibatchInterchange.ediintInterchangeNbr")
    private static MetafieldId EDITRAN_MSG_REF_NBR = MetafieldIdFactory.valueOf("editranBatch.edibatchMsgRefNbr")
    private List<String> DISPOSITION_CODES_FOR_UNIQUE_ID = new ArrayList<String>()
            {
                EventType.findOrCreateEventType(ADD_EVENT_1J_STR, "Received 1J", LogicalEntityEnum.BL, null)
                EventType.findOrCreateEventType(CANCELED_EVENT_1J_STR, "1J Canceled Event", LogicalEntityEnum.BL, null)
                EventType.findOrCreateEventType(RECEIVED_54_STR, "Received 54", LogicalEntityEnum.BL, null)
                EventType.findOrCreateEventType(RECEIVED_55_STR, "Received 55", LogicalEntityEnum.BL, null)
                DISPOSITION_CODES_FOR_UNIQUE_ID.add("7H")
            }
    private ServicesManager serviceManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
    private static final BLRELEASE_FLAG_TYPE_ID = MetafieldIdFactory.getCompoundMetafieldId(InventoryCargoField.BLREL_FLAG_TYPE, ServicesField.FLGTYP_ID)
    private static final Logger LOGGER = Logger.getLogger(ITSUSCustomsBLReleaseGvy.class)
}
