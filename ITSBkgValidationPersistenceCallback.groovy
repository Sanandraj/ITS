package ITSIntegration

import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.inventory.business.units.EqBaseOrderItem
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.orders.business.eqorders.Booking
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

/**
 * Author: <a href="mailto:skishore@weservetech.com"> KISHORE KUMAR S </a>
 * Description: This Code will be paste against Transacted Business function Extension Type in Code extension - This Code is call back for " ITSUpdateUnusedBookingTableViewCommand "
 * */

class ITSBkgValidationPersistenceCallback extends AbstractExtensionPersistenceCallback{
    @Override
    void execute(@Nullable Map input, @Nullable Map inOutResults) {

        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSBkgValidationPersistenceCallback starts :: ")
         Serializable bookingOrder = (Serializable) input?.get("entityGkey")
        Booking booking = Booking.hydrate(bookingOrder)
        LOGGER.debug("bookingOrder :: "+booking)
        if (booking.eqoTallyReceive > 0){
            LOGGER.debug("Inside tally-receive > 0 ")
            LOGGER.debug("bookingOrder "+booking)
            Set bkgItems = booking!= null ?  booking.getEqboOrderItems() : null;
            LOGGER.debug("bkgItems "+bkgItems)
            if (bkgItems != null && !bkgItems.isEmpty() && bkgItems.size() >= 1) {
                LOGGER.debug("Inside not null")
                Iterator iterator = bkgItems.iterator()
                LOGGER.debug("Iterator")
                while (iterator.hasNext()) {
                    EquipmentOrderItem eqoItem = EquipmentOrderItem.resolveEqoiFromEqboi((EqBaseOrderItem) iterator.next())
                    LOGGER.debug("EqoItem :: "+eqoItem)
                    Long eqoiQty = eqoItem.getEqoiQty()
                    LOGGER.debug("eqoiQty :: "+eqoiQty)
                    Long eqoiTallyOut = eqoItem.getEqoiTally()
                    LOGGER.debug("eqoiTallyOut :: "+eqoiTallyOut)
                    Long eqoiTallyIn = eqoItem.getEqoiTallyReceive()
                    LOGGER.debug("eqoiTallyIn :: "+eqoiTallyIn)
                    if (eqoiTallyIn > 0 || eqoiTallyOut > 0) {
                        if (eqoiTallyIn >= eqoiTallyOut && eqoiTallyIn < eqoiQty) {
                            LOGGER.debug("Inside In")
                            eqoItem.setEqoiQty(eqoiTallyIn)
                        }
                        if (eqoiTallyOut >= eqoiTallyIn && eqoiTallyOut < eqoiQty) {
                            LOGGER.debug("Inside Out")
                            eqoItem.setEqoiQty(eqoiTallyOut)
                        }
                    }
                }
            }
        }
        else if (booking.eqoTallyReceive == 0){
            LOGGER.debug("purged")
            booking.purge()
        }
    }
    private final static Logger LOGGER = Logger.getLogger(ITSBkgValidationPersistenceCallback.class)
}
