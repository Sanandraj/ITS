import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.inventory.business.units.EqBaseOrderItem
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.services.business.rules.EventType
import com.navis.vessel.business.schedule.VesselVisitDetails
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
        LOGGER.debug("Before Null check")
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
                            }
                            if (eqoiTallyOut >= eqoiTallyIn && eqoiTallyOut < eqoiQty) {
                                eqoItem.setEqoiQty(eqoiTallyOut)
                            }
                        }
                    }
                }
            }else if (booking.eqoTallyReceive == 0){
                booking.purge()
                LOGGER.debug("booking purged")
            }
        }
        else {
            LOGGER.debug("Booking Is Null")
        }
    }
    private final static Logger LOGGER = Logger.getLogger(ITSBkgValidationPersistenceCallback.class)
}
