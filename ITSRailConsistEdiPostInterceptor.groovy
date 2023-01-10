import com.navis.argo.*
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Complex
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.reference.EquipType
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.edi.EdiEntity
import com.navis.edi.EdiField
import com.navis.edi.business.api.EdiFinder
import com.navis.edi.business.atoms.EdiFilterFieldIdEnum
import com.navis.edi.business.edimodel.EdiConsts
import com.navis.edi.business.entity.EdiBatch
import com.navis.edi.business.entity.EdiFilterEntry
import com.navis.edi.business.entity.EdiSession
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizViolation
import com.navis.framework.util.message.MessageCollectorUtils
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.rail.business.atoms.SpottingStatusEnum
import com.navis.rail.business.entity.Railcar
import com.navis.rail.business.entity.RailcarVisit
import com.navis.road.business.util.RoadBizUtil
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject

/*
 *
 * @author <a href="mailto:sanandaraj@weservetech.com">Anandaraj S</a>, 04/AUG/2022
 *
 * Requirements : This groovy is used for Railconsist EDI - Groovy validation required.
 * Spot and IB train visit validation are checking
 *
 * @Inclusion Location	: Incorporated as a code extension of the type EDI_POST_INTERCEPTOR.
 *
 *  Load Code Extension to N4:
        1. Go to Administration --> System --> Code Extensions
        2. Click Add (+)
        3. Enter the values as below:
            Code Extension Name:  ITSRailConsistEdiPostInterceptor
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

class ITSRailConsistEdiPostInterceptor extends AbstractEdiPostInterceptor {

    private static Logger LOGGER = Logger.getLogger(ITSRailConsistEdiPostInterceptor.class)

    private void logMsg(String inMsg) {
        LOGGER.warn(" ITSRailConsistEdiPostInterceptor :" + inMsg)
    }

    @Override
    void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSRailConsistEdiPostInterceptor - beforeEdiPost - Execution started.")
        if (!RailConsistTransactionsDocument.class.isAssignableFrom(inXmlTransactionDocument.getClass())) {
            registerError("Invalid transaction, cannot process EDI.")
            return
        }
        RailConsistTransactionsDocument railConsistTransactionsDocument = (RailConsistTransactionsDocument) inXmlTransactionDocument
        RailConsistTransactionsDocument.RailConsistTransactions railConsistTransactions = railConsistTransactionsDocument.getRailConsistTransactions()
        List<RailConsistTransactionDocument.RailConsistTransaction> railConsistTransactionsList = railConsistTransactions.getRailConsistTransactionList()
        if (railConsistTransactionsList.size() == 0) {
            registerError("No transaction found, cannot process EDI.")
            return
        }
        RailConsistTransactionDocument.RailConsistTransaction railConsistTransaction = railConsistTransactionsList.get(0)
        RailConsistTransactionDocument.RailConsistTransaction.EdiRailCarVisit ediRailCarVisit = railConsistTransaction.getEdiRailCarVisit()
        EdiRailCar ediRailCar = ediRailCarVisit != null ? ediRailCarVisit.getRailCar() : null
        String railCarId = ediRailCar != null ? ediRailCar.getRailCarId() : null
        if (railCarId == null) {
            registerError("No railcar id found for railwaybill transaction, cannot process EDI.")
            return
        }
        Railcar railCar = railCarId != null ? Railcar.findRailcar(railCarId) : null
        LOGGER.debug("ITSRailConsistEdiPostInterceptor - railCar : " + railCar)
        RailcarVisit railcarVisit = railCar != null ? RailcarVisit.findActiveRailCarVisit(railCar) : null
        LOGGER.debug("ITSRailConsistEdiPostInterceptor - railcarVisit : " + railcarVisit)
        Boolean hasRailcarError = Boolean.TRUE
        if (railcarVisit != null) {
            String rcarVisitIbTrainId = railcarVisit.getCarrierIbVoyNbrOrTrainId()
            LOGGER.debug("ITSRailConsistEdiPostInterceptor - Inbound Train : " + rcarVisitIbTrainId)
            LOGGER.debug("ITSRailConsistEdiPostInterceptor - Is Spotted : " + railcarVisit.isRailcarVisitSpotted())
            LOGGER.debug("ITSRailConsistEdiPostInterceptor - Spotting Status : " + railcarVisit.getRcarvSpottingStatus())
            LOGGER.debug("ITSRailConsistEdiPostInterceptor - Railcar Track : " + railcarVisit.getRcarvTrack())
            if (rcarVisitIbTrainId != null && !BNSF_TRAIN_VISIT.equalsIgnoreCase(rcarVisitIbTrainId) && !UP_TRAIN_VISIT.equalsIgnoreCase(rcarVisitIbTrainId)) {
                registerError("Railcar " + railCarId + " is associated with Train Visit " + rcarVisitIbTrainId + ", cannot process EDI.")
                inParams.put("SKIP_POSTER", Boolean.TRUE)
                return
            } else if (railcarVisit.getRcarvTrack() != null) {
                registerError("Railcar " + railCarId + " has active visit located against track '" + railcarVisit.getRcarvTrack() + "', cannot process EDI.")
                inParams.put("SKIP_POSTER", Boolean.TRUE)
                return
            } else if (railcarVisit.isRailcarVisitSpotted() || !SpottingStatusEnum.NOTSPOTTED.equals(railcarVisit.getRcarvSpottingStatus())) {
                String spottingStatus = railcarVisit.getRcarvSpottingStatus() != null ? railcarVisit.getRcarvSpottingStatus().getName() : "NA"
                registerError("Railcar " + railCarId + " has active visit spotted (Spot Status: " + spottingStatus + "), cannot process EDI.")
                inParams.put("SKIP_POSTER", Boolean.TRUE)
                return
            } else {
                hasRailcarError = Boolean.FALSE
            }
        }
        if (railcarVisit == null || !hasRailcarError) {
            List<RailConsistTransactionDocument.RailConsistTransaction.EdiRailCarContainer> railCarContainerList = railConsistTransaction.getEdiRailCarContainerList()
            if (railCarContainerList == 0) {
                LOGGER.debug("No railcar data")
                return
            }
            EdiBatch ediBatch = inParams.get(EdiConsts.BATCH_GKEY) != null ? EdiBatch.hydrate(inParams.get(EdiConsts.BATCH_GKEY)) : null
            EdiSession ediSession = ediBatch != null ? ediBatch.getEdibatchSession() : null
            EdiFinder ediFinder = (EdiFinder) Roastery.getBean(EdiFinder.BEAN_ID);
            List ediSessionFilterEntries = ediFinder.findEdiFilterEntriesBySession(ediSession);
            for (RailConsistTransactionDocument.RailConsistTransaction.EdiRailCarContainer ediRailCarContainer : railCarContainerList) {
                EdiContainer ediContainer = ediRailCarContainer.getEdiContainer()
                String ctrNbr = ediContainer != null ? ediContainer.getContainerNbr() : null
                UnitCategoryEnum ediCategory = ediContainer != null && ediContainer.getContainerCategory() != null ? UnitCategoryEnum.getEnum(ediContainer.getContainerCategory()) : UnitCategoryEnum.EXPORT
                LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - ediCategory: " + ediCategory)
                Equipment ctrEquipment = ctrNbr != null ? Equipment.findEquipment(ctrNbr) : null
                LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - ctrEquipment: " + ctrEquipment)
                UnitFacilityVisit ufv = ctrEquipment != null ? findUfvForEquipment(ContextHelper.getThreadComplex(), ctrEquipment, ediCategory) : null
                LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - ufv: " + ufv)
                Unit ufvUnit = ufv != null ? ufv.getUfvUnit() : null
                LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - ufvUnit: " + ufvUnit)
                UfvTransitStateEnum[] activeTstate = new UfvTransitStateEnum[3]
                //activeTstate[0] = UfvTransitStateEnum.S20_INBOUND
                activeTstate[1] = UfvTransitStateEnum.S30_ECIN
                activeTstate[2] = UfvTransitStateEnum.S40_YARD
                if (ufv != null && activeTstate.contains(ufv.getUfvTransitState())) {
                    registerError(ctrNbr + " is either arriving or in yard, cannot process EDI.")
                }
                if (StringUtils.isEmpty(ediContainer.getContainerGrossWt())) {
                    registerError("No gross weight available for " + ctrNbr + ", cannot process EDI.")
                } else {
                    Double eqSafeWt = ctrEquipment != null ? ctrEquipment.getEqSafeWeightKg() : 0D
                    LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - eqSafeWt: " + eqSafeWt)
                    Double grossWt = Double.parseDouble(ediContainer.getContainerGrossWt())
                    LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - grossWt: " + grossWt)
                    if (grossWt == 0D) {
                        registerError("Zero gross weight available for " + ctrNbr + ", cannot process EDI.")
                    } else if (ctrEquipment != null && grossWt > eqSafeWt) {
                        registerWarning("Container #" + ctrNbr + ": gross weight (" + grossWt + ") exceeds safe weight " + eqSafeWt)
                    }
                }
                EdiOperator ediOperator = ediContainer != null ? ediContainer.getContainerOperator() : null
                LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - ediOperator: " + ediOperator)
                if (ediOperator != null && ediOperator.getOperator().length() > 4) {
                    EdiFlexFields ediFlexFields = ediRailCarContainer != null ? ediRailCarContainer.getEdiFlexFields() : null
                    String flexFieldOperator = ediFlexFields != null ? ediFlexFields.getUnitFlexString01() : null
                    EdiFilterEntry filterEntry = findEdiFilterEntry(flexFieldOperator)
                    if (filterEntry != null && ediSessionFilterEntries.contains(filterEntry)) {
                        ediOperator.setOperator(filterEntry.getEdifltrenToValue())
                    }
                    ediFlexFields.setUnitFlexString01(null)
                    LOGGER.debug("Railconsist Transaction XML: " + railConsistTransaction)
                }
                ScopedBizUnit ediLineOp = ediOperator != null ? ScopedBizUnit.resolveScopedBizUnit(ediOperator.getOperator(), ediOperator.getOperatorCodeAgency(), BizRoleEnum.LINEOP) : null
                LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - ediLineOp: " + ediLineOp)
                String ediOrderNbr = ediRailCarContainer.getOrderNbr()
                LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - ediOrderNbr: " + ediOrderNbr)
                Booking ediBooking = ediOrderNbr != null && ediLineOp != null ? Booking.findBookingWithoutVesselVisit(ediOrderNbr, ediLineOp) : null
                LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - ediBooking: " + ediBooking)
                if (ediBooking == null) {
                    registerError("Could not find booking " + ediOrderNbr + " for " + ctrNbr + ", cannot process EDI.")
                } else {
                    if (ctrEquipment != null) {
                        EquipType ctrEqType = ctrEquipment.getEqEquipType()
                        LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - ctrEqType: " + ctrEqType)
                        try {
                            EquipmentOrderItem eqOrderItem = ctrEqType != null && !"UNKN".equalsIgnoreCase(ctrEqType.getEqtypId()) ? ediBooking.findMatchingItemReceive(ctrEqType, Boolean.FALSE, Boolean.FALSE, Boolean.TRUE) : null
                            LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - eqOrderItem.getEqoiTallyReceive(): " + eqOrderItem.getEqoiTallyReceive())
                            LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - eqOrderItem.getOrderItemReservedUnitsCount(): " + eqOrderItem.getOrderItemReservedUnitsCount())
                            LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - eqOrderItem.getEqoiQty(): " + eqOrderItem.getEqoiQty())

                            if (eqOrderItem == null) {
                                registerError("Type/size of " + ctrNbr + " does not match with booking item, cannot process EDI.")
                            } else if ((eqOrderItem.getEqoiTallyReceive() + eqOrderItem.getOrderItemDeliveredUnitsCount() + eqOrderItem.getOrderItemReservedUnitsCount().toLong()) == eqOrderItem.getEqoiQty()) {
                                registerError("Booking " + ediOrderNbr + " is full, cannot process EDI.")
                            }
                        }
                        catch (BizViolation bv) {
                            LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - bv: " + bv)
                            MessageCollectorUtils.getMessageCollector().appendMessage(MessageLevel.SEVERE, bv.getMessageKey(), "Booking: " + ediOrderNbr + "|Container: " + ctrNbr, null);
                            //registerError("Type/size of " + ctrNbr + " does not match with booking item, cannot process EDI.")
                        }
                    }
                    if (ediBooking.getEqoHasReefers()) {
                        registerWarning("Reefer setting is missing for " + ctrNbr + " reserved for reefer booking " + ediOrderNbr)
                    }
                    List<EdiHazard> ctrHazardList = ediRailCarContainer.getEdiHazardList()
                    if (ctrHazardList.size() == 0 && ediBooking.isHazardous()) {
                        registerWarning("Hazmat information is missing for " + ctrNbr + " reserved for reefer booking " + ediOrderNbr)
                    }
                    CarrierVisit bkgCv = ediBooking.getEqoVesselVisit()
                    if (bkgCv != null && CarrierVisitPhaseEnum.CLOSED.equals(bkgCv.getCvVisitPhase())) {
                        registerError("Booking " + ediOrderNbr + " is created against a closed vessel visit, cannot process EDI.")
                    }
                }
                LocPosition ufvRailcarLocPosition = ufvUnit != null && LocTypeEnum.RAILCAR.equals(ufvUnit.getLocType()) ? ufvUnit.findCurrentPosition() : null
                String ufvRailcarId = ufvRailcarLocPosition != null ? ufvRailcarLocPosition.getPosLocId() : null
                LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - ufvUnit: " + ufvUnit)
                LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - ufvRailcarId: " + ufvRailcarId)
                LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - EDI railCarId: " + railCarId)
                if (ufvRailcarId != null && !ufvRailcarId.equalsIgnoreCase(railCarId)) {
                    LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - ufvRailcarId: " + ufvRailcarId)
                    LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - EDI railCarId: " + railCarId)

                    Railcar unitRailcar = Railcar.findRailcar(ufvRailcarId)
                    LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - unitRailcar: " + unitRailcar)
                    RailcarVisit unitRailcarVisit = unitRailcar != null ? RailcarVisit.findActiveRailCarVisit(unitRailcar) : null
                    LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - unitRailcarVisit: " + unitRailcarVisit)
                    String unitRcarVisitIbTrainId = unitRailcarVisit != null ? unitRailcarVisit.getCarrierIbVoyNbrOrTrainId() : null
                    LOGGER.debug("******* ITSRailConsistEdiPostInterceptor - unitRcarVisitIbTrainId: " + unitRcarVisitIbTrainId)
                    if (unitRailcarVisit != null) {
                        LOGGER.debug("ITSRailConsistEdiPostInterceptor - Inbound Train : " + unitRcarVisitIbTrainId)
                        LOGGER.debug("ITSRailConsistEdiPostInterceptor - Is Spotted : " + unitRailcarVisit.isRailcarVisitSpotted())
                        LOGGER.debug("ITSRailConsistEdiPostInterceptor - Spotting Status : " + unitRailcarVisit.getRcarvSpottingStatus())
                        LOGGER.debug("ITSRailConsistEdiPostInterceptor - Railcar Track : " + unitRailcarVisit.getRcarvTrack())
                    }

                    if (unitRcarVisitIbTrainId != null && !BNSF_TRAIN_VISIT.equalsIgnoreCase(unitRcarVisitIbTrainId) && !UP_TRAIN_VISIT.equalsIgnoreCase(unitRcarVisitIbTrainId)) {
                        registerError(ctrNbr + " is updated against railcar " + railCarId + " with active Train Visit " + unitRcarVisitIbTrainId + ", cannot process EDI.")
                        inParams.put("SKIP_POSTER", Boolean.TRUE)
                        return
                    } else if (unitRailcarVisit != null){
                        if (unitRailcarVisit.getRcarvTrack() != null) {
                            registerError(railCarId + " assigned to " + ctrNbr + " has active visit located against track '" + unitRailcarVisit.getRcarvTrack() + "', cannot process EDI.")
                            inParams.put("SKIP_POSTER", Boolean.TRUE)
                            return
                        } else if (unitRailcarVisit.isRailcarVisitSpotted() || !SpottingStatusEnum.NOTSPOTTED.equals(unitRailcarVisit.getRcarvSpottingStatus())) {
                            String spottingStatus = unitRailcarVisit.getRcarvSpottingStatus() != null ? unitRailcarVisit.getRcarvSpottingStatus().getName() : "NA"
                            registerError(ctrNbr + " is updated with railcar " + railCarId + " has active visit spotted (Spot Status: " + spottingStatus + "), cannot process EDI.")
                            inParams.put("SKIP_POSTER", Boolean.TRUE)
                            return
                        }
                    }

                }

            }

        }

        LOGGER.debug("ITSRailConsistEdiPostInterceptor - beforeEdiPost - Execution completed.")
    }

    private void registerWarning(String inWarningMessage) {
        RoadBizUtil.messageCollector.appendMessage(MessageLevel.WARNING, AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, inWarningMessage)
    }

    private UnitFacilityVisit findUfvForEquipment(Complex inComplex, Equipment inEquipment, UnitCategoryEnum inCategory) {
        UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID)
        Unit ctrUnit = unitFinder.findActiveUnit(inComplex, inEquipment)
        UnitFacilityVisit ufv = null
        if (ctrUnit == null) {
            Collection<Unit> advisedUnits = unitFinder.findAdvisedUnitsUsingEq(inComplex, inEquipment, inCategory)
            if (advisedUnits.size() > 0) {
                ctrUnit = advisedUnits.getAt(0)
                ufv = ctrUnit != null ? ctrUnit.getUfvForFacilityNewest(ContextHelper.getThreadFacility()) : null
            }
        } else {
            ufv = ctrUnit.getUnitActiveUfvNowActive()
        }
        return ufv
    }

    private static EdiFilterEntry findEdiFilterEntry(String fromValue) {
        if (fromValue != null) {
            DomainQuery dq = QueryUtils.createDomainQuery(EdiEntity.EDI_FILTER_ENTRY);
            dq.addDqPredicate(PredicateFactory.eq(EdiField.EDIFLTREN_FROM_VALUE, fromValue))
            dq.addDqPredicate(PredicateFactory.eq(EdiField.EDIFLTREN_FIELD_ID, EdiFilterFieldIdEnum.operator));
            List<EdiFilterEntry> ediFilterEntryList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            if (ediFilterEntryList.size() > 0) {
                return ediFilterEntryList.get(0)
            }
        }
        return null;
    }

    private final static String BNSF_TRAIN_VISIT = "IB_BNSF_TRAIN"
    private final static String UP_TRAIN_VISIT = "IB_UP_TRAIN"
}
