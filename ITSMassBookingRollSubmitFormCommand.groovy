import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.inventory.business.api.UnitFinder
import com.navis.orders.business.eqorders.Booking
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
                LOGGER.debug("Inside Persistence")
                String constantLineOpr= null
                String constantVvd = null
                int count = 0
                for (Serializable gKey : inGkeys as List<Serializable>){
                    count = count + 1
                    Booking booking = Booking.hydrate(gKey)
                    if (count==1){
                        constantLineOpr = booking.getEqoLine().getBzuId()
                    }
                    if (count>1){
                        String bkgCurrentLineOpr = booking.getEqoLine().getBzuId()
                        if (!bkgCurrentLineOpr.equals(constantLineOpr) ){
                            registerError("Error : Cannot perform Booking roll for selected bookings")
                            return
                        }
                    }
                    if (count==1){
                        constantVvd = booking.getEqoVesselVisit().getCvId()
                    }
                    if (count>1){
                        String bkgVvd = booking.getEqoVesselVisit().getCvId()
                        if (!bkgVvd.equals(constantVvd)){
                            registerError("Error : Cannot perform Booking roll for selected bookings")
                            return
                        }
                    }
                    Long bkgTallyReceive = booking.getEqoTallyReceive()
                    LOGGER.debug("bkgTallyReceive :: "+bkgTallyReceive)
                    if (bkgTallyReceive > 0 ){
                        registerError("Error : Cannot perform Booking roll for selected bookings")
                        return
                    }
                }
            }
        })
    }
    private static UnitFinder getFinder() {return (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID)}
}
