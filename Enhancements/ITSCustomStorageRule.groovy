package ITS.Enhancements


import com.navis.argo.business.model.Facility
import com.navis.cargo.InventoryCargoEntity
import com.navis.cargo.InventoryCargoField
import com.navis.cargo.business.model.BlRelease
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.FieldChanges
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryField
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.external.inventory.AbstractStorageRule
import org.apache.log4j.Logger

import java.time.*

/**
 * UAarthi - Custom start rule for import demurrage charges (includes 3-2)
 *
 * 1.	The application should start Import demurrage:
 *  a.	If these holds exists, demurrage starts when these holds are released 1H, 7H, 2H, 71, 72, 73
 *  b.	Otherwise demurrage starts at the first 3am after discharge

 */

class ITSCustomStorageRule extends AbstractStorageRule {
    @Override
    public Date calculateStorageStartDate(EFieldChanges inChanges) {
        return calculateImportStorageStartDate(inChanges);
    }

    @Override
    Date calculateStorageEndDate(EFieldChanges eFieldChanges) {
        //Default logic
        return null
    }

    public Date calculateImportStorageStartDate(EFieldChanges inChanges) {
        FieldChanges fieldChanges = (FieldChanges) inChanges;
        Unit unit = null;
        Date startDay = null;

        if (fieldChanges != null) {
            unit = (Unit) fieldChanges.getFieldChange(InventoryField.UFV_UNIT).getNewValue();
            if (unit != null) {
                Facility fcy = Facility.findFacility("PIERG");
                UnitFacilityVisit ufv = unit.getUfvForFacilityNewest(fcy);
                if (ufv != null) {
                    Date timeInYard = ufv.getUfvTimeEcIn() != null ? ufv.getUfvTimeEcIn() : ufv.getUfvTimeIn();
                    if (timeInYard != null) {

                        /*  InventoryCargoManager inventoryCargoManager = (InventoryCargoManager) Roastery.getBean(InventoryCargoManager.BEAN_ID);
                          Set<BillOfLading> blSet = (Set<BillOfLading>) inventoryCargoManager.getBlsForGoodsBl(unit);

                          List<Long> blKeys = blSet.stream().map(bl -> bl.getGkey()).collect(Collectors.toList())
                          if(blKeys != null && !blKeys.isEmpty()){
                              List<String> dispCodes = ["1H","7H", "2H", "71", "72", "73"]
                              findBlReleases(blKeys,dispCodes)
                          }*/

                        LocalDateTime lcDate = timeInYard.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                        int hour = lcDate.getHour()
                        if (hour < 3) {
                            ZonedDateTime zonedDateTime = lcDate.withHour(3).withMinute(0).withSecond(0).atZone(ZoneOffset.systemDefault());
                            Instant instant = zonedDateTime.toInstant();

                            Date finalDate = Date.from(instant);
                            log.warn("finalDate -" + finalDate)

                            return finalDate
                        } else {
                            ZonedDateTime zonedDateTime = lcDate.plusDays(1).withHour(3).withMinute(0).withSecond(0).atZone(ZoneOffset.systemDefault());
                            Instant instant = zonedDateTime.toInstant();
                            log.warn("instant -" + instant.now())

                            Date finalDate = Date.from(instant);
                            return finalDate
                        }
                    }
                }
            }
        }
        return startDay
    }


    private List<BlRelease> findBlReleases(List<Long> inBlGkeys, List<String> inDispositionCodes) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryCargoEntity.BL_RELEASE)
                .addDqPredicate(PredicateFactory.in(InventoryCargoField.BLREL_BL, inBlGkeys))
                .addDqPredicate(PredicateFactory.in(InventoryCargoField.BLREL_DISPOSITION_CODE, inDispositionCodes));
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
    }

    private static final Logger log = Logger.getLogger(this.class)
}