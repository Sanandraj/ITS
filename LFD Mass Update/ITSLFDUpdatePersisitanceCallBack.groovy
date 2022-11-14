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
*  @Author <ahref="mailto:mharikumar@weservetech.com"  >  Harikumar M</a>,
*  Date: 20/Oct/2022
*  Requirement: To bulk update Last free Day for Units with reason.
*  @Inclusion Location	: Incorporated as a code extension of the type TRANSACTED_BUSINESS_FUNCTION.Copy --> Paste this code (ITSLFDUpdatePersisitanceCallBack.groovy)
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
                        if (ufv.isTransitState(UfvTransitStateEnum.S70_DEPARTED) || ufv.isTransitState(UfvTransitStateEnum.S99_RETIRED)) {
                            builder.append("Unit with ID ${unit.getUnitId()} not in yard.").append("\n")
                            isError = Boolean.TRUE
                            continue
                        }
                        EFieldChange lfdFc = fieldChanges.findFieldChange(InventoryField.UFV_LAST_FREE_DAY)
                        EFieldChange notesFc = fieldChanges.findFieldChange(InventoryField.UFV_FLEX_STRING01)
                        Date guaranteeThruDay = ufv.getUfvGuaranteeThruDay()
                        try {
                            if (guaranteeThruDay != null && lfdFc != null) {
                                Date lfdNewVal = (Date) lfdFc?.getNewValue()
                                if (lfdNewVal != null) {
                                    if (lfdNewVal.before(ufv.getUfvGuaranteeThruDay())) {
                                        ufv.setUfvLastFreeDay(ufv.getUfvGuaranteeThruDay())
                                    } else if (lfdNewVal.after(ufv.getUfvGuaranteeThruDay())) {
                                        ufv.setUfvLastFreeDay(lfdNewVal)
                                    } else if (DateUtils.isSameDay(lfdNewVal, ufv.getUfvGuaranteeThruDay())) {
                                        ufv.setUfvLastFreeDay(ufv.getUfvGuaranteeThruDay())
                                    }
                                } else {
                                    ufv.setUfvLastFreeDay(null)
                                }

                                if (notesFc != null) {
                                    String notes = notesFc.getNewValue()
                                    ufv.setUfvFlexString01(notes)
                                }
                            } else if (lfdFc != null || notesFc != null) {
                                if (lfdFc != null) {
                                    Date lfddate = (Date) lfdFc.getNewValue()
                                    ufv.setUfvLastFreeDay(lfddate)
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
