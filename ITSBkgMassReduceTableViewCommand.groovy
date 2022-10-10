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

    /**
     * Author: <a href="mailto:skishore@weservetech.com"> KISHORE KUMAR S </a>
     * Description: This Code will be paste against TTable View Command Extension Type in Code extension - This Code will reduce all selected bookings.
     * */

    class ITSBkgMassReduceTableViewCommand extends AbstractTableViewCommand{
        @Override
        void execute(EntityId inEntityId, List<Serializable> inGkeys, Map<String, Object> inParams) {
            LOGGER.setLevel(Level.DEBUG)
            LOGGER.debug("ITSBkgMassReduceTableViewCommand Starts :: ")
            PersistenceTemplate pt = new PersistenceTemplate(getUserContext())
            pt.invoke(new CarinaPersistenceCallback() {
                @Override
                protected void doInTransaction() {
                    if (inGkeys != null && !inGkeys.isEmpty() && inGkeys.size()>1){
                        Iterator it = inGkeys.iterator()
                        long count =0
                        long errorCount = 0
                        boolean error = false
                        boolean bkgReduce = false
                        while(it.hasNext()){
                            Booking booking =Booking.hydrate(it.next())
                            VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(booking.getEqoVesselVisit())
                            TimeZone timeZone = ContextHelper.getThreadUserTimezone()
                            if (vvd.getVvdTimeCargoCutoff()?.equals(ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone)) ||
                                    vvd.getVvdTimeCargoCutoff()?.before(ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))){
                                if (booking!=null && booking.getEqboNbr()!=null){
                                    if (booking.eqoTallyReceive > 0){
                                        Set bkgItems = booking.getEqboOrderItems()
                                        if (bkgItems != null && !bkgItems.isEmpty() && bkgItems.size() >= 1) {
                                            Iterator iterator = bkgItems.iterator()
                                            while (iterator.hasNext()) {
                                                EquipmentOrderItem eqoItem = EquipmentOrderItem.resolveEqoiFromEqboi((EqBaseOrderItem) iterator.next())
                                                Long eqoiQty = eqoItem.getEqoiQty()
                                                Long eqoiTallyOut = eqoItem.getEqoiTally()
                                                Long eqoiTallyIn = eqoItem.getEqoiTallyReceive()
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
                                errorCount = errorCount+1
                                if (count == 0){
                                    error = true
                                }
                            }
                        }
                        if (!bkgReduce){
                            informationBox(count,Long.valueOf(inGkeys.size()))
                        }
                        if (bkgReduce ){
                            informationBox(count,errorCount)
                        }
                    }
                    else {
                        OptionDialog.showInformation(PropertyKeyFactory.valueOf("Selected bookings are null, or not more than one booking"),PropertyKeyFactory.valueOf("Booking Reduction"))
                    }
                }
            })
        }
        private static final informationBox(long count, long  errorCount){
            OptionDialog.showMessage(PropertyKeyFactory.valueOf("Vessel Cut-Offs Performance - ${count} bookings reduced, Vessel Cutoffs passed - ${errorCount} bookings"),PropertyKeyFactory.valueOf("Information"), MessageType.INFORMATION_MESSAGE, ButtonTypes.OK,null)
        }
        private final static Logger LOGGER = Logger.getLogger(ITSBkgMassReduceTableViewCommand.class)
    }
