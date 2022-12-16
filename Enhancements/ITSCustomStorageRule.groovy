/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.business.model.Facility
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.portal.FieldChanges
import com.navis.inventory.InventoryField
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.external.inventory.AbstractStorageRule
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger

import java.time.*


/**
 * @Author: uaarthi@weservetech.com, Aarthi U; Date: 22-11-2022
 *
 *  Requirements: Custom start rule for import demurrage charges (includes 3-2)
 *                1.The application should start Import demurrage:
 *                   a.	If these holds exists, demurrage starts when these holds are released 1H, 7H, 2H, 71, 72, 73
 *                   b.	Otherwise demurrage starts at the first 3am after discharge
 *
 * @Inclusion Location: Incorporated as a code extension of the type STORAGE_RULE
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSCustomStorageRule
 *     Code Extension Type: STORAGE_RULE
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 *
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
                    Date firstDeliverableDate = ufv.getUfvFlexDate01()
                    if (firstDeliverableDate != null) {
                        LocalDateTime lcDate = firstDeliverableDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                        if (!StringUtils.isEmpty(unit.getUnitFlexString06()) && "Y".equalsIgnoreCase(unit.getUnitFlexString06())) {
                            return firstDeliverableDate
                        }
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


    private static Logger log = Logger.getLogger(this.class)
}
