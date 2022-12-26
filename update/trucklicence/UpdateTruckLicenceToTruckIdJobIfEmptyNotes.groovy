package update.trucklicence

import com.navis.argo.business.api.ArgoUtils
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
 * @since 21-Dec-2022
 *
 * As kalmar is using the licence number instead truck-id, this groovy job updates truck master records
 * by backing up the truck-id to notes field and copied the license nbr to id field.
 * This should executed as part of initial setup and turn off once processed the entire records.
 * Use 10 seconds (0/10 * * * * ?) cron expression to schedule.
 *
 * */
class UpdateTruckLicenceToTruckIdJobIfEmptyNotes extends AbstractGroovyJobCodeExtension {

    @Override
    void execute(Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)

        DomainQuery dq = QueryUtils.createDomainQuery("Truck")
                .addDqPredicate(PredicateFactory.isNull(RoadField.TRUCK_NOTES)).setDqMaxResults(MAX_COUNT)
                .addDqOrdering(Ordering.asc(RoadField.TRUCK_CREATED))
        List<Truck> truckList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        LOGGER.debug("trucklist size: " + truckList.size())

        for (Truck truck : truckList) {
            LOGGER.debug("truckId: "+truck.getTruckId() + ", " + truck.getTruckLicenseNbr() + ", " + truck.getTruckNotes()+":")
            if (truck.getTruckNotes() == null || ArgoUtils.isEmpty(truck.getTruckNotes()) || ArgoUtils.isEmpty(truck.getTruckNotes().trim())) {
                try {
                    if (!isLicenseMatchWithExistingId(truck)) {
                        LOGGER.debug("proceed")
                        truck.setFieldValue(RoadField.TRUCK_NOTES, truck.getTruckId())

                        if (truck.getTruckLicenseNbr() && truck.getTruckLicenseNbr().length() > 10) {
                            LOGGER.debug("Capture_excess_char: "+truck.getTruckId()+", "+truck.getTruckLicenseNbr())
                            truck.setFieldValue(RoadField.TRUCK_ID, truck.getTruckLicenseNbr().substring(0, 10))
                        } else {
                            truck.setFieldValue(RoadField.TRUCK_ID, truck.getTruckLicenseNbr())
                        }

                        hApi.save(truck)
                        LOGGER.debug("licence updated : " + truck.getTruckLicenseNbr())
                    }

                } catch (Exception e) {
                    LOGGER.error("Error while execute : " + truck.getTruckId() + " :: "+ e.getMessage())
                }
            }
        }
        LOGGER.debug("updation completed")
    }

    private boolean isLicenseMatchWithExistingId(Truck inTruck) {
        DomainQuery dq = QueryUtils.createDomainQuery("Truck").addDqPredicate(PredicateFactory.eq(RoadField.TRUCK_ID, inTruck.getTruckLicenseNbr()))
        List<Truck> list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)

        if (list && list.size() > 0)
            return true
        else
            return false
    }

    private static HibernateApi hApi = HibernateApi.getInstance();
    private static int MAX_COUNT = 3000;

    private static Logger LOGGER = Logger.getLogger(UpdateTruckLicenceToTruckIdJobIfEmptyNotes.class)

}
