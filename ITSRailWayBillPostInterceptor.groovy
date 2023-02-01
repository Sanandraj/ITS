

/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.ContextHelper
import com.navis.argo.EdiCommodity
import com.navis.argo.EdiContainer
import com.navis.argo.EdiVesselVisit
import com.navis.argo.Port
import com.navis.argo.RailRoad
import com.navis.argo.RailWayBillTransactionDocument
import com.navis.argo.RailWayBillTransactionsDocument
import com.navis.argo.ShippingLine
import com.navis.argo.business.api.VesselVisitFinder
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.atoms.WiMoveKindEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Complex
import com.navis.argo.business.reference.CarrierItinerary
import com.navis.argo.business.reference.CarrierService
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.LineOperator
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.edi.business.util.StringUtil
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.business.Roastery
import com.navis.framework.util.BizViolation
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.rail.business.entity.Railroad
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.road.business.atoms.TruckVisitStatusEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject
import org.jetbrains.annotations.Nullable

/*
 *
 * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 13/Jul/2022
 *
 * Requirements :
 *
 * # Visit Validation
 * V1-Container visit does not exist: To record an error if the requested container is not arriving not in yard
 * V3-Container is not Arriving nor In-Yard: To record an error if the requested container has an active unit
 * V4-Container is routed to Vessel (Depart Carrier Type = VESSEL): To record an error if the requested container has OB Carrier updated with Vessel
 * V5-Container is already LOAD-PLANNED: To record an error if the requested container already planned for a rail load
 * V6-Container Status is EMPTY or ROB: To record an error if the requested container freight Kind is MTY or Through category
 * V7-Container Arrival Carrier does not match (if EDI Arrival Vessel/Voyage NOT= Blank)  : To record an error if the requested container has a different Vessel Visit than the one available in EDI
 * V8-Container is already routed to another Train: To record an WARNING if the requested container already routed against a Train Visit
 * V9-Container is Departing (In-Transaction Status = D): To record an error if the requested container is used against a deliver gate transaction
 * VA-Container is in Service Hold: To record an WARNING if the requested container has any active holds
 *
 * # Delivery Order Validation (Container Status = IMPORT only)
 * I1-Delivery Order is already entered (Any B/L-Container is assigned Trucker Code): To record an error if the requested container is updated with a bonded trucking company
 *
 * @Inclusion Location	: Incorporated as a code extension of the type EDI_POST_INTERCEPTOR.
 *
 *  Load Code Extension to N4:
        1. Go to Administration --> System --> Code Extensions
        2. Click Add (+)
        3. Enter the values as below:
            Code Extension Name:  ITSRailWayBillPostInterceptor
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
 *
 */

class ITSRailWayBillPostInterceptor extends AbstractEdiPostInterceptor {
    private static Logger LOGGER = Logger.getLogger(ITSRailWayBillPostInterceptor.class);

