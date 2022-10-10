import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.inventory.business.units.EqBaseOrderItem
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.orders.business.eqorders.EquipmentOrderItem
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
        EquipmentOrder booking = (EquipmentOrder) input?.get("input")
        EquipmentOrder bookingOrder = (EquipmentOrder) input?.get("EquipmentOrder")
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
                            }
                            if (eqoiTallyOut >= eqoiTallyIn && eqoiTallyOut < eqoiQty) {
                                eqoItem.setEqoiQty(eqoiTallyOut)
                            }
                        }
                    }
                }
            }else if (booking.eqoTallyReceive == 0){
                booking.purge()
            }
        }
    }
    private final static Logger LOGGER = Logger.getLogger(ITSBkgValidationPersistenceCallback.class)
}
