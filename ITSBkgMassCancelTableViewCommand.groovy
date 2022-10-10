import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.external.framework.ui.AbstractTableViewCommand
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.presentation.ui.message.ButtonTypes
import com.navis.framework.presentation.ui.message.MessageType
import com.navis.framework.presentation.ui.message.OptionDialog
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.orders.business.eqorders.Booking
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * Author: <a href="mailto:skishore@weservetech.com"> KISHORE KUMAR S </a>
 * Description: This Code will be paste against TTable View Command Extension Type in Code extension - This Code will cancel all selected bookings.
 * */

class ITSBkgMassCancelTableViewCommand extends AbstractTableViewCommand{
    @Override
    void execute(EntityId inEntityId, List<Serializable> inGkeys, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSBkgMassCancelTableViewCommand Starts :: ")
        PersistenceTemplate pt = new PersistenceTemplate(getUserContext())
        pt.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                if (inGkeys != null && !inGkeys.isEmpty() && inGkeys.size()>1){
                    Iterator it = inGkeys.iterator()
                    long count =0
                    long errorCount = 0
                    boolean error = false
                    boolean bkgCancel = false
                    while(it.hasNext()){
                        Booking booking = Booking.hydrate(it.next())
                        VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(booking.getEqoVesselVisit())
                        TimeZone timeZone = ContextHelper.getThreadUserTimezone()
                        if (vvd.getVvdTimeCargoCutoff()?.equals(ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone)) ||
                                vvd.getVvdTimeCargoCutoff()?.before(ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))){
                            if (booking!=null && booking.getEqboNbr()!=null ){
                                if (booking.eqoTallyReceive == 0){
                                    PersistenceTemplate template = new PersistenceTemplate(getUserContext())
                                    template.invoke(new CarinaPersistenceCallback() {
                                        @Override
                                        protected void doInTransaction() {
                                            booking.purge()
                                            count = count+1
                                            bkgCancel = true
                                        }
                                    })
                                }
                            }
                        }
                        else {
                            errorCount = errorCount+1
                            if (count == 0){
                                error = true
                            }
                        }
                    }
                    if (!bkgCancel){
                        informationBox(count,Long.valueOf(inGkeys.size()))
                    }
                    if (bkgCancel){
                        informationBox(count,errorCount)
                    }
                }
                else {
                    OptionDialog.showError(PropertyKeyFactory.valueOf("Selected bookings are null, or not more than one booking"),PropertyKeyFactory.valueOf("Mass Cancel bookings Error"))
                }
            }
        })
    }
    private static final informationBox(long count,long errorCount){
        OptionDialog.showMessage(PropertyKeyFactory.valueOf("Vessel Cut-Offs Performance - ${count}, Cutoff Passed for visits - ${errorCount}"),PropertyKeyFactory.valueOf("Information"), MessageType.INFORMATION_MESSAGE, ButtonTypes.OK,null)
    }
    private final static Logger LOGGER = Logger.getLogger(ITSBkgMassCancelTableViewCommand.class)
}
