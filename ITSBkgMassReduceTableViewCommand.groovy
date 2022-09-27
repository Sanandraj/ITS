import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.external.framework.ui.AbstractTableViewCommand
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.presentation.ui.message.ButtonTypes
import com.navis.framework.presentation.ui.message.MessageType
import com.navis.framework.presentation.ui.message.OptionDialog
import com.navis.orders.business.eqorders.Booking
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.inventory.business.units.EqBaseOrderItem
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger

class ITSBkgMassReduceTableViewCommand extends AbstractTableViewCommand{
    @Override
    void execute(EntityId inEntityId, List<Serializable> inGkeys, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSBkgMassReduceTableViewCommand Starts :: ")
        PersistenceTemplate pt = new PersistenceTemplate(getUserContext())
        pt.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                LOGGER.debug("Inside persistence Callback ")
                if (inGkeys != null && !inGkeys.isEmpty() && inGkeys.size()>1){
                    Iterator it = inGkeys.iterator()
                    long count =0
                    boolean error = false
                    boolean bkgReduce = false
                    while(it.hasNext()){
                        Booking booking =Booking.hydrate(it.next())
                        VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(booking.getEqoVesselVisit())
                        TimeZone timeZone = ContextHelper.getThreadUserTimezone()
                        if (vvd.getVvdTimeCargoCutoff()?.equals(ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone)) ||
                                vvd.getVvdTimeCargoCutoff()?.after(ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))){
                            if (booking!=null && booking.getEqboNbr()!=null){
                                if (booking.eqoTallyReceive > 0){
                                    LOGGER.debug("Tally Receive is equals to 0")
                                    Set bkgItems = booking.getEqboOrderItems()
                                    if (bkgItems != null && !bkgItems.isEmpty() && bkgItems.size() >= 1) {
                                        LOGGER.debug("Inside BkgItems If")
                                        Iterator iterator = bkgItems.iterator()
                                        LOGGER.debug("iterator :: "+iterator)
                                        while (iterator.hasNext()) {
                                            EquipmentOrderItem eqoItem = EquipmentOrderItem.resolveEqoiFromEqboi((EqBaseOrderItem) iterator.next())
                                            LOGGER.debug("eqoItem" + eqoItem)
                                            Long eqoiQty = eqoItem.getEqoiQty()
                                            Long eqoiTallyOut = eqoItem.getEqoiTally()
                                            Long eqoiTallyIn = eqoItem.getEqoiTallyReceive()
                                            LOGGER.debug("eqoiQty::" + eqoiQty)
                                            LOGGER.debug("eqoiTallyOut::" + eqoiTallyOut)
                                            LOGGER.debug("eqoiTallyIn::" + eqoiTallyIn)
                                            if (eqoiTallyIn > 0 || eqoiTallyOut > 0) {
                                                if (eqoiTallyIn >= eqoiTallyOut && eqoiTallyIn < eqoiQty) {
                                                    eqoItem.setEqoiQty(eqoiTallyIn)
                                                    count = count+1
                                                    bkgReduce = true
                                                }
                                                if (eqoiTallyOut >= eqoiTallyIn && eqoiTallyOut < eqoiQty) {
                                                    eqoItem.setEqoiQty(eqoiTallyOut)
                                                    count = count+1
                                                    bkgReduce = true
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else {
                            error = true
                            count = count+1
                        }
                    }
                    if (bkgReduce){
                        informationBox(count)
                    }
                    if (error){
                        OptionDialog.showError(PropertyKeyFactory.valueOf("Cannot Perform Mass Reduce for selected ${count} Bookings"),PropertyKeyFactory.valueOf("Error"))
                    }
                }
                else {
                    OptionDialog.showInformation(PropertyKeyFactory.valueOf("Selected bookings are null, or not more than one booking"),PropertyKeyFactory.valueOf("Booking Reduction"))
                }
            }
        })
    }
    private static final informationBox(long count){
        OptionDialog.showMessage(PropertyKeyFactory.valueOf("Vessel Cut-Offs Performance - ${count} Bookings Reduced"),PropertyKeyFactory.valueOf("Complete"), MessageType.INFORMATION_MESSAGE, ButtonTypes.OK,null)
    }
    private final static Logger LOGGER = Logger.getLogger(ITSBkgMassReduceTableViewCommand.class)
}
