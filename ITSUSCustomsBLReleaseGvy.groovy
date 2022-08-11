import com.navis.argo.*
import com.navis.argo.business.api.*
import com.navis.argo.business.atoms.*
import com.navis.argo.business.model.EdiPostingContext
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
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageCollector
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
* US Customs Code moved from Husky
*
 */

public class ITSUSCustomsBLReleaseGvy extends AbstractEdiPostInterceptor {

    /**
     * Method will be called from EDI engine
     */
    public void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        LOGGER.setLevel(Level.INFO);
        if (inXmlTransactionDocument == null || !ReleaseTransactionsDocument.class.isAssignableFrom(inXmlTransactionDocument.getClass())) {
            return;
        }
        ReleaseTransactionsDocument relDocument = (ReleaseTransactionsDocument) inXmlTransactionDocument;
        ReleaseTransactionsDocument.ReleaseTransactions releaseTrans = relDocument.getReleaseTransactions();
        ReleaseTransactionDocument.ReleaseTransaction[] releaseArray = releaseTrans.getReleaseTransactionArray();

        if (releaseArray == null || releaseArray.length == 0) {
            LOGGER.error("Release Array is NULL in before EDI post method");
            return;
        }

        ReleaseTransactionDocument.ReleaseTransaction releaseTransaction = releaseArray[0];
        validateRoutingPoint(releaseTransaction, inParams);

        //Skip posting if same edi code, interChangeNumber,msgreference nbr and release postdate exist already
        Object postdate = releaseTransaction.getReleasePostDate()
        String posdate = postdate?.toString()
        EdiReleaseIdentifier releaseIdentifier = releaseTransaction?.getEdiReleaseIdentifierList()?.get(0)
        String blNbr = releaseIdentifier?.getReleaseIdentifierNbr()
        Serializable batchGkey = (Serializable) inParams.get(BATCH_GKEY)
        Serializable tranGkey = (Serializable) inParams.get(EdiConsts.TRANSACTION_GKEY)
        EdiBatch batch = EdiBatch.hydrate(batchGkey)
        if (batch != null) {
            String msgRefNbr = batch.getEdibatchMsgRefNbr()
            EdiInterchange ediInterchange = batch.getEdibatchInterchange()
            String interchangeNbr = ediInterchange?.getEdiintInterchangeNbr()
            String code = releaseTransaction?.getEdiCode()
            if (interchangeNbr != null && code != null && msgRefNbr != null && blNbr != null) {
                LOGGER.info("code::" + code + "ediInterchange::" + ediInterchange + "msgRefNbr::" + msgRefNbr + "blNbr:" + blNbr + "tranGkey :"+tranGkey)
                if (posdate != null && hasInterchangeAndEdiCode(interchangeNbr, code, msgRefNbr, blNbr, posdate, tranGkey)) {
                    inParams.put(SKIP_POSTER, true)
                    BizViolation warning = BizWarning.create(ArgoPropertyKeys.INFO, null, "Skipped posting for ${blNbr} and edi Code ${code} as the Transaction is a Duplicate.");
                    getMessageCollector().appendMessage(warning);
                }
            }
        }
        /**
         Uncomment the below method if 5H is not just a Information message.
         Please note if you uncomment this method call you must uncomment handle5H5I4ALogicAfterPost() method call in afterEdiPost() too.
         * */
        String ediCode = releaseTransaction.getEdiCode();
        if ("5H".equalsIgnoreCase(ediCode) || "5I".equalsIgnoreCase(ediCode) || "4A".equalsIgnoreCase(ediCode)) {
            handle5H5I4ALogicBeforePost(releaseTransaction, ediCode);
        }

