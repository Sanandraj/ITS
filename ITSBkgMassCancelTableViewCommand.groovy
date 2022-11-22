/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.ContextHelper
import com.navis.external.framework.ui.AbstractTableViewCommand
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.presentation.ui.message.ButtonTypes
import com.navis.framework.presentation.ui.message.MessageType
import com.navis.framework.presentation.ui.message.OptionDialog
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.orders.business.eqorders.Booking
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author: Kishore Kumar S <a href= skishore@weservetech.com / >, 28/10/2022
 * Requirements : 5-2-Button to Cancel unused bookings after vessel cut-offs -- This groovy is used to cancel multiple booking selected from booking entity .
 * @Inclusion Location	: Incorporated as a code extension of the type TABLE_VIEW_COMMAND.
 *  Load Code Extension to N4:
 1. Go to Administration --> System -->  Code Extension
 2. Click Add (+)
 3. Enter the values as below:
 Code Extension Name:  ITSBkgMassCancelTableViewCommand.
 Code Extension Type:  TABLE_VIEW_COMMAND.
 Groovy Code: Copy and paste the contents of groovy code.
 4. Click Save button
 *
 *  Set up override configuration in variformId - ORD001.
 */


class ITSBkgMassCancelTableViewCommand extends AbstractTableViewCommand{
    @Override
    void execute(EntityId inEntityId, List<Serializable> inGkeys, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.INFO)
        LOGGER.info("ITSBkgMassCancelTableViewCommand Starts")
        if (inGkeys == null && inGkeys.isEmpty()){
            return;
        }
        PersistenceTemplate pt = new PersistenceTemplate(getUserContext())
        pt.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                if (inGkeys != null && !inGkeys.isEmpty() && inGkeys.size()>1){
                    List<Serializable> bookingGkeysDelete = new ArrayList<Serializable>()
                    long count =0
                    long errorCount = 0
                    boolean error = false
                    boolean bkgCancel = false
                    for(Serializable it: inGkeys){
                        Booking booking = Booking.hydrate(it)
                        if (booking == null){
                            return;
                        }
                        TimeZone timeZone = ContextHelper.getThreadUserTimezone()
                        if (timeZone != null && booking.getEqboNbr()!=null && booking.eqoTallyReceive == 0){
                            PersistenceTemplate template = new PersistenceTemplate(getUserContext())
                            template.invoke(new CarinaPersistenceCallback() {
                                @Override
                                protected void doInTransaction() {
                                    bookingGkeysDelete.add(booking.getPrimaryKey())
                                    count = count+1;
                                    bkgCancel = true;
                                }
                            })
                        }
                        else {
                            errorCount = errorCount+1;
                            if (count == 0){
                                error = true;
                            }
                        }
                    }
                    if (bookingGkeysDelete.size()>0){
                        deleteBookingsByGkeys(bookingGkeysDelete)
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
        OptionDialog.showMessage(PropertyKeyFactory.valueOf("Vessel Cut-offs (Performed count):      ${count} \nVessel Cut-offs (Not performed count):  ${errorCount}"),PropertyKeyFactory.valueOf("Information"), MessageType.INFORMATION_MESSAGE, ButtonTypes.OK,null)
    }
    private static final deleteBookingsByGkeys(List<Serializable> inGkeys){
        for(Serializable it:inGkeys){
            Booking booking = Booking.hydrate(it)
            booking.purge()
        }
    }
    private final static Logger LOGGER = Logger.getLogger(ITSBkgMassCancelTableViewCommand.class)
}
