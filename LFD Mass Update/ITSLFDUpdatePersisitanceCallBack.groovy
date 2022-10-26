import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.external.framework.util.EFieldChange
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.FieldChanges
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import org.apache.commons.lang.time.DateUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

import java.text.SimpleDateFormat

class ITSLFDUpdatePersisitanceCallBack extends AbstractExtensionPersistenceCallback {

    private static final Logger LOGGER = Logger.getLogger(ITSLFDUpdatePersisitanceCallBack.class);

    @Override
    void execute(@Nullable Map inParms, @Nullable Map inOutResults) {

        LOGGER.setLevel(Level.DEBUG)
        StringBuilder builder = new StringBuilder()
        boolean isError = false
        List<Serializable> gKeys = (List<Serializable>) inParms.get("GKEY")
        FieldChanges fieldChanges = (FieldChanges) inParms.get("FIELD_CHANGES")
        LOGGER.warn("gKeys" + gKeys)
        LOGGER.warn("fieldChanges" + fieldChanges.getFieldIds())
        if (gKeys != null) {
            for (Serializable gkey : gKeys) {
                UnitFacilityVisit ufv = (UnitFacilityVisit) UnitFacilityVisit.hydrate(gkey)

                if (ufv != null) {
                    Unit unit = ufv.getUfvUnit()
                    if (unit != null) {
                        if (!unit.getUnitCategory().equals(UnitCategoryEnum.IMPORT)) {
                            builder.append("Unit ${unit.getUnitId()} is not a Import unit.").append("\n")
                            isError = true
                            continue
                        }
                        if (ufv.isTransitState(UfvTransitStateEnum.S70_DEPARTED) || ufv.isTransitState(UfvTransitStateEnum.S99_RETIRED)) {
                            builder.append("Unit with ID ${unit.getUnitId()} not in yard.").append("\n")
                            isError = true
                            continue
                        }
                        EFieldChange lfdFc = fieldChanges.findFieldChange(MetafieldIdFactory.valueOf("ufvLastFreeDay"))
                        EFieldChange notesFc = fieldChanges.findFieldChange(MetafieldIdFactory.valueOf("ufvFlexString01"))
                        Date guaranteeThruDay = ufv.getUfvGuaranteeThruDay()
                        if (guaranteeThruDay != null && lfdFc != null) {
                            Date lfdNewVal = (Date) lfdFc?.getNewValue()
                            if (lfdNewVal != null) {
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd")
                                String lfd = sdf.format(lfdNewVal)
                                String guarantee = sdf.format(ufv.getUfvGuaranteeThruDay())
                                Date lfdDate = Date.parse("yyyy-MM-dd", lfd)
                                Date guaranteeDate = Date.parse("yyyy-MM-dd", guarantee)
                                if (lfdDate.before(guaranteeDate)) {
                                    ufv.setUfvLastFreeDay(ufv.getUfvGuaranteeThruDay())
                                } else if (lfdDate.after(guaranteeDate)) {
                                    ufv.setUfvLastFreeDay(lfdNewVal)
                                } else if (DateUtils.isSameDay(lfdDate, guaranteeDate)) {
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
                }
            }
            inOutResults.put("STRING_BUILDER", builder.toString())
            inOutResults.put("ERROR", isError)
        }

    }
}