        /**
         Uncomment the below method if your information message example 1h,2h,7h... comes with no guaranteed unique reference nbr and your message is expected to apply multiple hold.
         Please note:
         1)	If you uncomment this method call then you must uncomment setBackTransactionReferenceId () method call in afterEdiPost() too.
         2)	This should be uncommented if customer is using 2.3-rel or its older version
         * */
        if (DISPOSITION_CODES_FOR_UNIQUE_ID.indexOf(ediCode) >= 0) {
            setUniqueReferenceId(releaseTransaction, ediCode);
        }
        // Skip 1J or 1C or 95 or 4E message if 1W already exist
        if ("1J".equalsIgnoreCase(ediCode) || "1C".equalsIgnoreCase(ediCode) || "95".equalsIgnoreCase(ediCode) || "4E".equalsIgnoreCase(ediCode)) {
            isReleaseForOtherCode(releaseTransaction, inParams, ediCode);
        }

    }

    /**
     * Check if release message is for other facility.
     * @param inReleaseTransaction
     * @param inParams
     */
    private void isReleaseForOtherCode(ReleaseTransactionDocument.ReleaseTransaction inReleaseTransaction, Map inParams, String inEdiCode) {
        GeneralReference generalReference = GeneralReference.findUniqueEntryById("EDI", "350", "PORT_CODES");
        //String gfValue1 =  generalReference.getRefValue1();
        //if (generalReference == null || gfValue1 == null || (gfValue1 != null && !gfValue1.contains(inEdiCode))) {
        if (generalReference == null) {
            throw BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                    "Please configure port Code:" + inEdiCode + " in General Reference, Type:EDI, Identifier1:350 and Identifier2:PORT_CODES");
        }

        List<String> portCodeList = getPortCodesFromGeneralReference(generalReference);
        if (inReleaseTransaction.getEdiReleaseIdentifierList() != null && !inReleaseTransaction.getEdiReleaseIdentifierList().isEmpty()) {
            EdiReleaseIdentifier releaseIdentifier = inReleaseTransaction.getEdiReleaseIdentifierList().get(0);
            String blNbr = releaseIdentifier.getReleaseIdentifierNbr();
            MessageCollector collector = ContextHelper.getThreadMessageCollector();
            BillOfLading bl = null;
            if (blNbr != null) {
                LineOperator lineOp = (LineOperator) findLineOperator(inReleaseTransaction);
                bl = BillOfLading.findBillOfLading(blNbr, lineOp, null);

            }

            //set the quantity to 0 if its for other ports
            if (bl != null && inReleaseTransaction.getVesselCallFacility() != null && inReleaseTransaction.getVesselCallFacility().getFacilityPort() != null &&
                    inReleaseTransaction.getVesselCallFacility().getFacilityPort().getPortCodes() != null) {
                String portCode = inReleaseTransaction.getVesselCallFacility().getFacilityPort().getPortCodes().getId();
                if (!portCodeList.contains(portCode)) {
                    inParams.put(SKIP_POSTER, true);
                    Double releaseQty = Double.valueOf(inReleaseTransaction.getReleaseQty());
                    BlRelease blRelease = new BlRelease();
                    blRelease.setBlrelReferenceNbr(inReleaseTransaction.getReleaseReferenceId());
                    blRelease.setBlrelDispositionCode(inReleaseTransaction.getEdiCode());
                    blRelease.setBlrelQuantityType(EdiReleaseMapModifyQuantityEnum.InformationOnly);
                    blRelease.setBlrelNotes(inReleaseTransaction.getReleaseNotes());
                    Object postDate = inReleaseTransaction.getReleasePostDate();
                    Date releasePostDate = null;
                    if (postDate != null && postDate instanceof Calendar) {
                        releasePostDate = ArgoEdiUtils.convertLocalToUtcDate(postDate, ContextHelper.getThreadEdiPostingContext().getTimeZone());
                    }
                    blRelease.setBlrelQuantity(0.0);
                    blRelease.setBlrelPostDate(releasePostDate);
                    if (releaseQty > 0 && (inEdiCode != null && (inEdiCode.equals("4E") || inEdiCode.equals("95")))) {
                        releaseQty = releaseQty *-1;
                    }
                    blRelease.setFieldValue(MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherQty"), releaseQty);
                    blRelease.setFieldValue(MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherPort"), portCode);
                    blRelease.setBlrelBl(bl);
                    HibernateApi.getInstance().save(blRelease);
                    RoadBizUtil.commit();
                    DomainQuery domainQuery = QueryUtils.createDomainQuery("BlRelease")
                    .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, bl.getPrimaryKey()))
                    .addDqPredicate(PredicateFactory.isNotNull(MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherQty")))
                    .addDqAggregateField(AggregateFunctionType.SUM,MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFFOtherQty"));
                    Double totalOtherQty = QueryUtil.getQuerySum(domainQuery);
                    bl.setBlFlexDouble01(totalOtherQty);
                    HibernateApi.getInstance().save(bl);
                    ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);
                    if (totalOtherQty >0) {
                        Serializable holdGkey = servicesManager.applyHold("OTHER_PORT_RELEASE", bl, null, null, "Other port hold posted")
                    } else {
                        Serializable vetoGkey = servicesManager.applyPermission("OTHER_PORT_RELEASE", bl,null, null, "Other port hold release");
                    }
                    RoadBizUtil.commit();
                    //BizViolation warning = BizViolation.create(PropertyKeyFactory.valueOf("PORTCODE_NOT_DEFINED"), null,
                    //        "Cannot post the 350 message for the port code: " + portCode + ". Since this port code is not in General Reference.");
                    //collector.appendMessage(warning);
                }
            }
        }
    }

    /**
     * Get the port codes from General Reference.
     * @param inGeneralReference
     * @return
     */
    private static List<String> getPortCodesFromGeneralReference(GeneralReference inGeneralReference) {
        List<String> portCodesList = new ArrayList();
        String[] dataValueArray = new String[6];
        dataValueArray[0] = inGeneralReference.getRefValue1();
        dataValueArray[1] = inGeneralReference.getRefValue2();
        dataValueArray[2] = inGeneralReference.getRefValue3();
        dataValueArray[3] = inGeneralReference.getRefValue4();
        dataValueArray[4] = inGeneralReference.getRefValue5();
        dataValueArray[5] = inGeneralReference.getRefValue6();
        for (String dataValue : dataValueArray) {
            if (dataValue == null) {
                continue;
            }
            String[] arrayOfStrings = dataValue.split(',');
            for (int i = 0; i < arrayOfStrings.length; i++) {
                arrayOfStrings[i] = arrayOfStrings[i].trim();
            }
            portCodesList.addAll(new ArrayList(asList(arrayOfStrings)));
        }
        return portCodesList;
    }

    /**
     * This method is being used in beforeEdiPost method
     * Method to validate the routing point(Schedule D code) of the facility is same as the value of ediReleaseFlexString01 in message.
     * @param inReleaseTransaction
     */
    private void validateRoutingPoint(ReleaseTransactionDocument.ReleaseTransaction inReleaseTransaction, Map inParams) {
        EdiReleaseFlexFields releaseFlexFields = inReleaseTransaction.getEdiReleaseFlexFields();
        if (releaseFlexFields != null && releaseFlexFields.getEdiReleaseFlexString01() != null) {

            //Get posting context for configuration value
            EdiPostingContext postingContext = ContextHelper.getThreadEdiPostingContext();
            String facilityId = (String) ArgoEdiUtils.getConfigValue(postingContext, ArgoConfig.EDI_FACILITY_FOR_POSTING);

            //if facility is empty then throw an error and skip the posting since we cannot validate without facility id in setting
            if (StringUtils.isEmpty(facilityId)) {
                inParams.put(SKIP_POSTER, true);
                throw BizViolation.create(PropertyKeyFactory.valueOf("Facility Id is empty/null in setting EDI_FACILITY_FOR_POSTING"), null);
            }

            //Throw an error if facility not found for given id and skip the posting
            Facility facility = Facility.findFacility(facilityId, ContextHelper.getThreadComplex());
            if (facility == null) {
                inParams.put(SKIP_POSTER, true);
                throw BizViolation.create(PropertyKeyFactory.valueOf("Could not find the Facility for Id: " + facilityId), null);
            }

            // If routing point(Schedule D code) of the facility is not same as the value of ediReleaseFlexString01 in message then throw an error and
            // skip the posting
            String scheduledDCode = facility.getFcyRoutingPoint().getPointScheduleDCode();
            String messageScheduleDCode = releaseFlexFields.getEdiReleaseFlexString01();
            if (!messageScheduleDCode.equals(scheduledDCode)) {
                inParams.put(SKIP_POSTER, true);
                throw BizViolation
                        .create(PropertyKeyFactory.valueOf("EDI release message is not for this port, port schedule Dcode:" + scheduledDCode +
                                "  does not match with message schedule D code:" + messageScheduleDCode), null);
            }
        }
    }

    /**
     * Method will be called from EDI engine
     */
    @Override
    public void afterEdiPost(XmlObject inXmlTransactionDocument, HibernatingEntity inHibernatingEntity, Map inParams) {

        //if value is true then skip after edi post method
        if (Boolean.TRUE.equals(inParams.get(SKIP_POSTER))) {
            LOGGER.info("Skipped after edi post method.");
            return;
        }
        if (inXmlTransactionDocument == null || !ReleaseTransactionsDocument.class.isAssignableFrom(inXmlTransactionDocument.getClass())) {
            return;
        }
        ReleaseTransactionsDocument releaseDocument = (ReleaseTransactionsDocument) inXmlTransactionDocument;
        ReleaseTransactionsDocument.ReleaseTransactions releaseTrans = releaseDocument.getReleaseTransactions();
        ReleaseTransactionDocument.ReleaseTransaction[] releaseTransArray = releaseTrans.getReleaseTransactionArray();

        if (releaseTransArray == null || releaseTransArray.length == 0) {
            LOGGER.info("Release Array is NULL in after EDI post method");
            return;
        }

        ReleaseTransactionDocument.ReleaseTransaction releaseTransaction = releaseTransArray[0];
        String ediCode = releaseTransaction.getEdiCode();

        LOGGER.info("EDI CODE: " + ediCode);
        if (ediCode == null) {
            return;
        }

        //flush the session to persist the release which is being posted
        HibernateApi.getInstance().flush();
        BillOfLading bl = null;
        EdiReleaseIdentifier releaseIdentifier = releaseTransaction.getEdiReleaseIdentifierArray(0);
        String blNbr = releaseIdentifier.getReleaseIdentifierNbr();
        if (blNbr != null) {
            LineOperator lineOp = (LineOperator) findLineOperator(releaseTransaction);
            bl = BillOfLading.findBillOfLading(blNbr, lineOp, null);
        }

        // write the error
        if (ArgoUtils.isEmpty(blNbr) || bl == null) {
            LOGGER.error("Bill Of Lading not found for BlNbr: " + blNbr);
            return;
        }

        /* //handle 54 disposition code for US customs
        if ("54".equals(ediCode)) {
          handle54(bl);
        }*/

        //handle 55 disposition code for US customs
        if ("55".equals(ediCode)) {
            handle55(releaseTransaction, bl);
        }

        UpdateInbondStatus(ediCode, bl);
        //handle 1J&95 disposition code for US customs
        if ("95".equals(ediCode) || "1J".equalsIgnoreCase(ediCode)) {
            // record 1J received event
            if ("1J".equalsIgnoreCase(ediCode)) {
                recordServiceEvent(bl, ADD_EVENT_1J_STR);
                updateInbondStatus(bl);
            }
            handle1JUsing95(releaseTransaction, bl);
        }

        //GoLive Issue: 83 should subtract 1W release Qty
        if ("83".equals(ediCode) || "1W".equalsIgnoreCase(ediCode)) {
            // record 1J received event
            if ("1W".equalsIgnoreCase(ediCode)) {
                recordServiceEvent(bl, ADD_EVENT_1W_STR);
                updateInbondStatus(bl);
            }
            handle1WUsing83(releaseTransaction, bl);
        }

        //update inbond status only if there is active 1J
        if (("1C".equalsIgnoreCase(ediCode) || "1W".equalsIgnoreCase(ediCode)) && hasActive1J(bl.getBlGkey())) {
            updateInbondStatus(bl);
        }

        /**
         Before uncomment this method call please see comments that are given for handle5H5ILogicBeforePost() in beforeEdiPost()
         * */
        if ("5H".equalsIgnoreCase(ediCode) || "5I".equalsIgnoreCase(ediCode) || "4A".equalsIgnoreCase(ediCode)) {
            handle5H5I4ALogicAfterPost(releaseTransaction, bl, ediCode);
        }
        if ("4E".equalsIgnoreCase(ediCode)) {
            handle4E(bl, releaseTransaction.getReleaseReferenceId());
        }
        //handle  for message type 350 for US customs - Geeta
        LOGGER.info(
                "MoveFlexToRemarks() :Class of inHibernatingEntity is " + inHibernatingEntity.getClass().name);
        //If there is any values in Flex field 02 needs to move to remarks in BL release
        MoveFlexFieldToRemarks(releaseTransaction, bl, ediCode);
        //  HibernateApi.getInstance().flush();   //??? Required ?
        UpdateInbondStatus(ediCode, bl);
        /**
         Before uncomment this method call please see comments that are given for setUniqueReferenceId() in beforeEdiPost()
         * */

        if (DISPOSITION_CODES_FOR_UNIQUE_ID.indexOf(ediCode) >= 0) {
            HibernateApi.getInstance().flush();
            setBackTransactionReferenceId(releaseTransaction, bl, ediCode);
        }

        /**
         * Run BL hold release process
         */
        String releaseRefId = releaseTransaction.getReleaseReferenceId()
        if (releaseTransaction.getVesselCallFacility()) {
            releaseGenRefHolds(ediCode, releaseTransaction.getVesselCallFacility(), bl, releaseRefId)
        }

        //Update the vessel visit from the Release if the BLs carrier visit is gen_vessel
        updateBlCarrierVisit(bl, releaseTransaction, inParams);

        // BI 213107
        if (["1W","83"].contains(ediCode.toUpperCase())) {
            bl.blFlexString01 = (ediCode == "83" ? null : "Yes")
            HibernateApi.getInstance().save(bl)
        }
    }

    private void UpdateInbondStatus(String inDispCode, BillOfLading inBl) {
        if (inDispCode == null || inBl == null) {
            return;
        }
        def offSite = ["1W","1A", "2H", "1H", "71", "72", "73", "4A", "A3"]
        /*if (inDispCode.equals("1J")) {
            inBl.setBlExam(null);
            inBl.setBlInbond(InbondEnum.INBOND);
        } else */ if (inDispCode.equals("95")) {
            inBl.setBlExam(null);
            inBl.setBlInbond(null);
        } else if (inDispCode.equals("1W")) {
            inBl.setBlExam(ExamEnum.OFFSITE);
            inBl.setBlInbond(null);
        } else if (inDispCode.equals("83")) {
            inBl.setBlExam(null);
            inBl.setBlInbond(null);
        } else if (offSite.contains(inDispCode)) {
            //PNCT team wants to manually set the exam status to offsite - Gopal - 03/22/17
            inBl.setBlExam(ExamEnum.OFFSITE);
            inBl.setBlInbond(null);
        } else if (inDispCode.equals("84")) {
            inBl.setBlExam(null);
            inBl.setBlInbond(null);
        }
        inBl.updateUnitExamStatus();
        inBl.updateUnitInbondStatus();
    }

    /**
     * This method is being used in afterEdiPost method
     * This method will be executed if disposition code 95 or 1J(out of order message)
     * * Cancel active 1J using 95 disposition code
     * @param inReleaseTransaction
     * @param inBl
     */
    private void handle1JUsing95(ReleaseTransactionDocument.ReleaseTransaction inReleaseTransaction, BillOfLading inBl) throws BizViolation {
        String referenceId = inReleaseTransaction.getReleaseReferenceId();
        String ediCode = inReleaseTransaction.getEdiCode()
        List nintyFiveBlReleases = findBlReleases(inBl.getBlGkey(), "95");

        //no need to handle if 95 disposition code does not exist
        if (nintyFiveBlReleases.isEmpty()) {
            return;
        }

        // if blRelease is canceled already then don't cancel once again.
        BlRelease nintyFiveBlRelease = null;
        for (BlRelease release : nintyFiveBlReleases) {
            if (isBlReleaseCanceled(inBl.getBlGkey(), release.getBlrelGkey())) {
                continue;
            }
            nintyFiveBlRelease = release;
            break;
        }

        //return if there is no 95 release
        if (nintyFiveBlRelease == null) {
            return;
        }

        boolean isMatchByRef = isQtyMatchByReference(inReleaseTransaction)

        //To find active 1J use 95's reference id.
        if ("1J".equalsIgnoreCase(ediCode)) {
            referenceId = nintyFiveBlRelease.getBlrelReferenceNbr();
        }

        if (referenceId == null && isMatchByRef) {
            LOGGER.error("Could not cancel Active 1J since reference Id is null and MatchQtyByReference is selected in release map LOV.")
            return;
        }

        //Find existing 1J
        List<BlRelease> blRel = findActive1J(inReleaseTransaction, inBl.getBlGkey(), referenceId);
        for (BlRelease release1J : blRel) {
            // By setting blRelReference we are marking the 1J release as cancelled
            release1J.setFieldValue(InventoryCargoField.BLREL_REFERENCE, nintyFiveBlRelease);
            nintyFiveBlRelease.setFieldValue(InventoryCargoField.BLREL_REFERENCE,release1J);
            HibernateApi.getInstance().update(release1J);
            HibernateApi.getInstance().update(nintyFiveBlRelease);
        }

        if (!blRel.isEmpty()) {
            recordServiceEvent(inBl, CANCELED_EVENT_1J_STR);
            updateInbondStatus(inBl);
        }
    }

    private void handle1WUsing83(ReleaseTransactionDocument.ReleaseTransaction inReleaseTransaction, BillOfLading inBl) throws BizViolation {
        String referenceId = inReleaseTransaction.getReleaseReferenceId();
        String ediCode = inReleaseTransaction.getEdiCode();
        Double ediRelQty = (inReleaseTransaction.getReleaseQty() != null) ? Double.valueOf(inReleaseTransaction.getReleaseQty()) : 0.0;
        List blReleases83 = findBlReleases(inBl.getBlGkey(), "83");

        //no need to handle if 83 disposition code does not exist
        if (blReleases83.isEmpty()) {
            return;
        }

        // if blRelease is canceled already then don't cancel once again.
        BlRelease blRelease83 = null;
        for (BlRelease release : blReleases83) {
            if (isBlReleaseCanceled(inBl.getBlGkey(), release.getBlrelGkey())) {
                continue;
            }
            blRelease83 = release;
            break;
        }

        //return if there is no 83 release
        if (blRelease83 == null) {
            return;
        }

        boolean isMatchByRef = isQtyMatchByReference(inReleaseTransaction)

        //To find active 1W use 83's reference id.
        if ("1W".equalsIgnoreCase(ediCode)) {
            referenceId = blRelease83.getBlrelReferenceNbr();
        }

        if (referenceId == null && isMatchByRef) {
            LOGGER.error("Could not cancel Active 1W since reference Id is null and MatchQtyByReference is selected in release map LOV.")
            return;
        }

        //Find existing 1W
        List<BlRelease> blRel = findActive1W(inReleaseTransaction, inBl.getBlGkey(), referenceId);
        for (BlRelease release1W : blRel) {
            if (!blRel.isEmpty()) {
                recordServiceEvent(inBl, CANCELED_EVENT_1W_STR);
                updateInbondStatus(inBl);
                if ("83".equalsIgnoreCase(ediCode)) {
                    ediRelQty = ediRelQty - (ediRelQty * 2); //Make as Negatative
                    blRelease83.setFieldValue(InventoryCargoField.BLREL_QUANTITY, ediRelQty);
                    HibernateApi.getInstance().update(blRelease83);
                }
            }

            // By setting blRelReference we are marking the 1W release as cancelled
            release1W.setFieldValue(InventoryCargoField.BLREL_REFERENCE, blRelease83);
            HibernateApi.getInstance().update(release1W);
        }
    }

    private void handle4E(BillOfLading inBl, String inReferenceNbr) {
        //We will make sure that 4E disposition code is written to the database before we begin our adjustment process
        HibernateApi.getInstance().flush();

        //find active 1A, 1B or 1C disposition codes that are received till now for the given 4E reference nbr. If a 1A, 1B and 1C is already
        //cancelled by a 4E then the BL Release Reference Entity for that 1A,1B, 1C BLRelease will be set to the gkey of the 4E that cancelled it and
        //BL Release Reference Entity for 4E will be the gkey of the BLRelease that it cancels.
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBl.getPrimaryKey()))
                .addDqPredicate(PredicateFactory.in(InventoryCargoField.BLREL_DISPOSITION_CODE, [DISP_CODE_1A, DISP_CODE_1B, DISP_CODE_1C]))
                .addDqPredicate(PredicateFactory.isNull(InventoryCargoField.BLREL_REFERENCE))
        // the 1A, 1B and 1C with customs hold. The reason is, when the 4E is posted it was canceling the 1C(information) instead of 1C(Release) which
        // end up not decreasing the release qty.
                .addDqPredicate(PredicateFactory.eq(BLREL_FLAG_TYPE_ID, DISP_CODE_1C_HOLD_ID))
        //add order by predicate by BlREl post date and blrel_gkey;
                .addDqOrdering(Ordering.desc(InventoryCargoField.BLREL_POST_DATE))
                .addDqOrdering(Ordering.desc(InventoryCargoField.BLREL_GKEY));

        //add referebce nbr predicate to domain query if inReferenceNbr isnot null only
        if (inReferenceNbr != null) {
            dq.addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_REFERENCE_NBR, inReferenceNbr));
        }
        List<BlRelease> blReleases = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

        //find the BL Releases that were created as a result of receiving 4E.
        DomainQuery dq1 = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBl.getPrimaryKey()))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, DISP_CODE_4E))
                .addDqPredicate(PredicateFactory.isNull(InventoryCargoField.BLREL_REFERENCE))
                .addDqOrdering(Ordering.asc(InventoryCargoField.BLREL_POST_DATE));
        //add referebce nbr predicate to domain query if inReferenceNbr is not null only
        if (inReferenceNbr != null) {
            dq1.addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_REFERENCE_NBR, inReferenceNbr))
        }
        List<BlRelease> blReleases4E = HibernateApi.getInstance().findEntitiesByDomainQuery(dq1);
        //4E bel rel size is always 2 as 4E has two release map
        LOGGER.info("BL 4ERelease size : " + blReleases4E.size());

        //if the first entry(order by gkey and postdate) is 1C then we don't
        if (blReleases != null && blReleases.size() > 0) {
            BlRelease blRelease = blReleases.get(0);
            if (DISP_CODE_1C.equalsIgnoreCase(blRelease.getBlrelDispositionCode())) {
                LOGGER.info("Disp 1C is found as first record in list so only 4E cancels 1C disp code alone and other 4E entries will be nullified ");
                BlRelease rel4EFor1CDisp = get4EReleaseForHoldId(blReleases4E, DISP_CODE_1C_HOLD_ID);
                rel4EFor1CDisp.setFieldValue(InventoryCargoField.BLREL_QUANTITY_TYPE, blRelease.getBlrelQuantityType());
                rel4EFor1CDisp.setFieldValue(InventoryCargoField.BLREL_REFERENCE, blRelease);
                blRelease.setFieldValue(InventoryCargoField.BLREL_REFERENCE, rel4EFor1CDisp);
                //1A followd by 1C followed by 4E case; system release the 1A hold as there is an active 1A hold exist so here
                //we need to reapply the hold as 1C
                if (blReleases.size() > 1) {
                    BlRelease rel1ADisp = blReleases.get(1);
                    if (DISP_CODE_1A.equalsIgnoreCase(rel1ADisp.getBlrelDispositionCode())) {
                        String holdId = rel1ADisp.getBlrelFlagType().getFlgtypId().trim();
                        _sm.applyHold(holdId, inBl, null, inReferenceNbr, holdId);
                    }
                }

                setBlRelModifyQtyToNullForAllBlReleases(blReleases4E, rel4EFor1CDisp);
            } else if (DISP_CODE_1A.equalsIgnoreCase(blRelease.getBlrelDispositionCode())) {
                LOGGER.info("Disp 1A is found as first record in list so 4E cancels 1A disp code alone and other 4E entries will be nullified ");
                BlRelease rel4EFor1ADisp = get4EReleaseForHoldId(blReleases4E, blRelease.getBlrelFlagType().getFlgtypId());
                rel4EFor1ADisp.setFieldValue(InventoryCargoField.BLREL_QUANTITY_TYPE, blRelease.getBlrelQuantityType());
                rel4EFor1ADisp.setFieldValue(InventoryCargoField.BLREL_REFERENCE, blRelease);
                blRelease.setFieldValue(InventoryCargoField.BLREL_REFERENCE, rel4EFor1ADisp);
                setBlRelModifyQtyToNullForAllBlReleases(blReleases4E, rel4EFor1ADisp);
            } else if (DISP_CODE_1B.equalsIgnoreCase(blRelease.getBlrelDispositionCode())) {
                boolean isDisp1AFollowedBy1B = false;
                for (BlRelease blRel : blReleases) {
                    if (DISP_CODE_1A.equalsIgnoreCase(blRel.getBlrelDispositionCode())) {
                        LOGGER.info("Disp 1A and 1B are found as first and second records in list so 4E cancels both 1A and 1B disp codes");
                        BlRelease rel4EFor1ADisp = get4EReleaseForHoldId(blReleases4E, blRel.getBlrelFlagType().getFlgtypId());
                        rel4EFor1ADisp.setFieldValue(InventoryCargoField.BLREL_QUANTITY_TYPE, blRel.getBlrelQuantityType());
                        rel4EFor1ADisp.setFieldValue(InventoryCargoField.BLREL_REFERENCE, blRel);
                        blRel.setFieldValue(InventoryCargoField.BLREL_REFERENCE, rel4EFor1ADisp);
                        if (blReleases4E.size() == 1) {
                            //create second 4E bl release for canceling 1B entry as there is only one release map defined for 4E
                            BlRelease rel4E1B = new BlRelease();
                            rel4E1B.setFieldValue(InventoryCargoField.BLREL_BL, inBl);
                            rel4E1B.setFieldValue(InventoryCargoField.BLREL_QUANTITY, rel4EFor1ADisp.getBlrelQuantity());
                            rel4E1B.setFieldValue(InventoryCargoField.BLREL_FLAG_TYPE, rel4EFor1ADisp.getBlrelFlagType());
                            rel4E1B.setFieldValue(InventoryCargoField.BLREL_QUANTITY_TYPE, EdiReleaseMapModifyQuantityEnum.ReleasedQuantity);
                            rel4E1B.setFieldValue(InventoryCargoField.BLREL_REFERENCE_NBR, rel4EFor1ADisp.getBlrelReferenceNbr());
                            rel4E1B.setFieldValue(InventoryCargoField.BLREL_DISPOSITION_CODE, rel4EFor1ADisp.getBlrelDispositionCode());
                            rel4E1B.setFieldValue(InventoryCargoField.BLREL_NOTES, rel4EFor1ADisp.getBlrelNotes());
                            rel4E1B.setFieldValue(InventoryCargoField.BLREL_POST_DATE, rel4EFor1ADisp.getBlrelPostDate());
                            rel4E1B.setFieldValue(InventoryCargoField.BLREL_REFERENCE, blRelease);
                            HibernateApi.getInstance().save(rel4E1B);
                        } else {
                            BlRelease rel4EFor1BDisp = get4EReleaseForHoldId(blReleases4E, DISP_CODE_1C_HOLD_ID);
                            rel4EFor1BDisp.setFieldValue(InventoryCargoField.BLREL_QUANTITY_TYPE, blRelease.getBlrelQuantityType());
                            rel4EFor1BDisp.setFieldValue(InventoryCargoField.BLREL_REFERENCE, blRelease);
                            blRelease.setFieldValue(InventoryCargoField.BLREL_REFERENCE, rel4EFor1BDisp);
                        }
                        isDisp1AFollowedBy1B = true;
                    }
                }
                if (!isDisp1AFollowedBy1B) {
                    LOGGER.info("Disp 1B alone exist with out 1A which is practically not correct so nullifying all 4E entries ");
                    setBlRelModifyQtyToNullForAllBlReleases(blReleases4E, null);
                }
            } else {
                //this block gets executed if selected bl releases are nither 1A,1B nor 1C.
                LOGGER.info("Either 1A,1b or 1C expected but blrelease with disp code" + blRelease.getBlrelDispositionCode() +
                        "is presented wrongly so nullifying all 4E entries");
                setBlRelModifyQtyToNullForAllBlReleases(blReleases4E, null);
            }
        } else {
            LOGGER.info("Neither 1A,1b or 1C are found so nullifying all4E entries");
            setBlRelModifyQtyToNullForAllBlReleases(blReleases4E, null);
        }

        HibernateApi.getInstance().flush();
    }

    //this method Iterate each blrelease from given list and nullify it's modify quantity to null so that release qty will not have any impact on Bl
    //quantities. it skips to nullify if any BlRelease is given to skip.
    private void setBlRelModifyQtyToNullForAllBlReleases(List<BlRelease> inBlRels, BlRelease inToSkipUpdate) {
        for (BlRelease release4E : inBlRels) {
            if (inToSkipUpdate != null && inToSkipUpdate.equals(release4E)) {
                continue;
            }
            release4E.setFieldValue(InventoryCargoField.BLREL_QUANTITY_TYPE, null);
            release4E.setFieldValue(InventoryCargoField.BLREL_REFERENCE, release4E); // We will put the reference as itself
        }
    }

    /**
     * This method is being used in afterEdiPost method
     * This method will be be executed if disposition code is 54
     * set the manifest quantity to zero.
     * @param inBl
     */
    private void handle54(BillOfLading inBl) {
        inBl.setFieldValue(InventoryCargoField.BL_MANIFESTED_QTY, Double.valueOf("0.0"));
        HibernateApi.getInstance().update(inBl);
        recordServiceEvent(inBl, RECEIVED_54_STR);
    }

    /**
     * This method is being used in afterEdiPost method
     * This method will be be executed if disposition code is 55
     * Update manifest quantity with the quantity received in the 350 message
     * @param inRelease
     * @param inBl
     */
    private void handle55(ReleaseTransactionDocument.ReleaseTransaction inRelease, BillOfLading inBl) {
        Double qty = getQty(inRelease);
        inBl.setFieldValue(InventoryCargoField.BL_MANIFESTED_QTY, qty);
        recordServiceEvent(inBl, RECEIVED_55_STR);
        HibernateApi.getInstance().update(inBl);
    }

    /**
     * This method is being used in afterEdiPost method
     * This method will be be executed  for all  disposition codes  and message Type 350
     * Update Remarks with the string received in the 350 message flex field 01
     * @param inRelease
     * @param inBl
     */
    private void MoveFlexFieldToRemarks(ReleaseTransactionDocument.ReleaseTransaction inRelease, BillOfLading inBl, String ediCode) {
        EdiReleaseFlexFields flexFields = inRelease.getEdiReleaseFlexFields();
        LOGGER.info(
                "MoveFlexFieldToRemarks() Starts: ");
        if (flexFields == null) {
            LOGGER.info(
                    "MoveFlexFieldToRemarks() : value of  inRelease.getEdiReleaseFlexFields() is empty");
            return;
        }
        String flexField02 = flexFields.getEdiReleaseFlexString02()
        if (flexField02 == null) {
            LOGGER.info(
                    "MoveFlexFieldToRemarks() : value of  flexField02 is null");
            return;
        }
        if (flexField02.isEmpty()) {
            LOGGER.info(
                    "MoveFlexFieldToRemarks() : value of  flexField02 is empty");
            return
        };
        if (flexField02.length() > 4000) //Remarks field is 4000 characters
        {
            flexField02 = flexField02.substring(0, 3999)
        }
        Date relDate = ((org.apache.xmlbeans.XmlCalendar) inRelease.getReleasePostDate()).getTime();

        BlRelease blRel = findLatestBlReleaseForDispCodeAndBL(inBl.getBlGkey(), ediCode)
        if (blRel != null) {
            // blRel.setFieldValue for cutom field is giving error in log file. So create hashmap is to supress this error.
            if (blRel.getCustomFlexFields() == null) {
                blRel.setCustomFlexFields(new HashMap<String, Object>())
            }
            blRel.setFieldValue(MetafieldIdFactory.valueOf("customFlexFields.blrelCustomDFF_REMARKS"), flexField02);
            HibernateApi.getInstance().update(blRel);
            LOGGER.info("MoveFlexFieldToRemarks :" + flexFields.getEdiReleaseFlexString02() + "is set for ediCode:  " + inRelease.getEdiCode() +
                    " flexField02 " + flexField02);
        }
    }

    // this method iterate each blrelease from list and return the blrelease which matches it's flagtype with given flagtype
    private BlRelease get4EReleaseForHoldId(List<BlRelease> inblReleases, String inHoldId) {
        if (inblReleases != null) {
            for (BlRelease blrel : inblReleases) {
                String holdId = blrel.getBlrelFlagType().getFlgtypId().trim();
                if (inHoldId.trim().equalsIgnoreCase(holdId)) {
                    return blrel;
                }
            }
        }
        return null;
    }

    /**
     * This method is being used in afterEdiPost method
     * Update the Inbond status to CANCEL/INBOND. If manifest qty is less than or equal to inbond qty then inbond status should be INBOND otherwise
     * the status should be CANCEL
     * @param inBl
     */
    private void updateInbondStatus(BillOfLading inBl) {
        Serializable blGkey = inBl.getBlGkey();
        HibernateApi.getInstance().flush();

        //Determine the inbond quantity for BL.
        List<BlRelease> blReleases = findActiveInbond(blGkey);
        Double inbondQtySum = 0;
        List referenceIdList = new ArrayList();
        // Here we will find all the Active 1J's. BlRelease with disposition code 1J is active if BlReleaseReference is NOT populated
        for (BlRelease blRelease : blReleases) {
            String refId = blRelease.getBlrelReferenceNbr();
            //ignore the duplicate 1J
            if ("1J".equalsIgnoreCase(blRelease.getBlrelDispositionCode()) || "1W".equalsIgnoreCase(blRelease.getBlrelDispositionCode())) {
                /*if (referenceIdList.contains(refId)) {
                    continue;
                }*/
                referenceIdList.add(refId);
            }
            // We need to add the quantities for 1J, 1C  disposition codes only for inbond quantity check.
            Double qty = blRelease.getBlrelQuantity();
            if (qty != null) {
                inbondQtySum = inbondQtySum + qty;
                LOGGER.info("inbondQtySum=" + inbondQtySum);
            }
        }

        //If InbondQty is equal to manifest quantity, set inbond status to INBOND otherwise CANCEL
        Double blManifestQty = inBl.getBlManifestedQty();

        if (blManifestQty != null) {
            // if the bl release sum inbond quantity is greater than or equal to manifested quantity
            if (blManifestQty <= inbondQtySum) {
                inBl.updateInbond(InbondEnum.INBOND);
            } else {
                inBl.updateInbond(InbondEnum.CANCEL);
            }
            LOGGER.info("inbondQtySum=" + inbondQtySum + " blManifestQty=" + blManifestQty);
        }
        HibernateApi.getInstance().update(inBl);
    }

    /**
     * This method is being used in updateInbondStatus method
     * Find BL Release for given BOL and reference is null
     * @param inBlGkey
     * @return
     */
    private List<BlRelease> findActiveInbond(Serializable inBlGkey) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBlGkey))
                .addDqPredicate(PredicateFactory.in(InventoryCargoField.BLREL_DISPOSITION_CODE, ["1J", "1C", "1W"]))
                .addDqOrdering(Ordering.asc(InventoryCargoField.BLREL_CREATED))
                .addDqPredicate(PredicateFactory.isNull(InventoryCargoField.BLREL_REFERENCE));
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
    }

    /**
     * This method is being used in afterEdiPost method
     * Find Line Operator
     * @param inRelease
     * @return
     */
    private ScopedBizUnit findLineOperator(ReleaseTransactionDocument.ReleaseTransaction inRelease) {
        ShippingLine ediLine = inRelease.getEdiShippingLine();
        if (ediLine != null) {
            String lineCode = ediLine.getShippingLineCode();
            String lineCodeAgency = ediLine.getShippingLineCodeAgency();
            return ScopedBizUnit.resolveScopedBizUnit(lineCode, lineCodeAgency, BizRoleEnum.LINEOP);
        }
        return null;
    }

    /**
     * This method is being used in handle1JUsing95 method
     * Check BlRelease is canceled by another Bl release
     * @param inBlGkey
     * @param inBlRelGkey
     * @return
     */
    private boolean isBlReleaseCanceled(Serializable inBlGkey, Serializable inBlRelGkey) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBlGkey))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_REFERENCE, inBlRelGkey));
        return HibernateApi.getInstance().existsByDomainQuery(dq);
    }

    /**
     * This method is being used in handle55 method
     * Get release quantity
     * @param inRelease
     * @return
     */
    private Double getQty(ReleaseTransactionDocument.ReleaseTransaction inRelease) {
        String qtyString = inRelease.getQty();
        Double qty = safeGetDouble(qtyString);
        if (qty == null) {
            String releaseQtyString = inRelease.getReleaseQty();
            qty = safeGetDouble(releaseQtyString);
        }
        if (qty == null) {
            // assume the qty as 0.0
            qty = 0.0;
        }
        return qty;
    }

    /**
     * This method is being used in getQty method
     * convert string to Double
     * @param inNumberString
     * @return
     */
    private Double safeGetDouble(String inNumberString) {
        Double doubleObject = null;
        if (!StringUtils.isEmpty(inNumberString)) {
            try {
                doubleObject = new Double(inNumberString);
            } catch (NumberFormatException e) {
                throw e;
            }
        }
        return doubleObject;
    }

    /**
     * This method is being used in afterEdiPost,handle55, handle54 and handle1JUsing95 methods
     * Record BOL Event
     * @param inBl
     * @param inEventId
     */
    private void recordServiceEvent(BillOfLading inBl, String inEventId) {
        EventType eventType = EventType.findOrCreateEventType(inEventId, "Customs Event", LogicalEntityEnum.BL, null);
        FieldChanges fld = new FieldChanges();
        fld.setFieldChange(InventoryCargoField.BL_GKEY, inBl.getBlGkey());
        fld.setFieldChange(InventoryCargoField.BL_NBR, inBl.getBlNbr());
        fld.setFieldChange(InventoryCargoField.BL_INBOND, inBl.getBlInbond());
        if (eventType != null) {
            inBl.recordBlEvent(eventType, fld, "recorded through groovy", null);
        }
    }

    /**
     * This method is being used in handle1JUsing95 method
     * Find bl releases using posting date and disposition code
     * @param inBlGkey
     * @param inDispositionCode
     * @return
     */
    private List<BlRelease> findBlReleases(Serializable inBlGkey, String inDispositionCode) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBlGkey))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, inDispositionCode));
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
    }

    /**
     * This method is being used to get BL releases
     * Find bl releases using posting date and disposition code
     * @param inBlGkey
     * @param inDispositionCode
     * @param inPostedDate
     * @return
     */
    private List<BlRelease> findBlReleases(Serializable inBlGkey, String inDispositionCode, Date inPostedDate) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBlGkey))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, inDispositionCode))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_POST_DATE, inPostedDate));
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
    }

    /**
     * This method is being used in handle1JUsing95 method
     * find active 1J BlReleases
     */
    private List<BlRelease> findActive1J(ReleaseTransactionDocument.ReleaseTransaction inReleaseTransaction, Serializable inBlGkey,
                                         String inReferenceId) throws BizViolation {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBlGkey))
                .addDqPredicate(PredicateFactory.isNull(InventoryCargoField.BLREL_REFERENCE))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, "1J"));
        boolean isQtyMatchByReferenceNbr = isQtyMatchByReference(inReleaseTransaction);
        if (isQtyMatchByReferenceNbr) {
            dq.addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_REFERENCE_NBR, inReferenceId));
        }
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
    }

    /**
     * This method is being used in handle1WUsing83 method
     * find active 1J BlReleases
     */
    private List<BlRelease> findActive1W(ReleaseTransactionDocument.ReleaseTransaction inReleaseTransaction, Serializable inBlGkey,
                                         String inReferenceId) throws BizViolation {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBlGkey))
                .addDqPredicate(PredicateFactory.isNull(InventoryCargoField.BLREL_REFERENCE))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, "1W"));
        boolean isQtyMatchByReferenceNbr = isQtyMatchByReference(inReleaseTransaction);
        if (isQtyMatchByReferenceNbr) {
            dq.addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_REFERENCE_NBR, inReferenceId));
        }
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
    }

    /**
     * This method is being used in afterEdiPost method
     * Check any active 1J exist or not
     */
    private boolean hasActive1J(Serializable inBlGkey) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBlGkey))
                .addDqPredicate(PredicateFactory.isNull(InventoryCargoField.BLREL_REFERENCE))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, "1J"));
        return HibernateApi.getInstance().existsByDomainQuery(dq);
    }

    private boolean hasActive1W(Serializable inBlGkey) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBlGkey))
                .addDqPredicate(PredicateFactory.isNull(InventoryCargoField.BLREL_REFERENCE))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, "1W"));
        return HibernateApi.getInstance().existsByDomainQuery(dq);
    }

    private static boolean has1W(Serializable inBlGkey) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBlGkey))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, "1W"));

        return HibernateApi.getInstance().existsByDomainQuery(dq);
    }

    /**
     * This method is being used in isQtyMatchByReference method
     * find all release map for given disposition code and message type. Extract the release by BL hold/perm.
     *
     * @param inReleaseTransaction -   Release transaction
     * @param inEdiCodeSet -   Edi Code Set
     * @return IReleaseMap -   release map
     * @throws com.navis.framework.util.BizViolation -   BizViolation
     */
    private IReleaseMap findReleaseMapsFor95(ReleaseTransactionDocument.ReleaseTransaction inReleaseTransaction) throws BizViolation {
        ArgoEdiFacade ediFacade = (ArgoEdiFacade) Roastery.getBean(ArgoEdiFacade.BEAN_ID);
        String msgId = inReleaseTransaction.getMsgTypeId();
        String msgVersion = inReleaseTransaction.getMsgVersion();
        String msgReleaseNumber = inReleaseTransaction.getMsgReleaseNbr();
        Set ediCodeSet = new HashSet();
        ediCodeSet.add("95");

        List<IReleaseMap> releaseMaps =
                ediFacade.findEdiReleaseMapsForEdiCodes(msgId, ediCodeSet, msgVersion, msgReleaseNumber, LogicalEntityEnum.BL);
        String msg = "Map Code: " + inReleaseTransaction.getEdiCode() + " Message Id: " + msgId + ", Message Version: " + msgVersion +
                ", Release Number: " + msgReleaseNumber;
        if (releaseMaps.isEmpty()) {
            throw BizViolation.create(PropertyKeyFactory.valueOf("Could not find the release map for the condition: " + msg), null);
        }

        if (releaseMaps.size() > 1) {
            throw BizViolation.create(PropertyKeyFactory.valueOf("Found multiple release map for the condition: " + msg), null);
        }
        return releaseMaps.get(0);
    }

    /**
     * This method is being used in handle1JUsing95 method
     * return true if match qty is "Match Qty By Reference" ion release map configuration
     * @param inReleaseTransaction
     * @return
     * @throws BizViolation
     */
    private boolean isQtyMatchByReference(ReleaseTransactionDocument.ReleaseTransaction inReleaseTransaction) throws BizViolation {
        IReleaseMap releaseMap = findReleaseMapsFor95(inReleaseTransaction);
        return releaseMap == null ? false : EdiReleaseMapQuantityMatchEnum.MatchQtyByReference.equals(releaseMap.getEdirelmapMatchQty());
    }

    //RAMAN:sets the transaction reference nbr to EdiReleaseFlexString01 if reference nbr is not empty and generates an UID(Unique Identifier)
    // and sets UID to transaction reference nbr so that release poster creates multiple holds.

    private void setUniqueReferenceId(ReleaseTransactionDocument.ReleaseTransaction inReleaseTransaction, String inEdiCode) {
        LOGGER.info(" setUniqueReferenceId(): starts for edi code " + inEdiCode);
        String refId = inReleaseTransaction.getReleaseReferenceId();
        //backup of ReleaseReferenceId if it is not empty.
        if (StringUtils.isNotEmpty(refId)) {
            EdiReleaseFlexFields flexFields = inReleaseTransaction.getEdiReleaseFlexFields();
            //add a new EdiReleaseFlexFields to release transaction if one is not available
            if (flexFields == null) {
                flexFields = inReleaseTransaction.addNewEdiReleaseFlexFields();
            }
            //sets the  ReleaseReferenceId to EdiReleaseFlexString01
            flexFields.setEdiReleaseFlexString01(refId);
        }
        //sets the generated random UID to ReleaseReferenceId
        inReleaseTransaction.setReleaseReferenceId(UUID.randomUUID().toString());
    }

    //RAMAN:sets back the transaction reference nbr which is stored in ReleaseFlexString01 to Bl release entity if ReleaseFlexString01 nbr is not empty, system
    // skips to set back value if ReleaseFlexString01 is empty in this case BL release will have system generated UID as reference nbr
    private void setBackTransactionReferenceId(ReleaseTransactionDocument.ReleaseTransaction inReleaseTransaction, BillOfLading inBillOfLading,
                                               String inEdiCode) {
        LOGGER.info(" setBackTransactionReferenceId(): starts for edi code " + inEdiCode);
        EdiReleaseFlexFields flexFields = inReleaseTransaction.getEdiReleaseFlexFields();
        //skip to revert back the getEdiReleaseFlexString01 is empty so BL release will have system generated UID as reference nbr
        if (flexFields != null && flexFields.getEdiReleaseFlexString01() != null) {
            BlRelease blrel = findLatestBlReleaseForDispCodeAndBL(inBillOfLading.getBlGkey(), inEdiCode);
            if (blrel != null) {
                blrel.setFieldValue(InventoryCargoField.BLREL_REFERENCE_NBR, flexFields.getEdiReleaseFlexString01())
                LOGGER.info("setBackTransactionReferenceId() :" + flexFields.getEdiReleaseFlexString01() + "is set back to ediCode: " + inEdiCode);
            } else {
                LOGGER.info("setBackTransactionReferenceId() : blrel is null !");
            }
        } else {
            LOGGER.info(
                    "setBackTransactionReferenceId() : value of flexFields.getEdiReleaseFlexString01() is empaty so systed did not revert back the unique reference id");
        }
    }

    //RAMAN: find BlRelease Latest BL Release for given Bl and disposition code desc order
    private BlRelease findLatestBlReleaseForDispCodeAndBL(Serializable inBlGkey, String inDispositionCode) {

        LOGGER.info("findLatestBlReleaseForDispCodeAndBL inBlGkey" + inBlGkey + "inDispositionCode :" + inDispositionCode);
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE);
        dq.addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBlGkey))
        dq.addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_DISPOSITION_CODE, inDispositionCode));
        dq.addDqOrdering(Ordering.desc(InventoryCargoField.BLREL_CREATED))
        dq.addDqOrdering(Ordering.desc(InventoryCargoField.BLREL_GKEY));
        List<BlRelease> blreleaseList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        return !blreleaseList.isEmpty() ? blreleaseList.get(0) : null;
    }

    //nullify the release map modify qty if there are is prior 1C or 1b reference exist
    private void handle5H5I4ALogicBeforePost(ReleaseTransactionDocument.ReleaseTransaction inReleaseTransaction, String inEdiCode) {
        LOGGER.info(" handle5H5I4ALogicBeforePost(): starts for edi code " + inEdiCode);
        BillOfLading bl = null;
        if (inReleaseTransaction.getEdiReleaseIdentifierArray() != null && inReleaseTransaction.getEdiReleaseIdentifierArray().length > 0) {
            EdiReleaseIdentifier releaseIdentifier = inReleaseTransaction.getEdiReleaseIdentifierArray(0);
            String blNbr = releaseIdentifier.getReleaseIdentifierNbr();
            if (blNbr != null) {
                LineOperator lineOp = (LineOperator) findLineOperator(inReleaseTransaction);
                bl = BillOfLading.findBillOfLading(blNbr, lineOp, null);
            }
        }
        if ((bl == null) || (StringUtils.isNotBlank(inReleaseTransaction.getReleaseReferenceId()) &&
                !find1C1BForBLAndRefNbr(bl.getBlGkey(), inReleaseTransaction.getReleaseReferenceId()))) {
            Set<String> ediCodeSet = new HashSet<String>();
            ediCodeSet.add(inEdiCode);
            ArgoEdiFacade ediFacade = (ArgoEdiFacade) Roastery.getBean(ArgoEdiFacade.BEAN_ID);
            List<IReleaseMap> releaseMaps = ediFacade
                    .findEdiReleaseMapsForEdiCodes(inReleaseTransaction.getMsgTypeId(), ediCodeSet, inReleaseTransaction.getMsgVersion(),
                            inReleaseTransaction.getMsgReleaseNbr(), LogicalEntityEnum.BL);

            for (IReleaseMap relMap : releaseMaps) {
                EdiReleaseMap map = (EdiReleaseMap) relMap;
                map.setFieldValue(EdiField.EDIRELMAP_MODIFY_QTY, null);
                LOGGER.info("handle5H5ILogicBeforePost(): modify qty is changed to null for rel map" + map.getEdirelmapEdiCode());
            }
        }
        LOGGER.info("handle5H5I4ALogicBeforePost(): ends ");
    }

    //sets back the release map modify quantity with release qty
    private void handle5H5I4ALogicAfterPost(ReleaseTransactionDocument.ReleaseTransaction inReleaseTransaction, BillOfLading inBL, String inEdiCode) {
        LOGGER.info(" handle5H5I4ALogicAfterPost(): starts for edi code " + inEdiCode);
        ArgoEdiFacade ediFacade = (ArgoEdiFacade) Roastery.getBean(ArgoEdiFacade.BEAN_ID);
        Set<String> ediCodeSet = new HashSet<String>();
        ediCodeSet.add(inEdiCode);
        List<IReleaseMap> releaseMaps =
                ediFacade.findEdiReleaseMapsForEdiCodes(inReleaseTransaction.getMsgTypeId(), ediCodeSet,
                        inReleaseTransaction.getMsgVersion(), inReleaseTransaction.getMsgReleaseNbr(), LogicalEntityEnum.BL);
        for (IReleaseMap relMap : releaseMaps) {
            EdiReleaseMap map = (EdiReleaseMap) relMap;
            map.setFieldValue(EdiField.EDIRELMAP_MODIFY_QTY, EdiReleaseMapModifyQuantityEnum.ReleasedQuantity);
        }
        LOGGER.info(" handle5H5I4ALogicAfterPost(): ends");
    }

    private boolean find1C1BForBLAndRefNbr(Serializable inBlGkey, String inReferenceId) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_BL, inBlGkey))
                .addDqPredicate(PredicateFactory.in(InventoryCargoField.BLREL_DISPOSITION_CODE, ["1C", "1B"]))
                .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BLREL_REFERENCE_NBR, inReferenceId));
        return HibernateApi.getInstance().existsByDomainQuery(dq);
    }

    /**
     * This method is being used in afterEdiPost method
     * This method will release a bl hold for the ediCode value
     * ediCode must exist in General References for the 350_QUALIFIER Type
     * portId must not exist in General References for the 350_PORT_CODE Type
     */
    private void releaseGenRefHolds(String inEdiCode, EdiFacility inEdiFacility, BillOfLading inBl, String inReleaseRefId) {
        try {
            String portId = inEdiFacility.getFacilityPort().getPortCodes().getId()
            GeneralReference grQual = GeneralReference.findUniqueEntryById("350_QUALIFIER", inEdiCode)
            GeneralReference grPort = GeneralReference.findUniqueEntryById("350_PORT_CODE", portId)

            if (grQual != null && grPort == null) {
                LOGGER.info("attempt to release " + inEdiCode + " hold from bl " + inBl.getBlNbr())
                ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
                servicesManager.applyPermission(inEdiCode, inBl, null, inReleaseRefId, null)
            }
        } catch (Exception e) {
            LOGGER.info("Error applying permission " + e.getMessage())
        }
    }
    //Need to update Hold Permission id that is given for 1C release map
    //1C and 4E are letting the system determine Default hold by qty
    //private final String DISP_CODE_1C_HOLD_ID = "CUSTOMS DEFAULT HOLD";

    /** Update the vessel visit of the BL if it is linked to GEN_VESSEL and Release has a valid vessel visit.
     */

    private void updateBlCarrierVisit(BillOfLading inBl, ReleaseTransactionDocument.ReleaseTransaction inReleaseTransaction, Map inParams) {
        // Update Vessel Visit if the BL has GEN_VESSEL and release has a valid vessel visit
        if (inBl.getBlCarrierVisit().isGenericCv()) {
            try {
                Serializable batchGkey = (Serializable) inParams.get(EdiConsts.BATCH_GKEY);
                LOGGER.warn("BL has generic Carrier visit ");
                if (batchGkey != null) {
                    EdiBatch ediBatch = EdiBatch.hydrate(batchGkey);
                    if (ediBatch != null && ediBatch.getEdibatchCarrierVisit() != null &&
                            !ediBatch.getEdibatchCarrierVisit().isGenericCv()) {
                        LOGGER.warn("Updating BL Carrier visit ");
                        inBl.setBlCarrierVisit(ediBatch.getEdibatchCarrierVisit());
                        HibernateApi.getInstance().saveOrUpdate(inBl);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Error while trying to update BL Carrier visit");
                //ignore errors, do not stop any other update
            }
        }
    }

    /**
     * This method is being used in beforeEdiPost method
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
    private final String DISP_CODE_1C_HOLD_ID = "350_INFORMATION_DISPOSITION";
    private final String DISP_CODE_1A = "1A";
    private final String DISP_CODE_1B = "1B";
    private final String DISP_CODE_1C = "1C";
    private final String DISP_CODE_4E = "4E";

    private final String ADD_EVENT_1J_STR = "RECEIVED_1J";
    private final String ADD_EVENT_1W_STR = "RECEIVED_1W";
    private final String SKIP_POSTER = "SKIP_POSTER";
    private final String CANCELED_EVENT_1J_STR = "CANCELED_1J";
    private final String CANCELED_EVENT_1W_STR = "CANCELED_1W";
    private final String RECEIVED_54_STR = "RECEIVED_54";
    private final String RECEIVED_55_STR = "RECEIVED_55";
    private final String BATCH_GKEY = "BATCH_GKEY";
    private static MetafieldId BATCH_INTERCHANGE_NBR = MetafieldIdFactory.valueOf("editranBatch.edibatchInterchange.ediintInterchangeNbr")
    private static MetafieldId EDITRAN_MSG_REF_NBR = MetafieldIdFactory.valueOf("editranBatch.edibatchMsgRefNbr")
    private List<String> DISPOSITION_CODES_FOR_UNIQUE_ID = new ArrayList<String>();

    {
        EventType.findOrCreateEventType(ADD_EVENT_1J_STR, "Received 1J", LogicalEntityEnum.BL, null);
        EventType.findOrCreateEventType(CANCELED_EVENT_1J_STR, "1J Canceled Event", LogicalEntityEnum.BL, null);
        EventType.findOrCreateEventType(RECEIVED_54_STR, "Received 54", LogicalEntityEnum.BL, null);
        EventType.findOrCreateEventType(RECEIVED_55_STR, "Received 55", LogicalEntityEnum.BL, null);

        DISPOSITION_CODES_FOR_UNIQUE_ID.add("7H");
    }

    private ServicesManager _sm = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);
    private static final BLREL_FLAG_TYPE_ID = MetafieldIdFactory.getCompoundMetafieldId(InventoryCargoField.BLREL_FLAG_TYPE, ServicesField.FLGTYP_ID);
    private static final Logger LOGGER = Logger.getLogger(ITSUSCustomsBLReleaseGvy.class);
}
