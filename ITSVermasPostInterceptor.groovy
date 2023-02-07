import com.navis.argo.*
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.edi.business.edimodel.EdiConsts
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.business.Roastery
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.orders.business.eqorders.Booking
import com.navis.road.business.util.RoadBizUtil
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.xmlbeans.XmlObject
import org.apache.log4j.Logger

/*
 *
 * @Author <a href="mailto:uaarthi@weservetech.com">Aarthi U</a>, 13/Jun/2022
 *
 * Requirements: To intercept and correct the checkdigit of the Container received
 *
 * @Inclusion Location	: Incorporated as a code extension of the type EDI_POST_INTERCEPTOR.
 *
 *  Load Code Extension to N4:
        1. Go to Administration --> System --> Code Extensions
        2. Click Add (+)
        3. Enter the values as below:
            Code Extension Name:  ITSVermasPostInterceptor
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
 *  S.No    Modified Date     Modified By     Jira      Description
 *  01.     2023-02-01        madhavan m      IP-370    modify the custom error message
 */

class ITSVermasPostInterceptor extends AbstractEdiPostInterceptor {
    @Override
    void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID)
        VermasTransactionsDocument transactionsDocument = (VermasTransactionsDocument) inXmlTransactionDocument;
        VermasTransactionsDocument.VermasTransactions vermasTransactions = transactionsDocument.getVermasTransactions();
        List<VermasTransactionDocument.VermasTransaction> transactionList = vermasTransactions.getVermasTransactionList();
        if (transactionList != null && (!transactionList.isEmpty()) && transactionList.get(0) != null) {
            VermasTransactionDocument.VermasTransaction vermasTransaction = transactionList.get(0)
            EdiContainer container = vermasTransaction.getEdiContainer()
            def library = getLibrary("ITSEdiUtil")
            library.checkDigit(container)
            // Validation of active unit - Begin
            if (container != null && container.getContainerNbr() != null) {
                Equipment ctrEquipment = Equipment.findEquipment(container.getContainerNbr())
                UnitCategoryEnum unitCategory = container.getContainerCategory() ? UnitCategoryEnum.getEnum(container.getContainerCategory()) : UnitCategoryEnum.EXPORT
                boolean hasError = Boolean.FALSE
                String ediBkgNbr = vermasTransaction.getEdiReference() != null && REF_TYPE_BKG.equalsIgnoreCase(vermasTransaction.getEdiReference().getReferenceType()) ? vermasTransaction.getEdiReference().getReferenceNbr() : null
                EdiOperator ediCtrOp = container.getContainerOperator()
                if (ediBkgNbr != null && ediCtrOp != null) {
                    ScopedBizUnit lineOp = ScopedBizUnit.resolveScopedBizUnit(ediCtrOp.getOperator(), ediCtrOp.getOperatorCodeAgency(), BizRoleEnum.LINEOP)
                    Booking inBkg = lineOp != null ? Booking.findBookingWithoutVesselVisit(ediBkgNbr, lineOp) : null
                    if (inBkg == null) {
                        registerError("Requested booking: " + ediBkgNbr + " not found to the line "+ ediCtrOp.getOperator() +", cannot process EDI.")
                        inParams.put(EdiConsts.SKIP_POSTER, true)
                    } else {
                        CarrierVisit bkgCv = inBkg.getEqoVesselVisit()
                        VesselVisitDetails bkgVv = bkgCv != null ? VesselVisitDetails.resolveVvdFromCv(bkgCv) : null
                        if (bkgVv != null) {
                            if (NON_VISIT.equalsIgnoreCase(bkgVv.getVvFlexString01())) {
                                hasError = Boolean.TRUE
                                registerError("Requested booking " + ediBkgNbr + " is created against non-visit vessel " + bkgCv.getCvId() + ", cannot process EDI.")
                                inParams.put(EdiConsts.SKIP_POSTER, true)
                            } else if (CarrierVisitPhaseEnum.COMPLETE.equals(bkgVv.getVvdVisitPhase())) {
                                hasError = Boolean.TRUE
                                registerError("Requested booking " + ediBkgNbr + " created for " + bkgCv.getCvId() + " with phase COMPLETE, cannot process EDI.")
                                inParams.put(EdiConsts.SKIP_POSTER, true)
                            }
                        }
                    }

                }

                Unit activeUnit = ctrEquipment != null ? unitFinder.findActiveUnit(ContextHelper.threadComplex, ctrEquipment) : null
                log(Level.DEBUG, "ITSVermasPostInterceptor - activeUnit: " + activeUnit)
                if (activeUnit != null && UnitCategoryEnum.EXPORT.equals(activeUnit.getUnitCategory())){
                    EdiVerifiedGrossMass ediVgm = container.getContainerVerifiedGrossMass()
                    if (ediVgm != null && !StringUtils.isEmpty(ediVgm.getVerifiedGrossWt())) {
                        if (ediVgm.getVerifiedGrossWtUnit() != null && (VGM_UNIT_KG.equalsIgnoreCase(ediVgm.getVerifiedGrossWtUnit()) || VGM_UNIT_LB.equalsIgnoreCase(ediVgm.getVerifiedGrossWtUnit()))) {
                            double ediVgmWt = ediVgmKg(ediVgm)
                            double ctrTare = ctrEquipment.getBestTareWeightKq()
                            double ctrSafe = ctrEquipment.getEqSafeWeightKg().equals(0D) ? ctrEquipment.getEqEquipType().getEqtypSafeWeightKg() : ctrEquipment.getEqSafeWeightKg()
                            if (ediVgm != null) {
                                if (ediVgmWt.equals(0D)) {
                                    hasError = Boolean.TRUE
                                    registerError("EDI received for " + container.getContainerNbr() + " with VGM '0', cannot process EDI.")
                                    inParams.put(EdiConsts.SKIP_POSTER, true)
                                } else if (ctrTare != null && ediVgmWt < ctrTare) {
                                    hasError = Boolean.TRUE
                                    registerError("EDI received with VGM (" + ediVgmWt + ") less than tare wt (" + ctrTare + ") of " + container.getContainerNbr() + ", cannot process EDI.")
                                    inParams.put(EdiConsts.SKIP_POSTER, true)
                                } else if (ctrSafe != null && ediVgmWt > ctrSafe && !hasError) {
                                    registerWarning("EDI received with VGM (" + ediVgmWt + ") exceeds safe wt (" + ctrSafe + ") of " + container.getContainerNbr() + ".")
                                }
                            }

                        }else {
                            registerError("Invalid VGM Wt unit  is defined for " + container.getContainerNbr() + ", cannot process EDI.")
                            inParams.put(EdiConsts.SKIP_POSTER, true)
                        }

                    } else {
                        registerError("No VGM weight is defined for " + container.getContainerNbr() + ", cannot process EDI.")
                        inParams.put(EdiConsts.SKIP_POSTER, true)
                    }

                } else {
                    registerError("No active export unit found for " + container.getContainerNbr() + ", cannot process EDI.")
                    inParams.put(EdiConsts.SKIP_POSTER, true)
                }



            }
            // Validation of active unit - End
        }
    }

    private void logMsg(String inMsg) {
        log(Level.DEBUG, "ITSVermasPostInterceptor - ")
    }


    public Double ediVgmKg(EdiVerifiedGrossMass inVgm) {
        log(Level.DEBUG,"Inside conversion")
        Double inWtDouble = Double.parseDouble(inVgm.getVerifiedGrossWt())
        if (inWtDouble != null && inVgm.getVerifiedGrossWtUnit() != null && VGM_UNIT_LB.equalsIgnoreCase(inVgm.getVerifiedGrossWtUnit())) {
            inWtDouble = Math.round(inWtDouble * 0.453592)
        }

        return inWtDouble;
    }

    private void registerWarning(String inWarningMessage) {
        RoadBizUtil.messageCollector.appendMessage(MessageLevel.WARNING, AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, inWarningMessage)
    }
    private static final String REF_TYPE_BKG = "BOOKING"
    private static final String NON_VISIT = "YES"
    private static final String VGM_UNIT_KG = "KG"
    private static final String VGM_UNIT_LB = "LB"
}