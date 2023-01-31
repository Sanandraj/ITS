/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.*
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.Container
import com.navis.argo.business.reference.EquipType
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.cargo.business.model.BillOfLading
import com.navis.edi.business.edimodel.EdiConsts
import com.navis.edi.business.entity.EdiBatch
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.util.BizFailure
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.business.units.EquipmentState
import com.navis.road.business.util.RoadBizUtil
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject

/**
 * @Copyright 2022 - Code written by WeServe LLC
 * @Author .
 *
 * Requirements :
 * #1: To check whether the Visit phase of EDI Vessel Visit is closed.
 * #2: To validate the Non-Visit flag on vessel visit and skips EDI processing if flag is enabled
 * #3: To verify the availability of Line Op against Vessel Visit
 * #4: To check the availability of BL
 * #5: To validate the Hazmat/Over Dimension details against MTY container
 * #6: To validate the container gross weight
 * #7: To update the requested Line Op as Container Owner - Fleet update
 * #8:
 *
 *
 * @Inclusion Location	: Incorporated as a code extension of the type EDI_POST_INTERCEPTOR.
 *  Load Code Extension to N4:
 1. Go to Administration --> System --> Code Extensions
 2. Click Add (+)
 3. Enter the values as below:
 Code Extension Name:  ITSBapliePostInterceptor
 Code Extension Type:  EDI_POST_INTERCEPTOR
 Groovy Code: Copy and paste the contents of groovy code.
 4. Click Save button

 Attach code extension to EDI session:
 1. Go to Administration-->EDI-->EDI configuration
 2. Select the EDI session and right click on it
 3. Click on Edit
 4. Select the extension in "Post Code Extension" tab
 5. Click on save
 *
 */

class ITSBapliePostInterceptor extends AbstractEdiPostInterceptor {
    private static final Logger LOGGER = Logger.getLogger(ITSBapliePostInterceptor.class)

    public void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSBapliePostInterceptor - Execution started.");
        StowplanTransactionsDocument stowplanDocument = (StowplanTransactionsDocument) inXmlTransactionDocument;
        StowplanTransactionsDocument.StowplanTransactions stowplanTransactions = stowplanDocument.getStowplanTransactions();
        List<StowplanTransactionDocument.StowplanTransaction> list = stowplanTransactions.getStowplanTransactionList();
        if (list.isEmpty()) {
            throw BizFailure.create("There is no transaction in the batch");
        }

