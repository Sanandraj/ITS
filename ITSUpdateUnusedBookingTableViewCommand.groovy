import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.extension.portal.ExtensionBeanUtils
import com.navis.extension.portal.IExtensionTransactionHandler
import com.navis.external.framework.ui.AbstractTableViewCommand
import com.navis.framework.extension.FrameworkExtensionTypes
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.presentation.context.PresentationContextUtils
import com.navis.framework.presentation.context.RequestContext
import com.navis.framework.presentation.ui.event.listener.AbstractCarinaOptionCommand
import com.navis.framework.presentation.ui.message.ButtonType
import com.navis.framework.presentation.ui.message.ButtonTypes
import com.navis.framework.presentation.ui.message.MessageType
import com.navis.framework.presentation.ui.message.OptionDialog
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger


/**
 * Author: <a href="mailto:skishore@weservetech.com"> KISHORE KUMAR S </a>
 * Description: This Code will be paste against Table view Command Extension Type in Code extension - This Code will cancel/reduce bookings of the selected vessel visit
 * */

class ITSUpdateUnusedBookingTableViewCommand extends AbstractTableViewCommand {
    @Override
    void execute(EntityId inEntityId, List<Serializable> inGkeys, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSUpdateUnusedBookingTableViewCommand Starts :: ")
        PersistenceTemplate persistenceTemplate = new PersistenceTemplate(getUserContext())
        persistenceTemplate.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                LOGGER.debug("Inside persistence Callback ")
                if (inGkeys != null && !inGkeys.isEmpty() && inGkeys.size()==1){
                    for (Serializable vvKeys : inGkeys){
                        VesselVisitDetails vvd = VesselVisitDetails.hydrate(vvKeys)
                        LOGGER.debug("vvd :: "+vvd)
                        List<Booking> bookingList = getBookingDetails(vvd.getCvdCv().getCvId())
                        LOGGER.debug("bookingList :: "+bookingList)
                        TimeZone timeZone = ContextHelper.getThreadUserTimezone()
                        if (vvd.getVvdTimeCargoCutoff()?.equals(ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone)) ||
                                vvd.getVvdTimeCargoCutoff()?.before(ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))){
                            LOGGER.debug("Inside timezone")
                            OptionDialog.showQuestion(PropertyKeyFactory.keyWithFormat("Perform Vessel CutOff - Cancel or Reduce Booking ","Cancel and Reduce Booking"), PropertyKeyFactory.keyWithFormat("Perform Vessel CutOff","Cancel and Reduce Booking"), ButtonTypes.YES_NO_CANCEL, new AbstractCarinaOptionCommand() {
                                @Override
                                protected void safeExecute(ButtonType buttonTypes) {
                                    final Logger LOGGER = Logger.getLogger(ITSUpdateUnusedBookingTableViewCommand.class)
                                    if (ButtonType.YES == buttonTypes) {
                                        OptionDialog.showInformation(PropertyKeyFactory.keyWithFormat("Perform Cut-Offs","Perform Cut-Offs"),PropertyKeyFactory.keyWithFormat("Cancel Booking","Cancelling Booking"), ButtonTypes.YES_NO_CANCEL, new AbstractCarinaOptionCommand(){
                                            @Override
                                            protected void safeExecute(ButtonType buttonType) {
                                                if (ButtonType.YES == buttonType) {
                                                    LOGGER.debug("Inside Cancel Booking")
                                                    long count = 0
                                                    long add = 0
                                                    boolean bkgReduce = false
                                                    boolean bkgCancel = false
                                                    Iterator it = bookingList.iterator()
                                                    while(it.hasNext()){
                                                        EquipmentOrder booking = EquipmentOrder.resolveEqoFromEqbo(it.next())
                                                        boolean bkgNbrNull = true
                                                        if (booking!=null && booking.getEqboNbr()!=null ){
                                                            bkgNbrNull = false
                                                            if (booking.eqoTallyReceive == 0){
                                                                LOGGER.debug("Tally Receive is equals to 0")

                                                                // TODO why multiple PersistenceTemplate?
                                                                PersistenceTemplate pt = new PersistenceTemplate(getUserContext())
                                                                pt.invoke(new CarinaPersistenceCallback() {
                                                                    @Override
                                                                    protected void doInTransaction() {
                                                                        booking?.purge()
                                                                        count = count + 1
                                                                        bkgCancel = true
                                                                        LOGGER.debug("Deleted Bookings in Tally In")

                                                                    }
                                                                })
                                                            }
                                                            else {
                                                                RequestContext requestContext = PresentationContextUtils.getRequestContext();
                                                                UserContext userContext = requestContext.getUserContext();
                                                                Map input = new HashMap()
                                                                Map results = new HashMap()
                                                                input.put("entityGkey", EquipmentOrder)
                                                                input.put("bkgGkey", booking)
                                                                add = add + 1
                                                                bkgReduce = true
                                                                IExtensionTransactionHandler handler = ExtensionBeanUtils.getExtensionTransactionHandler()
                                                                handler?.executeInTransaction(userContext, FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, "ITSBkgValidationPersistenceCallback", input, results)
                                                            }
                                                        }
                                                        if (bkgNbrNull){
                                                            registerError("Booking Number unable to find")
                                                        }
                                                    }
                                                    //TODO move the whole section to a separate method -- and call that within the condition
                                                    updateEvent(vvd) // TODO why is the ITSVesselEventUpdatePersistenceCallBack callback code necessary?
                                                    if (bkgCancel || bkgReduce){
                                                        informationBox(count,add)
                                                    }
                                                }
                                            }
                                        })
                                    }
                                }
                            })
                        }
                        else if (vvd.getVvdTimeCargoCutoff()?.after(ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))){
                            /*OptionDialog.showError(PropertyKeyFactory.valueOf("Cannot perform Vessel Cut-offs for selected vessel Id - "+vvd.getVesselId()),PropertyKeyFactory.valueOf("Cannot Perform Vessel Cut-Offs"))*/
                            LOGGER.debug("Inside after timezone")
                            OptionDialog.showQuestion(PropertyKeyFactory.keyWithFormat("Perform Vessel CutOff - Cancel or Reduce Booking ","Cancel and Reduce Booking"), PropertyKeyFactory.keyWithFormat("Perform Vessel CutOff","Cancel and Reduce Booking"), ButtonTypes.YES_NO_CANCEL, new AbstractCarinaOptionCommand() {
                                @Override
                                protected void safeExecute(ButtonType buttonTypes) {
                                    final Logger LOGGER = Logger.getLogger(ITSUpdateUnusedBookingTableViewCommand.class)
                                    if (ButtonType.YES == buttonTypes) {
                                        OptionDialog.showWarning(PropertyKeyFactory.keyWithFormat("Perform Cut-Offs","Perform Cut-Offs"),PropertyKeyFactory.keyWithFormat("Cancel Booking","Cancelling Booking"), ButtonTypes.YES_NO_CANCEL, new AbstractCarinaOptionCommand(){
                                            @Override
                                            protected void safeExecute(ButtonType buttonType) {
                                                if (ButtonType.YES == buttonType) {
                                                    LOGGER.debug("Inside Cancel Booking")
                                                    long count =0
                                                    long add = 0
                                                    boolean bkgReduce = false
                                                    boolean bkgCancel = false
                                                    Iterator it = bookingList.iterator()
                                                    while(it.hasNext()){
                                                        EquipmentOrder booking = EquipmentOrder.resolveEqoFromEqbo(it.next())
                                                        if (booking!=null && booking.getEqboNbr()!=null ){
                                                            if (booking.eqoTallyReceive == 0){
                                                                LOGGER.debug("Tally Receive is equals to 0")
                                                                PersistenceTemplate pt = new PersistenceTemplate(getUserContext())
                                                                pt.invoke(new CarinaPersistenceCallback() {
                                                                    @Override
                                                                    protected void doInTransaction() {
                                                                        booking.purge()
                                                                        count = count+1
                                                                        bkgCancel = true
                                                                        LOGGER.debug("Deleted Bookings in Tally In")
                                                                    }
                                                                })
                                                            }
                                                            else {
                                                                RequestContext requestContext = PresentationContextUtils.getRequestContext();
                                                                UserContext userContext = requestContext.getUserContext();
                                                                Map input = new HashMap()
                                                                Map results = new HashMap()
                                                                input.put("entityGkey", EquipmentOrder)
                                                                input.put("bkgGkey", booking)
                                                                IExtensionTransactionHandler handler = ExtensionBeanUtils.getExtensionTransactionHandler()
                                                                handler?.executeInTransaction(userContext, FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, "ITSBkgValidationPersistenceCallback", input, results)
                                                                LOGGER.debug("Code Tally Out")
                                                                add = add+1
                                                                bkgReduce = true
                                                            }
                                                        }
                                                    }
                                                    updateEvent(vvd)
                                                    if (bkgCancel || bkgReduce){
                                                        informationBox(count,add)
                                                    }
                                                }
                                            }
                                        })
                                    }
                                }
                            })
                        }
                        else {
                            OptionDialog.showError(PropertyKeyFactory.valueOf("Null Value Dry cut-off"),PropertyKeyFactory.valueOf("Unable to perform"))
                        }
                    }
                }
                else {
                    OptionDialog.showError(PropertyKeyFactory.valueOf("Selected more than one vessel visit"),PropertyKeyFactory.valueOf("More than one visit Id"))
                }
            }
        })
    }
    private static List<Booking> getBookingDetails(String cvId){
        DomainQuery dq = QueryUtils.createDomainQuery("Booking")
        .addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("eqoVesselVisit.cvId"),cvId))
        return (HibernateApi.getInstance().findEntitiesByDomainQuery(dq))
    }
    private static final informationBox(long count, long add){
        OptionDialog.showMessage(PropertyKeyFactory.valueOf("Vessel Cut-Offs Performance - ${count} Bookings Cancelled and ${add} Bookings Reduced"),PropertyKeyFactory.valueOf("Complete"), MessageType.INFORMATION_MESSAGE,ButtonTypes.OK,null)
    }
    private static final updateEvent(VesselVisitDetails vvd){
        RequestContext requestContext = PresentationContextUtils.getRequestContext();
        UserContext userContext = requestContext.getUserContext();
        Map input = new HashMap()
        Map results = new HashMap()
        input.put("entityGkey", VesselVisitDetails)
        input.put("bkgGkey", vvd)
        IExtensionTransactionHandler handler = ExtensionBeanUtils.getExtensionTransactionHandler()
        handler?.executeInTransaction(userContext, FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, "ITSVesselEventUpdatePersistenceCallBack", input, results)
        LOGGER.debug("Code Tally Out")
    }
    private final static Logger LOGGER = Logger.getLogger(ITSUpdateUnusedBookingTableViewCommand.class)
}
