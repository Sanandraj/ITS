/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.inventory.business.units.EqBaseOrderItem
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.orders.business.eqorders.Booking
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

/**
 * @Author: Kishore Kumar S <a href= skishore@weservetech.com / >, 28/10/2022
 * Requirements : 5-2-Button to Cancel unused bookings after vessel cut-offs -- This groovy is used to reduce multiple booking selected from booking entity .
 * @Inclusion Location	: Incorporated as a code extension of the type TRANSACTED_BUSINESS_FUNCTION.
 *  Load Code Extension to N4:
 1. Go to Administration --> System -->  Code Extension
 2. Click Add (+)
 3. Enter the values as below:
 Code Extension Name:  ITSBkgValidationPersistenceCallback.
 Code Extension Type:  TRANSACTED_BUSINESS_FUNCTION.
 Groovy Code: Copy and paste the contents of groovy code.
 4. Click Save button
 *
 */
class ITSBkgValidationPersistenceCallback extends AbstractExtensionPersistenceCallback{
    @Override
    void execute(@Nullable Map input, @Nullable Map inOutResults) {

        LOGGER.setLevel(Level.INFO)
        LOGGER.info("ITSBkgValidationPersistenceCallback starts :: ")
         Serializable bookingOrder = (Serializable) input?.get("entityGkey")
        Booking booking = Booking.hydrate(bookingOrder)
        if (booking ==null){
            return;
        }
        if (booking.eqoTallyReceive > 0){
            Set bkgItems = booking!= null ?  booking.getEqboOrderItems() : null;
            if (bkgItems != null && !bkgItems.isEmpty() && bkgItems.size() >= 1) {
                Iterator iterator = bkgItems.iterator()
                while (iterator.hasNext()) {
                    EquipmentOrderItem eqoItem = EquipmentOrderItem.resolveEqoiFromEqboi((EqBaseOrderItem) iterator.next())
                    Long eqoiQty = eqoItem?.getEqoiQty()
                    Long eqoiTallyOut = eqoItem?.getEqoiTally()
                    Long eqoiTallyIn = eqoItem?.getEqoiTallyReceive()
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
        }
        else if (booking.eqoTallyReceive == 0){
            booking.purge()
        }
    }
    private final static Logger LOGGER = Logger.getLogger(ITSBkgValidationPersistenceCallback.class)
}