        try {
            for (StowplanTransactionDocument.StowplanTransaction stowplanTransaction : list) {

                Serializable batchGkey = inParams.get(EdiConsts.BATCH_GKEY)
                EdiBatch ediBatch = batchGkey != null ? EdiBatch.hydrate(batchGkey) : null
                CarrierVisit batchCv = ediBatch.getEdibatchCarrierVisit()
                // EdiVesselVisit ediVesselVisit = stowplanTransaction.getEdiVesselVisit()
                //CarrierVisit ediCv = ediVesselVisit != null ? findEdiCarrierVisit(ediVesselVisit) : null
                VesselVisitDetails vvd = batchCv != null ? VesselVisitDetails.resolveVvdFromCv(batchCv) : null;
//                log(Level.DEBUG, "VVD: " + vvd)
                EdiOperator ediOp = stowplanTransaction.getEdiContainer().getContainerOperator()
                ScopedBizUnit ediLineOp = ediOp != null ? ScopedBizUnit.resolveScopedBizUnit(ediOp.getOperator(), ediOp.getOperatorCodeAgency(), BizRoleEnum.LINEOP) : null
                if (ediLineOp == null) {
                    // registerError("Invalid line operator provided. Skipping EDI.")
                    return
                }
                EdiContainer ediContainer = stowplanTransaction.getEdiContainer()
                if (vvd != null) {
//                    log(Level.DEBUG, "VVD Non-Visit: " + vvd.getVvFlexString01())
                    //Vessel Visit Phase validation - Begin
                    if (CarrierVisitPhaseEnum.CLOSED.equals(vvd.getVvdVisitPhase())) {
                        registerError("Requested Vessel Visit " + batchCv.getCvId() + " is closed, cannot process EDI.")
                        inParams.put(EdiConsts.SKIP_POSTER, true)
                        return
                    }
                    //Vessel Visit Phase validation - End

                    //Non-visit validation - Begin
                    else if (NON_VISIT.equalsIgnoreCase(vvd.getVvFlexString01())) {
                        registerError("EDI received for non-visit vessel " + batchCv.getCvId() + " , cannot process EDI.")
                        inParams.put(EdiConsts.SKIP_POSTER, true)
                        return
                    }

                    //Non-visit validation - End

                    //Vessel Visit Line validation - Begin
                    else if (ediLineOp != null && !vvd.isLineAllowed(ediLineOp)) {
                        registerWarning("Requested Line Op: " + ediLineOp.getBzuId() + " is not available in Vessel Visit " + vvd.getCvdCv().getCvId() + ".")
                    }
                    //Vessel Visit Line validation - End

                }
                List<EdiReference> referenceList = stowplanTransaction.getEdiReferenceList()
                for (EdiReference ediReference : referenceList) {
                    String refNbr = ediReference != null ? ediReference.getReferenceNbr() : null
                    String refType = ediReference != null ? ediReference.getReferenceType() : null
                    if (refNbr != null && refType != null && REF_TYPE.equalsIgnoreCase(refType) && batchCv != null) {
                        BillOfLading bl = BillOfLading.findBillOfLading(refNbr, ediLineOp, batchCv)
                        if (bl == null) {
                            registerWarning("Requested BL " + refNbr + " is not found in N4.")
                        }
                    }
                }

                Equipment ediCtr = ediContainer != null && ediContainer.getContainerNbr() != null ? Container.findEquipment(ediContainer.getContainerNbr()) : null
                double ediGrossWt = ediContainer != null && !StringUtils.isEmpty(ediContainer.getContainerGrossWt()) ? Double.parseDouble(ediContainer.getContainerGrossWt()) : 0D
                String ediCtrStatus = ediContainer != null ? ediContainer.getContainerStatus() : null
                boolean isEmpty = ediCtrStatus != null && CTR_STATUS_MTY.equalsIgnoreCase(ediCtrStatus) ? Boolean.TRUE : Boolean.FALSE
                if (isEmpty) {
                    if (stowplanTransaction.getEdiHazardList() != null && stowplanTransaction.getEdiHazardList().size() > 0) {
                        registerWarning("Hazmat details defined against empty container " + ediContainer.getContainerNbr() + ".")
                    }
                    if (ediContainer.getDimension() != null) {
                        registerWarning("Over dimension details defined against empty container " + ediContainer.getContainerNbr() + ".")
                    }
                }

                if (ediCtr != null) {
                    EquipType ediEqType = StringUtils.isEmpty(ediContainer.getContainerISOcode()) ? null : EquipType.findEquipType(ediContainer.getContainerISOcode())
                    if (!(ediEqType != null && ediEqType.equals(ediCtr.getEqEquipType()))) {
                        registerWarning("ISO code '" + ediContainer.getContainerISOcode() + "' from EDI does not match with container " + ediCtr.getEqIdFull())
                    }
                    //Container Gross Wt Validation - Begin
                    if (ediGrossWt == 0D) {
                        registerError("Stowplan received for " + ediCtr.getEqIdFull() + " with invalid gross weight, cannot process EDI.")
                    } else if (ediCtr.getEqSafeWeightKg() != null && ediGrossWt > ediCtr.getEqSafeWeightKg()) {
                        registerWarning("Stowplan of " + ediCtr.getEqIdFull() + " has Gross Wt. '" + ediGrossWt + "' exceeds Safe Wt. " + ediCtr.getEqSafeWeightKg() + ".")
                    }
                    //Container Gross Wt Validation - End
                    ScopedBizUnit ctrOperator = ediCtr.getEquipmentOwner()
                    LOGGER.debug("ITSBapliePostInterceptor - ctrOperator: " + ctrOperator)
                    if (!ediLineOp.equals(ctrOperator)) {

                        EquipmentState eqState = EquipmentState.findOrCreateEquipmentState(ediCtr, ContextHelper.threadOperator)
                        LOGGER.debug("ITSBapliePostInterceptor - eqState: " + eqState)
                        eqState.upgradeEquipmentOwner(ediLineOp, DataSourceEnum.EDI_STOW)
                        eqState.upgradeEquipmentOperator(ediLineOp, DataSourceEnum.EDI_STOW)
                    }


                }

            }
        } finally {
            //inParams.put(EdiConsts.SKIP_POSTER, Boolean.TRUE);
        }
        LOGGER.debug("ITSBapliePostInterceptor - Execution completed.");
    }


    private void registerWarning(String inWarningMessage) {
        RoadBizUtil.messageCollector.appendMessage(MessageLevel.WARNING, AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, inWarningMessage)
    }

    private static final String NON_VISIT = "YES"
    private static final String CTR_STATUS_MTY = "MTY"
    private static final String REF_TYPE = "BL"


}
