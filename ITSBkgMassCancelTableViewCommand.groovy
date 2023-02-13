/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/


import com.navis.argo.ContextHelper
import com.navis.external.framework.ui.AbstractTableViewCommand
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.presentation.ui.message.ButtonTypes
import com.navis.framework.presentation.ui.message.MessageType
import com.navis.framework.presentation.ui.message.OptionDialog
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.orders.business.eqorders.Booking
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 *
 *
 * @Author: mailto:skishore@weservetech.com, Kishore Kumar S; Date: 28/10/2022
 *
 *  Requirements: 5-2-Button to Cancel unused bookings after vessel cut-offs -- This groovy is used to cancel multiple booking selected from booking entity.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSBkgMassCancelTableViewCommand
 *     Code Extension Type: TABLE_VIEW_COMMAND
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup override configuration in variformId - REV_ORD001
 *
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */


class ITSBkgMassCancelTableViewCommand extends AbstractTableViewCommand {
    @Override
    void execute(EntityId inEntityId, List<Serializable> inGkeys, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.INFO)
        LOGGER.info("ITSBkgMassCancelTableViewCommand Starts")
        if (inGkeys == null && inGkeys.isEmpty()) {
            return;
        }
        PersistenceTemplate pt = new PersistenceTemplate(getUserContext())
        pt.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                if (inGkeys != null && !inGkeys.isEmpty() && inGkeys.size() > 0) {
                    List<Serializable> bookingGkeysDelete = new ArrayList<Serializable>()
                    long count = 0
                    long errorCount = 0
                    for (Serializable it : inGkeys) {
                        Booking booking = Booking.hydrate(it)
                        if (booking == null) {
                            informationBox(count, errorCount)
                            return;
                        }
                        TimeZone timeZone = ContextHelper.getThreadUserTimezone()
                        if (timeZone != null && booking.getEqboNbr() != null && booking.getEqoTally() == 0) {
                            bookingGkeysDelete.add(booking.getPrimaryKey())
                        } else {
                            errorCount = errorCount + 1;
                        }
                    }
                    if (bookingGkeysDelete.size() > 0) {
                        for (Serializable it : inGkeys) {
                            try {
                                Booking booking = Booking.hydrate(it)
                                booking.purge()
                                count = count + 1;
                            } catch (Exception inEx) {
                                errorCount = errorCount + 1;
                            }
                        }
                    }
                    informationBox(count, errorCount)
                } else {
                    OptionDialog.showError(PropertyKeyFactory.valueOf("Selected bookings are null, or not more than one booking"), PropertyKeyFactory.valueOf("Mass Cancel bookings Error"))
                }
            }
        })
    }

    private static final informationBox(long count, long errorCount) {
        OptionDialog.showMessage(PropertyKeyFactory.valueOf("Bookings purged:      ${count} \nBooking Error count:  ${errorCount}"), PropertyKeyFactory.valueOf("Information"), MessageType.INFORMATION_MESSAGE, ButtonTypes.OK, null)
    }

    private static Logger LOGGER = Logger.getLogger(ITSBkgMassCancelTableViewCommand.class)
}