import com.navis.argo.*
import com.navis.argo.business.api.ArgoEdiFacade
import com.navis.argo.business.api.ArgoEdiUtils
import com.navis.argo.business.api.VesselVisitFinder
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Complex
import com.navis.argo.business.model.EdiPostingContext
import com.navis.argo.business.model.Facility
import com.navis.argo.business.reference.LineOperator
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.edi.business.edimodel.EdiConsts
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizViolation
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.orders.business.eqorders.Booking
import com.navis.road.business.util.RoadBizUtil
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.vessel.business.schedule.VesselVisitLine
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject

/**
 * @Copyright 2022 - Code written by WeServe LLC
 * @Author .
 *
 * Requirements :
 * #1: To check whether the Visit phase of EDI Vessel Visit is closed.
 * #2: To validate the Unit Inbound/ Booking Outbound Vessel visit
 * #3: To validate the Unit Category and container availability
 * #4:
 *
 *
 * @Inclusion Location	: Incorporated as a code extension of the type EDI_POST_INTERCEPTOR.
 * Load Code Extension to N4:
 1. Go to Administration --> System --> Code Extensions
 2. Click Add (+)
 3. Enter the values as below:
 Code Extension Name:  ITSTMSReleasePostInterceptor
 Code Extension Type:  EDI_POST_INTERCEPTOR
 Groovy Code: Copy and paste the contents of groovy code.
 4. Click Save button

 * Attach code extension to EDI session:
 1. Go to Administration-->EDI-->EDI configuration
 2. Select the EDI session and right click on it
 3. Click on Edit
 4. Select the extension in "Post Code Extension" tab
 5. Click on save
 *
 *
 */

class ITSTMSReleasePostInterceptor extends AbstractEdiPostInterceptor {
    private static final Logger LOGGER = Logger.getLogger(ITSTMSReleasePostInterceptor.class)

    void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        //LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSTMSReleasePostInterceptor - beforeEdiPost - Execution started.")
        if (inXmlTransactionDocument == null || !ReleaseTransactionsDocument.class.isAssignableFrom(inXmlTransactionDocument.getClass())) {
            return
        }

        ReleaseTransactionsDocument relTransDoc = (ReleaseTransactionsDocument) inXmlTransactionDocument
        ReleaseTransactionsDocument.ReleaseTransactions releaseTrans = relTransDoc.getReleaseTransactions()
        List<ReleaseTransactionDocument.ReleaseTransaction> transactionList = releaseTrans.getReleaseTransactionList()

        if (transactionList == null) {
            registerError("Release Array is NULL in before EDI post method")
            return
        }

        if (transactionList != null && transactionList.size() == 0) {
            registerError("Release Array is NULL in before EDI post method")
            return
        }

