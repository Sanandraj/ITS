import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.orders.business.eqorders.Booking
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author <a href="mailto:skishore@weservetech.com">KISHORE KUMAR S</a>
 */

class ITSMassBookingRollSubmitFormCommand extends AbstractFormSubmissionCommand{
    private static Logger LOGGER = Logger.getLogger(ITSMassBookingRollSubmitFormCommand.class)
    @Override
    void doBeforeSubmit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSMassBookingRollSubmitFormCommand Starts ::")
        LOGGER.debug("inGkeys :: "+inGkeys)
        LOGGER.debug("inOutFieldChanges :: "+inOutFieldChanges)
        PersistenceTemplate persistenceTemplate = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext())
        persistenceTemplate.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                final Logger LOGGER = Logger.getLogger(ITSMassBookingRollSubmitFormCommand.class)
                List<String> bkLineOperator = new ArrayList<String>()
                List<Serializable> bkgVesselVisit = new ArrayList<String>()
                for (Serializable gKey : inGkeys as List<Serializable>){
                    LOGGER.debug("inside gKey If ")
                    Booking booking = Booking.hydrate(gKey)
                    LOGGER.debug("booking :: "+booking)
                    String bkgLineOp = booking.getEqoLine().getBzuId()
                    LOGGER.debug("bkgLineOp ::"+bkgLineOp)
                    bkLineOperator.add(bkgLineOp)
                    String bkgVvId = booking.getEqoVesselVisit().getCvGkey()
                    LOGGER.debug("bkgVvId :: "+bkgVvId)
                    bkgVesselVisit.add(bkgVvId)
                }
                LOGGER.debug("bkLineOperator ::"+bkLineOperator)
                LOGGER.debug("bkgVesselVisit ::"+bkgVesselVisit)
                if (bkLineOperator !=null && bkLineOperator.size()>0 && bkgVesselVisit != null && bkgVesselVisit.size()>0){
                    boolean allVvEqual = bkgVesselVisit.stream().distinct().limit(2).count() <= 1
                    boolean allLineOpEqual = bkLineOperator.stream().distinct().limit(2).count() <= 1
                    boolean errorValue = false

                    if (allVvEqual){
                        LOGGER.debug("inside allVv Equal If ")
                        for (Serializable cvGkey: bkgVesselVisit){
                            CarrierVisit cvD= CarrierVisit.hydrate(cvGkey)
                            VesselVisitDetails visitDetails = VesselVisitDetails.resolveVvdFromCv(cvD)
                            LOGGER.debug("vvd :: "+visitDetails)
                            for (String lineOp :bkLineOperator ){
                                LOGGER.debug("lineOp :: "+lineOp)
                                if (visitDetails.isLineAllowed(ScopedBizUnit.findEquipmentOperator(lineOp))){
                                    LOGGER.debug("Vessel Visit is shared by Line Operator")
                                    LOGGER.debug("vvD Cut Off time :: "+visitDetails.getVvdTimeCargoCutoff())
                                    if ((visitDetails.getVvdTimeCargoCutoff()).after(ArgoUtils.timeNow())){
                                        for (Serializable gKey : inGkeys as List<Serializable>){
                                            Booking booking = Booking.hydrate(gKey)
                                            List<Unit> eqboUnitsNbr = getFinder().findUnitsForOrder(booking)
                                            LOGGER.debug("eqboUnitsNbr :: "+eqboUnitsNbr)
                                            if (eqboUnitsNbr!=null && eqboUnitsNbr.size()>0){
                                                LOGGER.debug("Inside Unit List not null")
                                                for (Unit unit: eqboUnitsNbr){
                                                    LOGGER.debug("Inside unit Iteration :: "+unit)
                                                    for (UnitFacilityVisit ufv : (unit.getUnitUfvSet() as List<UnitFacilityVisit>)){
                                                        String ufvTransitState= ufv.getUfvTransitState()
                                                        LOGGER.debug("ufvTransitState :: "+ufvTransitState)
                                                        if (allLineOpEqual){
                                                            LOGGER.debug("Inside all Line Operator equal")
                                                            if ((ufv.getUfvTransitState()).equals(UfvTransitStateEnum.S10_ADVISED)){
                                                                errorValue = false
                                                                LOGGER.debug("Successfully Rolled")
                                                            }
                                                            else if (!(ufv.getUfvTransitState()).equals(UfvTransitStateEnum.S10_ADVISED) &&
                                                                    ufv.isTransitStateBeyond(UfvTransitStateEnum.S20_INBOUND) &&
                                                                    ufv.isTransitStateAtMost(UfvTransitStateEnum.S40_YARD))
                                                            {
                                                                errorValue = false
                                                                LOGGER.debug("Cannot perform booking Roll also no error message")
                                                            }
                                                            else if (!(ufv.getUfvTransitState()).equals(UfvTransitStateEnum.S10_ADVISED) &&
                                                                    ufv.isTransitStateBeyond(UfvTransitStateEnum.S10_ADVISED)){
                                                                errorValue = false
                                                                LOGGER.debug("Successfully Rolled")
                                                            }
                                                        }
                                                        /**Same vessel visit - different line operator */
                                                        else {
                                                            errorValue = true
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                        /** Cut-off time has crossed*/
                                    else {
                                        errorValue = true
                                    }
                                }
                            }
                        }
                    }
                    /** different Vessel visit - same and different line operator*/
                    else {
                        errorValue = true
                    }
                    if (errorValue){
                        registerError("Error : Cannot perform Booking roll for selected bookings")
                        LOGGER.debug("Do not Roll Booking")
                    }
                }
            }
        })
    }
    private static UnitFinder getFinder() {
        return (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID)
    }
}