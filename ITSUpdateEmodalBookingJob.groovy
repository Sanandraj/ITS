package ITS

import com.navis.argo.ArgoField
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.external.argo.AbstractGroovyJobCodeExtension
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.orders.OrdersEntity
import com.navis.orders.OrdersField
import com.navis.orders.business.eqorders.Booking
import org.apache.log4j.Logger

/**
 ITSUpdateEmodalBookingJob is groovy job code extensions that initiates an update of booking(s) on emodal.
 returns a count of bookings that could be found.

 Installation: install this source code in n4 as a Groovy job code extension

 */
class ITSUpdateEmodalBookingJob extends AbstractGroovyJobCodeExtension {

    /**
     * execute is the entry point for the groovy job.
     * This returns the number of bookings that could be updated.
     *
     * @param args Arguments from the soap request.
     */
    @Override
    void execute(Map<String, Object> inParams) {
        def bookingUtil = getLibrary("ITSEmodalLibrary");
        final CarrierVisitPhaseEnum[] activePhases = [
                CarrierVisitPhaseEnum.CREATED,
                CarrierVisitPhaseEnum.INBOUND,
                CarrierVisitPhaseEnum.ARRIVED,
                CarrierVisitPhaseEnum.WORKING,
                CarrierVisitPhaseEnum.COMPLETE]
        final MetafieldId BOOKING_PHASE = MetafieldIdFactory.getCompoundMetafieldId(OrdersField.EQO_VESSEL_VISIT, ArgoField.CV_VISIT_PHASE)
        final DomainQuery bookingDq = QueryUtils
                .createDomainQuery(OrdersEntity.BOOKING)
                .addDqPredicate(PredicateFactory.in(BOOKING_PHASE, activePhases))
        //.addDqField(InventoryField.EQBO_NBR)
        Serializable[]  bookingGkeys = HibernateApi.getInstance().findPrimaryKeysByDomainQuery(bookingDq);
        if (bookingGkeys != null && bookingGkeys.length >0) {
            for (Serializable bookingGkey : bookingGkeys) {
                Booking book = Booking.hydrate(bookingGkey);
                if (book != null) {

                    bookingUtil.execute(book, null);
                }
            }
            logMsg("Sent update for bookings " + bookingGkeys.length)
        }
    }
    private void logMsg(String msg) {
        LOGGER.warn("ITSUpdateEmodalBookingJob " + msg)
    }

    private final static Logger LOGGER = Logger.getLogger(ITSUpdateEmodalBookingJob.class)
}
