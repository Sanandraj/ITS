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
 * @Author: mailto: skishore@weservetech.com, Kishore Kumar S; Date: 28/10/2022
 *
 *  Requirements: 5-2-Button to Cancel unused bookings after vessel cut-offs -- This groovy is used to cancel multiple booking selected from vessel visit entity.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSUpdateUnusedBookingTableViewCommand
 *     Code Extension Type: TABLE_VIEW_COMMAND
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup override configuration in variformId - REV_VSL005.
 *
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 *
 *
 */

class ITSUpdateUnusedBookingTableViewCommand extends AbstractTableViewCommand {
    @Override
    void execute(EntityId inEntityId, List<Serializable> inGkeys, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.INFO)
        LOGGER.info("ITSUpdateUnusedBookingTableViewCommand Starts : ")
        if (inGkeys == null && inGkeys.isEmpty()) {
            return;
        }
        PersistenceTemplate persistenceTemplate = new PersistenceTemplate(getUserContext())
        persistenceTemplate.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                if (inGkeys != null && !inGkeys.isEmpty() && inGkeys.size() == 1) {
                    for (Serializable vvKeys : inGkeys) {
                        VesselVisitDetails vvd = VesselVisitDetails.hydrate(vvKeys)
                        if (vvd == null) {
                            return;
                        }
                        List<Booking> bookingList = getBookingDetails(vvd.getCvdCv().getCvId())
                        if (bookingList == null) {
                            return;
                        }
                        int count = 0
                        TimeZone timeZone = ContextHelper.getThreadUserTimezone()

                        if(vvd){
                            String msg ="Perform Vessel CutOff - Cancel or Reduce Booking "
                            if(vvd.getVvdTimeCargoCutoff() == null){
                                msg = "Perform Vessel CutOff - Dry cut-off is not set for the visit. Do you want to proceed with Cancel or Reduce Booking?"
                            }
                            if (vvd.getVvdTimeCargoCutoff() != null &&
                                    vvd.getVvdTimeCargoCutoff().before(ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))) {
                                msg = "Perform Vessel CutOff - Dry cut-off is passed. Do you want to proceed with Cancel or Reduce Booking? "
                            }
                            OptionDialog.showQuestion(PropertyKeyFactory.keyWithFormat(msg, "Cancel and Reduce Booking"), PropertyKeyFactory.keyWithFormat("Perform Vessel CutOff", "Cancel and Reduce Booking"), ButtonTypes.YES_NO, new AbstractCarinaOptionCommand() {
                                @Override
                                protected void safeExecute(ButtonType buttonTypes) {
                                    if (ButtonType.YES == buttonTypes) {
                                        vesselValidation(bookingList, vvd)
                                    }
                                }

                            })
                        }

                    }
                }
            }
        })
    }

    private static List<Booking> getBookingDetails(String cvId) {
        DomainQuery dq = QueryUtils.createDomainQuery("Booking")
                .addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("eqoVesselVisit.cvId"), cvId))
        return (HibernateApi.getInstance().findEntitiesByDomainQuery(dq))
    }

    private static final informationBox(long count, long add) {
        OptionDialog.showMessage(PropertyKeyFactory.valueOf("Vessel Cut-offs (Performed count):      ${count} \nVessel Cut-offs (Not performed count):  ${add}"), PropertyKeyFactory.valueOf("Complete"), MessageType.INFORMATION_MESSAGE, ButtonTypes.OK, null)
    }

    private void vesselValidation(List<Booking> bookingList, VesselVisitDetails vvd) {
        int count = 0
        long add = 0
        RequestContext requestContext = PresentationContextUtils.getRequestContext()
        UserContext userContext = requestContext.getUserContext();
        IExtensionTransactionHandler handler = ExtensionBeanUtils.getExtensionTransactionHandler()
        Iterator it = bookingList.iterator()
        while (it.hasNext()) {
            Booking booking = Booking.resolveEqoFromEqbo(it.next())
            if (booking == null) {
                return;
            }
            if (booking != null && booking.getEqboNbr() != null) {
                Map input = new HashMap()
                Map results = new HashMap()
                input.put("entityGkey", booking?.getPrimaryKey())
                if (booking.eqoTallyReceive >= 0) {
                    handler?.executeInTransaction(userContext, FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, "ITSBkgValidationPersistenceCallback", input, results)
                    if(results != null && results.get("reduced") != null){
                        String isReduced = (String) results.get("reduced")
                        if("YES".equalsIgnoreCase(isReduced)){
                            count = count + 1
                        }
                    }

                }
            }
        }


        if(count > 0){
            Map input = new HashMap()
            Map results = new HashMap()
            input.put("recordEvent", "YES")
            input.put("vesselVisit", vvd.getPrimaryKey())
            handler?.executeInTransaction(userContext, FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, "ITSBkgValidationPersistenceCallback", input, results)
        }
        informationBox(count, add)


    }
    private static Logger LOGGER = Logger.getLogger(ITSUpdateUnusedBookingTableViewCommand.class)
}