    @Override
    public void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
      //  LOGGER.setLevel(Level.DEBUG);
        LOGGER.debug("ITSRailWayBillPostInterceptor (beforeEdiPost) - - Execution started.");
        if (RailWayBillTransactionsDocument.class.isAssignableFrom(inXmlTransactionDocument.getClass())) {
            RailWayBillTransactionsDocument railWayBillDocument = (RailWayBillTransactionsDocument) inXmlTransactionDocument;
            RailWayBillTransactionsDocument.RailWayBillTransactions railWayBillTrans = railWayBillDocument != null ? railWayBillDocument.getRailWayBillTransactions() : null;
            List<RailWayBillTransactionDocument.RailWayBillTransaction> railWayBillTransList = railWayBillTrans != null ? railWayBillTrans.getRailWayBillTransactionList() : null;

            RailWayBillTransactionDocument.RailWayBillTransaction railWayBillTransaction = railWayBillTransList != null && railWayBillTransList.size() > 0 ? railWayBillTransList.get(0) : null
            if (railWayBillTransaction == null) {
                registerError("No transaction data available, cannot process EDI.")
                return
            }
            EdiCommodity ediCommodity = railWayBillTransaction.getEdiRailWayBillContainer() != null ? railWayBillTransaction.getEdiRailWayBillContainer().getEdiCommodity() : null
            Port originPort = ediCommodity != null ? ediCommodity.getOriginPort() : null
            if (originPort != null && originPort.getPortId() != null && !FACILITY_PORT_CODE.equalsIgnoreCase(originPort.getPortId())) {
                Port destinationPort = ediCommodity != null ? ediCommodity.getDestinationPort() : null
                if(destinationPort != null && destinationPort.getPortId() != null && FACILITY_PORT_CODE.equalsIgnoreCase(destinationPort.getPortId())){
                    registerError("EDI data does not match the outbound rail move criteria, cannot process EDI.")
                }
                return
            }

            RailWayBillTransactionDocument.RailWayBillTransaction.EdiRailWayBillContainer ediRailWayBillContainer = railWayBillTransaction.getEdiRailWayBillContainer();
            EdiContainer ediContainer = ediRailWayBillContainer != null ? ediRailWayBillContainer.getEdiContainer() : null
            if (ediContainer == null) {
                return
            }
            String ediCtrNbr = ediContainer.getContainerNbr()
            Equipment ctrEquipment = ediCtrNbr != null ? Equipment.findEquipment(ediContainer.getContainerNbr()) : null
            if (ctrEquipment == null) {
                registerError("No container visit found for " + ediCtrNbr + ", cannot process EDI.")
                return
            }
            UnitCategoryEnum unitCategoryEnum = ediContainer != null && ediContainer.getContainerCategory() != null ? UnitCategoryEnum.getEnum(ediContainer.getContainerCategory()) : UnitCategoryEnum.IMPORT
            UnitFacilityVisit unitUfv = unitCategoryEnum != null ? this.findUfvForEquipment(ContextHelper.getThreadComplex(), ctrEquipment, unitCategoryEnum):null
            EdiVesselVisit ediVesselVisit = railWayBillTransaction.getEdiVesselVisit()
            CarrierVisit ediCv = ediVesselVisit != null ? this.resolveCarrierVisit(ediVesselVisit) : null
            //Validation - V1
            if (unitUfv == null) {
                registerError("No active container visit found for " + ediCtrNbr + ", cannot process EDI.")
                return
            }
            Unit ctrUnit = unitUfv.getUfvUnit()

            if (unitUfv != null) {
                if (UnitCategoryEnum.THROUGH.equals(ctrUnit?.getUnitCategory())) {
                        registerError("Category of  " + ediCtrNbr + " is THROUGH, cannot process EDI.")
                        return
                }
                CarrierVisit unitObCv = unitUfv.getUfvObCv()
                //Validation - V3
                if (!UfvTransitStateEnum.S20_INBOUND.equals(unitUfv.getUfvTransitState()) && !UfvTransitStateEnum.S30_ECIN.equals(unitUfv.getUfvTransitState()) && !UfvTransitStateEnum.S40_YARD.equals(unitUfv.getUfvTransitState())) {
                    registerError("Requested container " + ediCtrNbr + " is not Arriving nor In-Yard, cannot process EDI.")
                }
                //Validation - V4

                else if (unitObCv != null && LocTypeEnum.VESSEL.equals(unitObCv.getCvCarrierMode())) {
                    registerError("Departure mode of " + ediCtrNbr + " is VESSEL, cannot process EDI.")
                    return
                }
                //Validation - V5
                else if (unitUfv.getUnitFacilityVisitHasPlannedMove()) {
                    if (unitUfv.getNextWorkInstruction() != null && WiMoveKindEnum.RailLoad.equals(unitUfv.getNextWorkInstruction().getWiMoveKind())) {
                        registerError("Requested container " + ediCtrNbr + " has an active Rail Load plan, cannot process EDI.")
                    }
                }
                //Validation - V6
                else if (FreightKindEnum.MTY.equals(ctrUnit.getUnitFreightKind())) {
                    registerError("Status of requested container " + ediCtrNbr + " is MTY, cannot process EDI.")
                    return
                }
                //Validation - V7
                else if (ctrUnit.getInboundCv() != null && LocTypeEnum.VESSEL.equals(ctrUnit.getInboundCv().getCvCarrierMode())) {
                    if (ediCv != null) {
                        if (!ediCv.equals(ctrUnit.getInboundCv())) {
                            registerError("Inbound Vessel Visit (" + ctrUnit.getInboundCv().getCvId() + ") of " + ctrUnit.getUnitId() + " does not match with EDI Vessel Visit " + ediCv.getCvId() + ", cannot process EDI.")
                            return
                        }
                    } else if (railWayBillTransaction.getEdiVesselVisit() != null){
                        registerError("Vessel Visit details (" + railWayBillTransaction.getEdiVesselVisit().getVesselName() + " | " + railWayBillTransaction.getEdiVesselVisit().getInVoyageNbr() + ") for " + ctrUnit.getUnitId() + " in EDI does not have a matching visit in N4, cannot process EDI.")
                        return
                    }
                }
                //Validation - I1
                if (UnitCategoryEnum.IMPORT.equals(ctrUnit.getUnitCategory()) && ctrUnit.getUnitRouting() != null && ctrUnit.getUnitRouting().getRtgTruckingCompany() != null) {
                    registerWarning("Trucker Code " + ctrUnit.getUnitRouting().getRtgTruckingCompany().getBzuId() + " is updated against " + ediCtrNbr + ".")
                }
                TruckVisitStatusEnum[] tvStatus = [TruckVisitStatusEnum.OK, TruckVisitStatusEnum.TROUBLE]
                TranStatusEnum[] tranStatus = [TranStatusEnum.OK, TranStatusEnum.TROUBLE, TranStatusEnum.INCOMPLETE,TranStatusEnum.RETURNING]
                TranSubTypeEnum[] tranSubTypeEnums = [TranSubTypeEnum.DI, TranSubTypeEnum.DE, TranSubTypeEnum.DM]
                TruckTransaction truckTransaction = TruckTransaction.findLatestTruckTransactionByCtrNbr(ediCtrNbr, tvStatus, tranStatus)
                //Validation - V9
                if (truckTransaction != null && tranSubTypeEnums.contains(truckTransaction.getTranSubType())) {
                    registerError("Deliver transaction is created for " + ediCtrNbr + ", cannot process EDI.")
                    return
                }

                //Validation - V8
                if (unitObCv != null && (LocTypeEnum.TRAIN.equals(unitObCv.getCvCarrierMode()) || LocTypeEnum.RAILCAR.equals(unitObCv.getCvCarrierMode())) && !BNSF_TRAIN.equalsIgnoreCase(unitObCv.getCvId()) && !UP_TRAIN.equalsIgnoreCase(unitObCv.getCvId())) {
                    registerWarning("Container " + ediCtrNbr + " is already routed to Train Visit " + unitObCv.getCvId())
                }
                //Validation - VA
                if (ctrUnit.getUnitImpedimentRail() != null) {
                    registerWarning("Container " + ediCtrNbr + " has holds that stops rail movement.")
                }
                //Validation - VB
                RailRoad railRoad = railWayBillTransaction.getEdiRailRoad()
                ScopedBizUnit railOp = railRoad != null ? Railroad.resolveScopedBizUnit(railRoad.getRailRoadCode(), railRoad.getRailRoadCodeAgency(), BizRoleEnum.RAILROAD) : null
                CarrierService carrierService = railOp != null && OPR_BNSF.equalsIgnoreCase(railOp.getBzuId()) ? CarrierService.findCarrierService(BNSF_SERVICE) : null
                if (carrierService == null) {
                    carrierService = railOp != null && OPR_UP.equalsIgnoreCase(railOp.getBzuId()) ? CarrierService.findCarrierService(UP_SERVICE) : null
                }
                if (carrierService != null && carrierService.getSrvcItinerary() != null) {
                    CarrierItinerary itinerary = carrierService.getSrvcItinerary()
                    if (itinerary != null) {
                        Port destination = ediRailWayBillContainer.getEdiCommodity() != null ? ediRailWayBillContainer.getEdiCommodity().getDestinationPort() : null
                        RoutingPoint destPort = destination != null ? RoutingPoint.resolveRoutingPointFromEncoding(destination.getPortIdConvention(), destination.getPortId()) : null
                        if (destPort != null && !itinerary.isPointInItinerary(destPort)) {
                            registerWarning("Destination (" + destPort.getPointId() + ") provided for " + ediCtrNbr + " is not available in Train Service " + carrierService.getSrvcId() + ".")
                        }
                    }
                }

            }


        }
        LOGGER.debug("ITSRailWayBillPostInterceptor (beforeEdiPost) - - Execution completed.");
    }

    private void registerWarning(String inWarningMessage) {
        RoadBizUtil.messageCollector.appendMessage(MessageLevel.WARNING, AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, inWarningMessage)
    }
    private UnitFacilityVisit findUfvForEquipment(Complex inComplex, Equipment inEquipment, UnitCategoryEnum inCategory) {
        UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID)
        Unit ctrUnit = unitFinder.findActiveUnit(inComplex, inEquipment)
        UnitFacilityVisit ufv = null
        if (ctrUnit != null){
            ufv = ctrUnit?.getUnitActiveUfvNowActive()
        }

        return ufv
    }

    /**
     * Resolve Vessel Visit based on the EDI Voyage details.
     * @param inEdiVv
     * @throws com.navis.framework.util.BizViolation
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
        }
        return carrierVisit
    }



    private static final BNSF_TRAIN = "TBA_BNSF_TRAIN"
    private static final UP_TRAIN = "TBA_UP_TRAIN"
    private static final BNSF_SERVICE = "BNSF"
    private static final UP_SERVICE = "UPRR"
    private static final GEN_SERVICE = "GEN_RR"
    private static final OPR_BNSF = "BNSF"
    private static final OPR_UP = "UP"
    private static final FACILITY_PORT_CODE = "LGB"

}