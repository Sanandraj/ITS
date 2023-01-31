package ITS

/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.*
import com.navis.argo.BlTransactionsDocument.BlTransactions
import com.navis.argo.business.api.VesselVisitFinder
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.ExamEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Complex
import com.navis.argo.business.reference.LineOperator
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.cargo.CargoPropertyKeys
import com.navis.cargo.business.model.BillOfLading
import com.navis.cargo.business.model.BlGoodsBl
import com.navis.cargo.business.model.GoodsBl
import com.navis.edi.business.edimodel.EdiConsts
import com.navis.edi.business.util.StringUtil
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.business.Roastery
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.business.units.Unit
import com.navis.orders.OrdersPropertyKeys
import com.navis.road.business.util.RoadBizUtil
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject
import org.jetbrains.annotations.Nullable

/*
 *
 * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 10/March/2022
 *
 * Requirements :
 *
 * #Port Validation
 * P2-EDI B/L POL is NOT on I/B Vessel Service Port Rotation: To verify the availability of POL provided in EDI against the IB Vessel Service if the port is valid and record warning.
 * P3-EDI B/L POD is NOT on I/B Vessel Service Port Rotation: To verify the POD provided in EDI against the IB Vessel Service and reject EDI if fails.
 *
 * #B/L Package Count Validation
 * Q1-B/L Pakacge Count = ZERO: To verify whether Manifested Qty is provided in EDI
 * Q3-If B/L Commodity record exists; B/L Package Count NOT= ∑(Commodity Package Count): To validate the Manifested Qty against sum of all BL Item qty and record warning.
 *
 * #B/L Weight and Measure Validation
 * W1-B/L Weight is ZERO: To verify whether BL Weight provided is zero or valid
 *
 * #B/L Container/Commodity Validation
 * M2-Neither Container record nor Commodity record exist.: To verify the availability of Continer and BL item details and reject if both are unavailable.
 * M3-B/L-Container is duplicate: To verify the availability of d8uplicate continer details and reject if available.
 *
 * #Additional validations
 * E1-BL deletion if any B/L-Container is associated with Container Visit: To verify the stowplan update against BL units and reject EDI if available
 * E2-BL deletion if any B/L-Container has Exam record: To verify the Exam Status against BL or associated units and reject EDI if available
 *
 * If any unit is associated to BL and EDI visit is different from BL visit record an error.
 *
 * @Inclusion Location	: Incorporated as a code extension of the type EDI_POST_INTERCEPTOR.
 *  Load Code Extension to N4:
        1. Go to Administration --> System --> Code Extensions
        2. Click Add (+)
        3. Enter the values as below:
            Code Extension Name:  ITSManifestPostInterceptor
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


class ITSManifestPostInterceptor extends AbstractEdiPostInterceptor {
    private static final Logger LOGGER = Logger.getLogger(ITSManifestPostInterceptor.class)

    @Override
    void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        LOGGER.setLevel(Level.DEBUG)
        logMsg("Execution started.")
        if (!BlTransactionsDocument.class.isAssignableFrom(inXmlTransactionDocument.getClass())) {
            throw BizFailure.create(OrdersPropertyKeys.ERRKEY__TYPE_MISMATCH_XMLBEAN, null, inXmlTransactionDocument.getClass().getName())
        }

        BlTransactionsDocument blDocument = (BlTransactionsDocument) inXmlTransactionDocument
        final BlTransactions blTransactions = blDocument.getBlTransactions()
        List<BlTransactionDocument.BlTransaction> blTranList = blTransactions.getBlTransactionList()
        if (blTranList.size() != 1) {
            throw BizFailure.create(CargoPropertyKeys.ERRKEY__XML_BL_TRANSACTION_DOCUMENT_LENGTH_EXCEED, null, String.valueOf(blTranList.size()))
        }
        BlTransactionDocument.BlTransaction blTransaction = blTranList.get(0)
        if (blTransaction == null) {
            logMsg("BL Transaction is null returning ")
            return
        }
        String msgFunction = blTransaction.getMsgFunction()
        EdiVesselVisit ediVesselVisit = blTransaction.getEdiVesselVisit()
        ShippingLine ediLine = ediVesselVisit != null ? ediVesselVisit.getShippingLine() : null
        ScopedBizUnit lineOp = ediLine != null ? ScopedBizUnit.resolveScopedBizUnit(ediLine.getShippingLineCode(), ediLine.getShippingLineCodeAgency(), BizRoleEnum.LINEOP) : null
        CarrierVisit ediCv = ediVesselVisit != null ? this.resolveCarrierVisit(ediVesselVisit) : null
        VesselVisitDetails vvd = ediCv != null && LocTypeEnum.VESSEL.equals(ediCv.getCvCarrierMode()) ?
                VesselVisitDetails.resolveVvdFromCv(ediCv) : null

        EdiBillOfLading ediBillOfLading = blTransaction.getEdiBillOfLading()
        if (!StringUtils.isEmpty(msgFunction) && MSG_FUNCTION_DELETE.equalsIgnoreCase(msgFunction)) {
            //#Additional validations
            BillOfLading ediBl
            if (ediBillOfLading != null && lineOp != null && ediCv != null) {
                ediBl = BillOfLading.findBillOfLading(ediBillOfLading.getBlNbr(), lineOp, ediCv)
            }
            if (ediBl == null) {
                return
            }
            //Validation E2 - BL
            if (ediBl.getExamStatus() != null && !ExamEnum.CANCEL.equals(ediBl.getExamStatus())) {
                registerError("EDI received to delete BL '" + ediBl.getNbr() + "' with Exam Status '" + ediBl.getExamStatus() + "', cannot process EDI.")
            } else if (ediBl.getBlBlGoodsBls() != null && ediBl.getBlBlGoodsBls().size() > 0) {
                logMsg("ediBl.getBlBlGoodsBls(): " + ediBl.getBlBlGoodsBls())
                Set<BlGoodsBl> blGoodsBlSet = ediBl.getBlBlGoodsBls()
                for (BlGoodsBl blGoodsBl : blGoodsBlSet) {
                    logMsg("blGoodsBl: " + blGoodsBl)
                    GoodsBl goodsBl = blGoodsBl != null ? blGoodsBl.getBlgdsblGoodsBl() : null
                    Unit gdsUnit = goodsBl != null ? goodsBl.getGdsUnit() : null
                    logMsg("gdsUnit: " + gdsUnit)
                    if (gdsUnit != null) {
                        //Validation E1
                        if (gdsUnit.getUnitIsStowplanPosted()) {
                            registerError("Baplie has posted against " + gdsUnit.getUnitId() + " associated with BL " + ediBl.getBlNbr() + ", cannot process EDI.")
                            return
                        }else if (gdsUnit.getUnitExam() != null && !ExamEnum.CANCEL.equals(gdsUnit.getUnitExam())) {
                            //Validation E2 - BL Units
                            registerError("Container " + gdsUnit.getUnitId() + " associated to BL " + ediBl.getNbr() + " has Exam Status " + gdsUnit.getUnitExam() + ", cannot process EDI.")
                            return
                        }
                    }
                }
            }
            return
        }
        if (ediBillOfLading != null) {

            List<BillOfLading> billOfLadings = BillOfLading.findAllBillsOfLading(ediBillOfLading?.getBlNbr())
            if (billOfLadings != null && !billOfLadings.isEmpty()){
                BillOfLading bl = billOfLadings.get(0)
                if (!ediCv.equals(bl?.getBlCarrierVisit())){
                    if (bl.getBlBlGoodsBls() != null && bl.getBlBlGoodsBls().size() > 0){
                        registerError("BL ${bl?.getBlNbr()} is available for vessel visit ${bl?.getCarrierVisitId()} with reserved units and does not match with EDI vessel visit ${ediCv?.getCvId()}, cannot process EDI.")
                        return
                    }
                }
            }
            if (ediBillOfLading.getManifestedQty() == null) {
                registerError("BL " + ediBillOfLading.getBlNbr() + " received without Manifested Qty, cannot process EDI.")
                return
            }
            BigInteger mftQty = ediBillOfLading.getManifestedQty()
            BigInteger itemQty = 0
            boolean hasContainers = Boolean.TRUE
            boolean hasBlCommodity = Boolean.TRUE
            //Validation Q1
            if (mftQty == 0) {
                registerError("BL " + ediBillOfLading.getBlNbr() + " received with Manifested Qty: 0, cannot process EDI.")
                return
            }
            List<BlTransactionDocument.BlTransaction.EdiBlItemHolder> itemHolderList = blTransaction.getEdiBlItemHolderList()
            if (itemHolderList == null || itemHolderList.size() == 0) {
                registerError("Neither container not commodity record exists for BL " + ediBillOfLading.getBlNbr())
            }
            List <String> blContainerNbrList = new ArrayList<String>()
            for (BlTransactionDocument.BlTransaction.EdiBlItemHolder itemHolder : itemHolderList) {
                if (itemHolder != null && itemHolder.getEdiBlEquipmentList().size() == 0 && itemHolder.getEdiBlItemList().size() == 0) {
                    registerError("Neither container not commodity record exists for BL " + ediBillOfLading.getBlNbr())
                }
                List<EdiBlEquipment> blEqList = itemHolder.getEdiBlEquipmentList()
                for (EdiBlEquipment blEq : blEqList) {
                    EdiContainer blContainer = blEq != null ? blEq.getEdiContainer() : null
                    if (blContainer == null) {
                        hasContainers = Boolean.FALSE
                    } else {
                        //Validation M3
                        String blContainerNbr = blContainer.getContainerNbr()
                        if (blContainerNbrList != null && blContainer != null && blContainerNbrList.contains(blContainerNbr)) {
                            registerError("Duplicate entries found for " + blContainerNbr + " against BL " + ediBillOfLading.getBlNbr() + ", cannot process EDI.")
                        }
                        blContainerNbrList.add(blContainerNbr)
                    }
                }
                List<EdiBlItem> blItemList = itemHolder.getEdiBlItemList()
                for (EdiBlItem blItem : blItemList) {
                    if (blItem == null) {
                        break
                    } else if (blItem.getEdiCommodity() == null) {
                        hasBlCommodity = Boolean.FALSE
                    }
                    itemQty += blItem.getQuantity() != null ? blItem.getQuantity().toBigInteger() : 0
                    //Validation W1
                    if (blItem.getWeight() == null) {
                        registerError("BL " + ediBillOfLading.getBlNbr() + " has BL Item(s) without weight, cannot process EDI. ")
                        inParams.put(EdiConsts.SKIP_POSTER, true)
                    } else if (Double.parseDouble(blItem.getWeight()) == 0D) {
                        registerError("BL " + ediBillOfLading.getBlNbr() + " has BL Item(s) with weight zero, cannot process EDI. ")
                    }
                }
            }

            //Validation Q3
            if (mftQty != itemQty) {
                registerWarning("BL " + ediBillOfLading.getBlNbr() + " received with Manifested Qty: " + mftQty + " that does not match sum of Bl Item quantities: " + itemQty)
            }
            //Validation M2
            if (!hasContainers && !hasBlCommodity) {
                registerError("Neither container not commodity record exists for BL " + ediBillOfLading.getBlNbr())
            }


        }

        if (vvd != null) {
            Port loadPort = blTransaction.getLoadPort()
            Port dischPort = blTransaction.getDischargePort1()
            RoutingPoint pol = loadPort != null ? RoutingPoint.resolveRoutingPointFromEncoding(loadPort.getPortIdConvention(), loadPort.getPortId()) : null
            RoutingPoint pod = dischPort != null ? RoutingPoint.resolveRoutingPointFromEncoding(dischPort.getPortIdConvention(), dischPort.getPortId()) : null
            if (pol != null && !ediCv.isPointInItinerary(pol)) {
                //Validation P2
                registerWarning("POL '" + pol.getPointId() + "' is unavailable in the port rotation of " + ediCv.getCvId())
            }
            //Validation P3
            if (pod != null && !ediCv.isPointInItinerary(pod)) {
                registerError("POD '" + pod.getPointId() + "' is unavailable in the port rotation of " + ediCv.getCvId() + ", cannot process EDI.")
            }

//            if (blTransaction.getEdiBlItemHolderList().size() != 0) {
//                List<BlTransactionDocument.BlTransaction.EdiBlItemHolder> blItemHolderList = blTransaction.getEdiBlItemHolderList()
//                for (BlTransactionDocument.BlTransaction.EdiBlItemHolder blItemHolder : blItemHolderList) {
//                    List<EdiBlItem> ediBlItemList = blItemHolder.getEdiBlItemList()
//                    for (EdiBlItem ediBlItem : ediBlItemList) {
//                        String blItemWeight = ediBlItem != null ? ediBlItem.getWeight() : null
//                        if (StringUtils.isEmpty(blItemWeight)) {
//                            registerError("BL item weight is unavailable, cannot process EDI.")
//                        }
//                        if (ediBlItem != null && ediBlItem.getEdiCommodity() == null) {
//                            registerError("No commodity details available, cannot process EDI.")
//                        }
//                    }
//                    List<EdiBlEquipment> blEquipmentList = blItemHolder.getEdiBlEquipmentList()
//                    for (EdiBlEquipment blEquipment : blEquipmentList) {
//                        if (blEquipment == null || (blEquipment != null && blEquipment.getEdiContainer() == null)) {
//                            registerError("No container details available, cannot process EDI.")
//                        }
//                    }
//                }
//            }
        }

        logMsg("Execution completed.")
    }

    private static logMsg(String inMsg) {
        LOGGER.debug("ITSManifestPostInterceptor - " + inMsg)
    }


    /**
     * Resolve Vessel Visit based on the EDI Voyage details.
     * @param inEdiVv
     * @throws BizViolation
     */
    private CarrierVisit resolveCarrierVisit(@Nullable EdiVesselVisit inEdiVv) throws BizViolation {
        Complex inComplex = ContextHelper.getThreadComplex()
        ShippingLine inLine = inEdiVv.getShippingLine()
        ScopedBizUnit lineOp = inLine != null ? LineOperator.resolveScopedBizUnit(inLine.getShippingLineCode(), inLine.getShippingLineCodeAgency(), BizRoleEnum.LINEOP) : null
        if (inComplex == null) {
            LOGGER.warn(" Thread Complex is Null")
        }
        if (lineOp == null) {
            return null
        }
        String vvConvention = inEdiVv != null ? inEdiVv.getVesselIdConvention() : null
        String vvId = StringUtil.isNotEmpty(vvConvention) ? inEdiVv.getVesselId() : null
        CarrierVisit carrierVisit = null
        if (StringUtil.isNotEmpty(vvId)) {
            VesselVisitFinder vvf = (VesselVisitFinder) Roastery.getBean(VesselVisitFinder.BEAN_ID)
            if ((inEdiVv.getInVoyageNbr()?.trim() != null) || (inEdiVv.getInOperatorVoyageNbr()?.trim() != null)) {
                if (null == carrierVisit && (inEdiVv.getInVoyageNbr()?.trim()) != null) {
                    try {
                        carrierVisit = vvf.findVesselVisitForInboundStow(inComplex, vvConvention, vvId, inEdiVv.getInVoyageNbr(), null, null)
                    } catch (BizViolation violation) {
                        LOGGER.error(violation)
                    }
                }
                if (null == carrierVisit && (inEdiVv.getInOperatorVoyageNbr()?.trim() != null)) {
                    try {
                        carrierVisit = vvf.findVesselVisitForInboundStow(inComplex, vvConvention, vvId, inEdiVv.getInOperatorVoyageNbr(), null, null)
                    } catch (BizViolation violation) {
                        LOGGER.error(violation)
                    }
                }
            } else if ((inEdiVv.getOutVoyageNbr()?.trim() != null) || (inEdiVv.getOutOperatorVoyageNbr()?.trim() != null)) {
                if (null == carrierVisit && (inEdiVv.getOutVoyageNbr()?.trim() != null)) {
                    try {
                        carrierVisit = vvf.findOutboundVesselVisit(inComplex, vvConvention, vvId, inEdiVv.getOutVoyageNbr(), LineOperator.resolveLineOprFromScopedBizUnit(lineOp), null)
                    } catch (BizViolation violation) {
                        LOGGER.error(violation)
                    }
                }
                if (null == carrierVisit && (inEdiVv.getOutOperatorVoyageNbr()?.trim() != null)) {
                    try {
                        carrierVisit = vvf.findOutboundVesselVisit(inComplex, vvConvention, vvId, inEdiVv.getOutOperatorVoyageNbr(), LineOperator.resolveLineOprFromScopedBizUnit(lineOp), null)
                    } catch (BizViolation violation) {
                        LOGGER.error(violation)
                    }
                }
            }
            //LOGGER.warn("cv::" + carrierVisit)
        }
        return carrierVisit
    }


    private void registerWarning(String inWarningMessage) {
        RoadBizUtil.messageCollector.appendMessage(MessageLevel.WARNING, AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, inWarningMessage)
    }

    private static final MSG_FUNCTION_DELETE = "DELETE"
}