package update.trucklicence

import com.navis.external.argo.AbstractGroovyJobCodeExtension
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.road.RoadField
import com.navis.road.business.model.Truck
import org.apache.log4j.Level
import org.apache.log4j.Logger


/**
 * @author <a href="mailto:sramasamy@weservetech.com"> Ramasamy Sathappan</a>
 * @since 26-Dec-2022
 *
 * This is an hourly job that would correct the id/license mismatch
 *
 * */
class UpdateTruckLicenceToTruckIdJob extends AbstractGroovyJobCodeExtension {

    @Override
    void execute(Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)

        DomainQuery subQuery = QueryUtils.createDomainQuery("Truck").addDqField(RoadField.TRUCK_ID)

        DomainQuery dq = QueryUtils.createDomainQuery("Truck")
                .addDqPredicate(PredicateFactory.subQueryNotIn(subQuery, RoadField.TRUCK_LICENSE_NBR))
                .addDqOrdering(Ordering.asc(RoadField.TRUCK_CREATED))
        List<Truck> truckList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        LOGGER.debug("trucklist size: " + truckList.size())


        for (Truck truck : truckList) {
            LOGGER.debug("truckId: "+truck.getTruckId() + ", " + truck.getTruckLicenseNbr() + ", " + truck.getTruckNotes()+":")
            try {
                if (!isLicenseMatchWithExistingId(truck)) {
                    LOGGER.debug("proceed")
                    if (truck.getTruckNotes() != truck.getTruckId())
                        truck.setFieldValue(RoadField.TRUCK_NOTES, truck.getTruckId())

                    if (truck.getTruckLicenseNbr() && truck.getTruckLicenseNbr().length() > 10) {
                        LOGGER.debug("Capture_excess_char: "+truck.getTruckId()+", "+truck.getTruckLicenseNbr())
                        truck.setFieldValue(RoadField.TRUCK_ID, truck.getTruckLicenseNbr().substring(0, 10))
                    } else {
                        truck.setFieldValue(RoadField.TRUCK_ID, truck.getTruckLicenseNbr())
                    }

                    //hApi.save(truck)  //At times, not working
                    HibernateApi.getInstance().save(truck)
                    LOGGER.debug("licence updated : " + truck.getTruckLicenseNbr())
                }

            } catch (Exception e) {
                LOGGER.error("Error while execute : " + truck.getTruckId() + " :: "+ e.getMessage())
            }
        }
        LOGGER.debug("updation completed")
    }

    private boolean isLicenseMatchWithExistingId(Truck inTruck) {
        DomainQuery dq = QueryUtils.createDomainQuery("Truck").addDqPredicate(PredicateFactory.eq(RoadField.TRUCK_ID, inTruck.getTruckLicenseNbr()))
        List<Truck> list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        LOGGER.debug("list: "+list)

        if (list && list.size() > 0)
            return true
        else
            return false
    }

    private static HibernateApi hApi = HibernateApi.getInstance();
    private static int MAX_COUNT = 3000;

    private static Logger LOGGER = Logger.getLogger(UpdateTruckLicenceToTruckIdJob.class)

}