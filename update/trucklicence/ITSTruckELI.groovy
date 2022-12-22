package update.trucklicence

import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.persistence.HibernateApi
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
 * For kalmar's usage the licence number to be copied to truck-id field, as they are using licence nbr as id nd back up the truck-id to notes field.
 *
 * */
class ITSTruckELI extends AbstractEntityLifecycleInterceptor {

    @Override
    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        try {
            LOGGER.setLevel(Level.DEBUG)
            //LOGGER.debug("onCreate BEGIN")
            Truck truck = (Truck) inEntity._entity;
            //LOGGER.debug("truck gkey: "+truck)

            if (truck && !isLicenseMatchWithExistingId(truck)) {
                inMoreFieldChanges.setFieldChange(RoadField.TRUCK_NOTES, truck.getTruckId())

                if (truck.getTruckLicenseNbr() && truck.getTruckLicenseNbr().length() > 10) {
                    LOGGER.debug("Capture_excess_char: "+truck.getTruckId()+", "+truck.getTruckLicenseNbr())
                    inMoreFieldChanges.setFieldChange(RoadField.TRUCK_ID, truck.getTruckLicenseNbr().substring(0, 10))
                } else {
                    inMoreFieldChanges.setFieldChange(RoadField.TRUCK_ID, truck.getTruckLicenseNbr())
                }
                LOGGER.debug("licence-update ::: " + truck.getTruckLicenseNbr())
            }

        } catch (Exception e) {
            LOGGER.error("Error in onCreate : "+e.getMessage())
        }
    }


    private boolean isLicenseMatchWithExistingId(Truck inTruck) {
        DomainQuery dq = QueryUtils.createDomainQuery("Truck").addDqPredicate(PredicateFactory.eq(RoadField.TRUCK_ID, inTruck.getTruckLicenseNbr()))
        List<Truck> list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        if (list && list.size() > 0)
            return true
        else
            return false
    }

    private static Logger LOGGER = Logger.getLogger(ITSTruckELI.class)

}
