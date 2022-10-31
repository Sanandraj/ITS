package ITSIntegration

import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.orders.business.eqorders.Booking
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

class ITSBkgMassCancelPersistenceCallBack extends AbstractExtensionPersistenceCallback{
    @Override
    void execute(@Nullable Map input, @Nullable Map map1) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSBkgMassCancelPersistenceCallBack starts :: ")
        Serializable bookingOrder = (Serializable) input?.get("booking")
        Booking booking = Booking.hydrate(bookingOrder)
        LOGGER.debug("bookingOrder :: "+booking)
        if (booking.eqoTallyReceive == 0){
            LOGGER.debug("purged")
            booking.purge()
        }
    }
    private final static Logger LOGGER = Logger.getLogger(ITSBkgMassCancelPersistenceCallBack.class)
}