        ReleaseTransactionDocument.ReleaseTransaction releaseTransaction = transactionList.get(0)
        if (releaseTransaction != null) {
            List<EdiReleaseIdentifier> releaseIdentifierList = releaseTransaction.getEdiReleaseIdentifierList()
            EdiReleaseIdentifier releaseIdentifier = releaseIdentifierList != null ? releaseIdentifierList.get(0) : null
            String releaseId = releaseIdentifier != null ? releaseIdentifier.getReleaseIdentifierNbr() : null
            String releaseIdType = releaseTransaction.getReleaseIdentifierType()
            EdiVesselVisit ediVesselVisit = releaseTransaction.getEdiVesselVisit()
            CarrierVisit carrierVisit = ediVesselVisit != null ? findEdiVesselVisit(ediVesselVisit, releaseIdType) : null
            VesselVisitDetails vvd = carrierVisit != null ? VesselVisitDetails.resolveVvdFromCv(carrierVisit) : null
            if (vvd == null) {
                registerWarning("No matching vessel visit found [Vessel Id: " + ediVesselVisit.getVesselId() + " | Convention: "
                        + ediVesselVisit.getVesselIdConvention() + " | Vessel Name: " + ediVesselVisit.getVesselName() + " | IB Vyg: " + ediVesselVisit.getInOperatorVoyageNbr()
                        + " | OB Vyg: " + ediVesselVisit.getOutOperatorVoyageNbr() + "].")
            } else {
                if (CarrierVisitPhaseEnum.CLOSED.equals(vvd.getVvdVisitPhase())) {
                    registerWarning("EDI Vessel Visit " + carrierVisit.getCvId() + " is closed.")
                }
            }
            if (releaseId != null && releaseIdType != null && RELEASE_TYPE_UNIT.equalsIgnoreCase(releaseIdType)) {
                logMsg("Release Identifier: " + releaseId)
                String eqNbr = releaseIdentifier != null ? releaseIdentifier.getReleaseIdentifierNbr() : null
                UnitFacilityVisit ediUfv = eqNbr != null ? findUnitFacilityVisit(eqNbr) : null
                if (ediUfv == null) {
                    registerError("Requested container " + releaseId + " is 'NOT In-Yard' nor 'Arriving', cannot process EDI.")
                    inParams.put(EdiConsts.SKIP_POSTER, true)
                    return
                } else {
                    CarrierVisit ufvIbVessel = ediUfv.getUfvActualIbCv()
                    if (ufvIbVessel != null && LocTypeEnum.VESSEL.equals(ufvIbVessel.getCvCarrierMode()) && CarrierVisitPhaseEnum.CLOSED.equals(ufvIbVessel.getCvVisitPhase())) {
                        registerWarning("Requested container " + releaseId + " has closed inbound Vessel Visit " + ufvIbVessel.getCvId() + ".")
                    }
                    ShippingLine ediLine = ediVesselVisit != null ? ediVesselVisit.getShippingLine() : null
                    if (ediLine == null && ediUfv.getUfvUnit() != null && ediUfv.getUfvUnit().getUnitLineOperator() != null) {
                        ediLine = ediVesselVisit.addNewShippingLine()
                        ScopedBizUnit unitLine = ediUfv.getUfvUnit().getUnitLineOperator()
                        ediLine.setShippingLineCode(unitLine.getBzuScac())
                        ediLine.setShippingLineCodeAgency("SCAC")
                        ediVesselVisit.setShippingLine(ediLine)
                    }
                }


            } else if (releaseId != null && releaseIdType != null && RELEASE_TYPE_BKG.equalsIgnoreCase(releaseIdType)) {
                ShippingLine shpLine = releaseTransaction != null ? releaseTransaction.getEdiShippingLine() : null
                ScopedBizUnit scopedBizUnit = shpLine != null ? LineOperator.resolveScopedBizUnit(shpLine.getShippingLineCode(), shpLine.getShippingLineCodeAgency(), BizRoleEnum.LINEOP) : null
                LineOperator lineOperator = scopedBizUnit != null ? LineOperator.resolveLineOprFromScopedBizUnit(scopedBizUnit) : null
                Booking bkg = lineOperator != null ? Booking.findBookingWithoutVesselVisit(releaseId, lineOperator) : null
                logMsg("Booking : " + bkg)
                if (bkg == null) {
                    inParams.put(EdiConsts.SKIP_POSTER, true)
                    registerError("Requested booking " + releaseId + " not found, cannot process EDI.")
                    return
                } else if (bkg.getEqoVesselVisit() != null) {
                    String ediCvId = carrierVisit != null ? carrierVisit.getCvId() : null
                    CarrierVisit bkgObCarrierVisit = bkg.getEqoVesselVisit()
                    if (LocTypeEnum.VESSEL.equals(bkgObCarrierVisit.getCvCarrierMode()) && CarrierVisitPhaseEnum.CLOSED.equals(bkgObCarrierVisit.getCvVisitPhase())) {
                        registerWarning("Requested booking " + releaseId + " has closed Vessel Visit " + bkgObCarrierVisit.getCvId() + ".")
                    } else if (!ediCvId.equals(bkgObCarrierVisit?.getCvId())) {
                        VesselVisitDetails vesselVisitDetails = VesselVisitDetails.resolveVvdFromCv(bkgObCarrierVisit)
                        if (vesselVisitDetails != null && vesselVisitDetails.getVvlineForBizu(lineOperator) != null) {
                            String requestedVv = ediCvId == null ? "[" + ediVesselVisit.getVesselName() + " | " + ediVesselVisit.getOutOperatorVoyageNbr() + "]" : ediCvId
                            VesselVisitLine vesselVisitLine = vesselVisitDetails.getVvlineForBizu(lineOperator)
                            ediVesselVisit.setVesselId(vesselVisitDetails.getVvdVessel().vesLloydsId)
                            ediVesselVisit.setVesselIdConvention("LLOYDS")
                            ediVesselVisit.setVesselName(vesselVisitDetails.getVvdVessel().vesName)
                            ediVesselVisit.setInOperatorVoyageNbr(vesselVisitLine.getVvlineInVoyNbr())
                            ediVesselVisit.setOutOperatorVoyageNbr(vesselVisitLine.getVvlineOutVoyNbr())
                            registerWarning("Requested booking " + releaseId + " found with different Vessel Visit: " + bkgObCarrierVisit.getCvId() + ". Requested VV: " + requestedVv)
                        }

                    }
                }
            }
        }
        LOGGER.debug("ITSTMSReleasePostInterceptor - beforeEdiPost - Execution completed.")
    }


    private CarrierVisit findEdiVesselVisit(EdiVesselVisit inEdiVesselVisit, String inReleaseIdType) throws BizViolation {
        EdiPostingContext ediPostingContext = ContextHelper.threadEdiPostingContext
        Complex complex = ContextHelper.threadComplex
        Facility facility = ContextHelper.threadFacility

        if (inEdiVesselVisit != null && inReleaseIdType != null) {
            LOGGER.debug("ITSTMSReleasePostInterceptor - findEdiVesselVisit - inReleaseIdType: " + inReleaseIdType)
            LineOperator line = ArgoEdiUtils.getLineOperatorNullifyIfNotExist(inEdiVesselVisit)
            VesselVisitFinder vvFinder = (VesselVisitFinder) Roastery.getBean(VesselVisitFinder.BEAN_ID)
            List<CarrierVisit> carrierVisitList;
            if (RELEASE_TYPE_UNIT.equalsIgnoreCase(inReleaseIdType)) {
                carrierVisitList = vvFinder.findVesselVisitByIdAndVoyage(complex, facility, inEdiVesselVisit.getVesselIdConvention(),
                        inEdiVesselVisit.getVesselId(), inEdiVesselVisit.getInOperatorVoyageNbr(), line, true);
            } else if (RELEASE_TYPE_BKG.equalsIgnoreCase(inReleaseIdType)) {
                ArgoEdiFacade argoEdiFacade = (ArgoEdiFacade) Roastery.getBean(ArgoEdiFacade.BEAN_ID)
                CarrierVisit cv = inEdiVesselVisit != null ? argoEdiFacade.findVesselVisit(ediPostingContext, complex, facility, inEdiVesselVisit, line, false) : null;
                LOGGER.debug("ITSTMSReleasePostInterceptor - findEdiVesselVisit - Booking cv : " + cv)
                return cv
            }
            if (carrierVisitList?.size() > 0) {
                return carrierVisitList.get(0)
            }
        }
        return null
    }


    private void logMsg(String inMsg) {
        LOGGER.debug("ITSTMSReleasePostInterceptor : " + inMsg)
    }


    private UnitFacilityVisit findUnitFacilityVisit(String inUnitId) {
        String[] transitState = new String[3];
        transitState[0] = "S20_INBOUND";
        transitState[1] = "S30_ECIN";
        transitState[2] = "S40_YARD";
        if (inUnitId != null) {
            DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID, inUnitId))
                    .addDqPredicate(PredicateFactory.in(UnitField.UFV_TRANSIT_STATE, transitState));
            List<UnitFacilityVisit> ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            if (ufvList.size() > 0) {
                return (UnitFacilityVisit) ufvList.get(0)
            }
        }
        return null;
    }

    private void registerWarning(String inWarningMessage) {
        RoadBizUtil.messageCollector.appendMessage(MessageLevel.WARNING, AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, inWarningMessage)
    }

    private static final RELEASE_TYPE_UNIT = "UNITRELEASE"
    private static final RELEASE_TYPE_BKG = "BKGRELEASE"
}
