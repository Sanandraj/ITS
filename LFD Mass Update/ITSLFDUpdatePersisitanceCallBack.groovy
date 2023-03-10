/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/

import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.external.framework.util.EFieldChange
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.FieldChanges
import com.navis.inventory.InventoryField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import org.apache.commons.lang.time.DateUtils
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

/*
*
*  @Author: mailto:mharikumar@weservetech.com, Harikumar M; Date: 20/10/2022
*
*  Requirements: To bulk update Line Last free Day for Units with reason.
*
*  @Inclusion Location: Incorporated as a code extension of the type
*
*  Load Code Extension to N4:
*  1. Go to Administration --> System --> Code Extensions
*  2. Click Add (+)
*  3. Enter the values as below:
*     Code Extension Name: ITSLFDUpdatePersisitanceCallBack
*     Code Extension Type: TRANSACTED_BUSINESS_FUNCTION
*     Groovy Code: Copy and paste the contents of groovy code.
*  4. Click Save button
*
*
*  S.No    Modified Date   Modified By     Jira      Description
*
*/

class ITSLFDUpdatePersisitanceCallBack extends AbstractExtensionPersistenceCallback {

    private static final Logger LOGGER = Logger.getLogger(ITSLFDUpdatePersisitanceCallBack.class);
    public static final String STRING_BUILDER = "STRING_BUILDER"

    @Override
    void execute(@Nullable Map inParms, @Nullable Map inOutResults) {

        //LOGGER.setLevel(Level.DEBUG)
        StringBuilder builder = new StringBuilder()
        Boolean isError = Boolean.FALSE
        List<Serializable> gKeys = (List<Serializable>) inParms.get("GKEY")
        FieldChanges fieldChanges = (FieldChanges) inParms.get("FIELD_CHANGES")
        if (gKeys != null) {
            for (Serializable gkey : gKeys) {
                UnitFacilityVisit ufv = (UnitFacilityVisit) UnitFacilityVisit.hydrate(gkey)

                if (ufv != null) {
                    Unit unit = ufv.getUfvUnit()
                    if (unit != null) {
                        if (!unit.getUnitCategory().equals(UnitCategoryEnum.IMPORT)) {
                            builder.append("Unit ${unit.getUnitId()} is not a Import unit.").append("\n")
                            isError = Boolean.TRUE
                            continue
                        }
                        if (ufv.isTransitState(UfvTransitStateEnum.S10_ADVISED) || ufv.isTransitState(UfvTransitStateEnum.S20_INBOUND) ||
                            ufv.isTransitState(UfvTransitStateEnum.S70_DEPARTED) || ufv.isTransitState(UfvTransitStateEnum.S99_RETIRED)) {
                            builder.append("Unit with ID ${unit.getUnitId()} not in yard.").append("\n")
                            isError = Boolean.TRUE
                            continue
                        }

                        if (fieldChanges != null) {
                            EFieldChange lineLfdFc = fieldChanges.findFieldChange(InventoryField.UFV_LINE_LAST_FREE_DAY)
                            EFieldChange notesFc = fieldChanges.findFieldChange(InventoryField.UFV_FLEX_STRING01)
                            Date lineGuaranteeThruDay = ufv.getUfvLineGuaranteeThruDay()
                            try {
                                if (lineGuaranteeThruDay != null && lineLfdFc != null) {
                                    Date lfdNewVal = (Date) lineLfdFc?.getNewValue()
                                    if (lfdNewVal != null) {
                                        if (lfdNewVal.before(lineGuaranteeThruDay)) {
                                            ufv.setUfvLineLastFreeDay(lineGuaranteeThruDay)
                                        } else if (lfdNewVal.after(lineGuaranteeThruDay)) {
                                            ufv.setUfvLineLastFreeDay(lfdNewVal)
                                        } else if (DateUtils.isSameDay(lfdNewVal, lineGuaranteeThruDay)) {
                                            ufv.setUfvLineLastFreeDay(lineGuaranteeThruDay)
                                        }
                                    } else {
                                        ufv.setUfvLineLastFreeDay(null)
                                    }

                                    if (notesFc != null) {
                                        String notes = notesFc.getNewValue()
                                        ufv.setUfvFlexString01(notes)
                                    }
                                } else if (lineLfdFc != null || notesFc != null) {
                                    if (lineLfdFc != null) {
                                        Date lfddate = (Date) lineLfdFc.getNewValue()
                                        ufv.setUfvLineLastFreeDay(lfddate)
                                    }
                                    if (notesFc != null) {
                                        String notes = notesFc.getNewValue()
                                        ufv.setUfvFlexString01(notes)
                                    }
                                }
                                HibernateApi.getInstance().save(ufv)
                            }
                            catch (Exception e) {
                                LOGGER.debug("Exception::" + e)
                            }
                        }
                    }
                } else {
                    builder.append("UnitFacilityVisit not found.").append("\n")
                    isError = Boolean.TRUE
                }

            }
        } else {
            builder.append("Gkeys not found.").append("\n")
            isError = Boolean.TRUE
        }
        inOutResults.put(STRING_BUILDER, builder.toString())
        inOutResults.put("ERROR", isError)
    }
}

