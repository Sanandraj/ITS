/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/

import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Facility
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.portal.FieldChanges
import com.navis.inventory.InventoryField
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.external.inventory.AbstractStorageRule
import org.apache.log4j.Logger

/**
 * @Author: mailto: bgopal@weservetech.com, Gopal B; Date: 08/11/2022
 *
 *  Requirements: This code extension calculates the start date for Export Reefer charge calculation and end date for Import reefer.
 * For Exports, the start time will be the Train visit's Actual Time of Arrival.
 * for Imports, the end time till be Actual Time of Departure of Train.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSCustomReeferChargeRule
 *     Code Extension Type: STORAGE_RULE
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup: Create a Rule Id 1. REEFER POWER IMPORT RULE and 2. REEFER RULE SET in STORAGE RULE TYPES tab with Custom End Day Extension as ITSCustomReeferChargeRule
 *
 *
 *  S.No    Modified Date   Modified By                                Jira      Description
 *   1      25-01-2023      Anandaraj S <sanandaraj@weservetech.com>   IP-293    Requested for THROUGH Container.
 */


public class ITSCustomReeferChargeRule extends AbstractStorageRule {

    private static final Logger LOGGER = Logger.getLogger(ITSCustomReeferChargeRule.class);

    @Override
    public Date calculateStorageEndDate(EFieldChanges inChanges) {
        //for Imports
        return calculateImportStorageStartOrEndDate(inChanges, Boolean.FALSE);
    }

    @Override
    public Date calculateStorageStartDate(EFieldChanges inChanges) {
        //for imports
        return calculateImportStorageStartOrEndDate(inChanges, Boolean.TRUE);
    }

    public Date calculateImportStorageStartOrEndDate(EFieldChanges inChanges, Boolean isStartDate) {

        FieldChanges fieldChanges = (FieldChanges) inChanges;
        Unit unit = null;
        Date calculationTime = null;
        if (fieldChanges != null) {
            unit = (Unit) fieldChanges.getFieldChange(InventoryField.UFV_UNIT).getNewValue();
            if (unit != null) {
                Facility fcy = Facility.findFacility(facilityId);
                UnitFacilityVisit ufv = unit.getUfvForFacilityNewest(fcy);
                if (ufv != null) {

                    if (isStartDate) {
                        if (UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()) || UnitCategoryEnum.THROUGH.equals(unit.getUnitCategory())) {
                            calculationTime = ufv.getUfvTimeIn();
                        } else if (UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory())) {
                            // ITS wants Export reefers by train to have ATA
                            if (ufv.getUfvActualIbCv() != null && (LocTypeEnum.TRAIN.equals(ufv.getUfvActualIbCv().getCvCarrierMode())
                                    || LocTypeEnum.RAILCAR.equals(ufv.getUfvActualIbCv().getCvCarrierMode()))) {
                                calculationTime = ufv.getUfvActualIbCv().getCvATA();
                            } else {
                                calculationTime = ufv.getUfvTimeIn();
                            }
                        }
                    } else {
                        if (UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory())) {
                            calculationTime = ufv.getUfvTimeOfLoading() != null ? ufv.getUfvTimeOfLoading() : ufv.getUfvTimeOut();
                        } else if (UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()) || UnitCategoryEnum.THROUGH.equals(unit.getUnitCategory())) {
                            CarrierVisit carrierVisit = ufv.getUfvActualObCv() != null ? ufv.getUfvActualObCv() :
                                    ufv.getUfvIntendedObCv();
                            if (carrierVisit != null && (LocTypeEnum.TRAIN.equals(carrierVisit.getCvCarrierMode()) ||
                                    LocTypeEnum.RAILCAR.equals(carrierVisit.getCvCarrierMode()))) {
                                calculationTime = carrierVisit.getCvATD();
                            } else {
                                calculationTime = ufv.getUfvTimeOfLoading() != null ? ufv.getUfvTimeOfLoading() :
                                        ufv.getUfvTimeOut();
                            }
                        }
                    }
                }
            }
        }
        return calculationTime;
    }

    void logMsg(String msg) {
        LOGGER.warn("ITSCustomReeferChargeRule " + msg);
    }
    private String facilityId = "PIERG";
}


