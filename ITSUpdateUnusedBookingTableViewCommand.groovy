/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

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
import com.navis.services.business.rules.EventType
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger


/**
 * @Author: Kishore Kumar S <a href= skishore@weservetech.com / >, 28/10/2022
 * Requirements : 5-2-Button to Cancel unused bookings after vessel cut-offs -- This groovy is used to cancel multiple booking selected from vessel visit entity .
 * @Inclusion Location	: Incorporated as a code extension of the type TABLE_VIEW_COMMAND.
 *  Load Code Extension to N4:
 1. Go to Administration --> System -->  Code Extension
 2. Click Add (+)
 3. Enter the values as below:
 Code Extension Name:  ITSUpdateUnusedBookingTableViewCommand.
 Code Extension Type:  TABLE_VIEW_COMMAND.
 Groovy Code: Copy and paste the contents of groovy code.
 4. Click Save button
 *
 *  Set up override configuration in variformId - VSL005.
 */

class ITSUpdateUnusedBookingTableViewCommand extends AbstractTableViewCommand {
    @Override
    void execute(EntityId inEntityId, List<Serializable> inGkeys, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.INFO)
        LOGGER.info("ITSUpdateUnusedBookingTableViewCommand Starts : ")
        if (inGkeys == null && inGkeys.isEmpty()){
            return;
        }
        PersistenceTemplate persistenceTemplate = new PersistenceTemplate(getUserContext())
        persistenceTemplate.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                if (inGkeys != null && !inGkeys.isEmpty() && inGkeys.size()==1){
                    for (Serializable vvKeys : inGkeys){
                        VesselVisitDetails vvd = VesselVisitDetails.hydrate(vvKeys)
                        if (vvd==null){
                            return;
                        }
                        List<Booking> bookingList = getBookingDetails(vvd.getCvdCv().getCvId())
                        if (bookingList == null){
                            return;
                        }
                        TimeZone timeZone = ContextHelper.getThreadUserTimezone()
                        EventType event = EventType.findEventTypeProxy("TO_BE_DETERMINED")

                        if (vvd.getVvdTimeCargoCutoff().equals(ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone)) ||
                                vvd.getVvdTimeCargoCutoff().after(ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))){
                            OptionDialog.showQuestion(PropertyKeyFactory.keyWithFormat("Perform Vessel CutOff - Cancel or Reduce Booking ","Cancel and Reduce Booking"), PropertyKeyFactory.keyWithFormat("Perform Vessel CutOff","Cancel and Reduce Booking"), ButtonTypes.YES_NO, new AbstractCarinaOptionCommand() {
                                @Override
                                protected void safeExecute(ButtonType buttonTypes) {
                                    if (ButtonType.YES == buttonTypes) {
                                        OptionDialog.showInformation(PropertyKeyFactory.keyWithFormat("Perform Cut-Offs","Perform Cut-Offs"),PropertyKeyFactory.keyWithFormat("Vessel Cut-Off","Cancelling Booking"), ButtonTypes.YES_NO, new AbstractCarinaOptionCommand(){
                                            @Override
                                            protected void safeExecute(ButtonType buttonType) {
                                                if (ButtonType.YES == buttonType) {
                                                    vesselValidation(bookingList,vvd)

                                                }
                                            }
                                        })
                                    }
                                }
                            })
                        }
                        else if (vvd?.getVvdTimeCargoCutoff()?.after(ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))){
                            OptionDialog.showQuestion(PropertyKeyFactory.keyWithFormat("Perform Vessel CutOff - Cancel or Reduce Booking ","Cancel and Reduce Booking"), PropertyKeyFactory.keyWithFormat("Perform Vessel CutOff","Cancel and Reduce Booking"), ButtonTypes.YES_NO_CANCEL, new AbstractCarinaOptionCommand() {
                                @Override
                                protected void safeExecute(ButtonType buttonTypes) {
                                    if (ButtonType.YES == buttonTypes) {
                                        OptionDialog.showWarning(PropertyKeyFactory.keyWithFormat("Perform Cut-Offs","Perform Cut-Offs"),PropertyKeyFactory.keyWithFormat("Vessel Cut-Off","Cancelling Booking"), ButtonTypes.YES_NO_CANCEL, new AbstractCarinaOptionCommand(){
                                            @Override
                                            protected void safeExecute(ButtonType buttonType) {
                                                if (ButtonType.YES == buttonType) {
                                                    vesselValidation(bookingList,vvd)
                                                }
                                            }
                                        })
                                    }
                                }
                            })
                        }
                        else {
                            OptionDialog.showError(PropertyKeyFactory.valueOf("Dry cut-off not set."),PropertyKeyFactory.valueOf("Unable to perform"))
                        }
                        if (event!=null){
                            vvd.recordEvent(event,null,ContextHelper.getThreadUserId(), ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))
                        }
                    }
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
    private static final vesselValidation(List<Booking> bookingList, VesselVisitDetails vvd){
        long count = 0
        long add = 0
        boolean bkgReduce = false
        boolean bkgCancel = false
        Iterator it = bookingList.iterator()
        while(it.hasNext()){
            Booking booking = Booking.resolveEqoFromEqbo(it.next())
            if (booking==null){
                return ;
            }
            if (booking!=null && booking.getEqboNbr()!=null ){
                if (booking.eqoTallyReceive == 0){
                    RequestContext requestContext = PresentationContextUtils.getRequestContext()
                    UserContext userContext = requestContext.getUserContext();
                    Map input = new HashMap()
                    Map results = new HashMap()
                    input.put("entityGkey", booking?.getPrimaryKey())
                    bkgCancel = true
                    IExtensionTransactionHandler handler = ExtensionBeanUtils.getExtensionTransactionHandler()
                    handler?.executeInTransaction(userContext, FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, "ITSBkgValidationPersistenceCallback", input, results)
                    count = count + 1
                }
                else if (booking.eqoTallyReceive > 0){
                    RequestContext requestContext = PresentationContextUtils.getRequestContext()
                    UserContext userContext = requestContext.getUserContext();
                    Map input = new HashMap()
                    Map results = new HashMap()
                    input.put("entityGkey", booking?.getPrimaryKey())
                    bkgReduce = true
                    IExtensionTransactionHandler handler = ExtensionBeanUtils.getExtensionTransactionHandler()
                    handler?.executeInTransaction(userContext, FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, "ITSBkgValidationPersistenceCallback", input, results)
                    add = add + 1
                }
            }
        }
        if (bkgCancel || bkgReduce){
            informationBox(count,add)
        }
        else if (!bkgCancel || !bkgReduce){
            informationBox(count,add)
        }
    }
    private final static Logger LOGGER = Logger.getLogger(ITSUpdateUnusedBookingTableViewCommand.class)
}
