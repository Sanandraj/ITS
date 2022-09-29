import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChange
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.presentation.ui.message.ButtonTypes
import com.navis.framework.presentation.ui.message.MessageType
import com.navis.framework.presentation.ui.message.OptionDialog
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageCollectorFactory
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import org.apache.commons.lang.time.DateUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.text.SimpleDateFormat

class ITSValidateGuaranteeFormSubmission extends AbstractFormSubmissionCommand {
    private static final Logger LOGGER = Logger.getLogger(ITSValidateGuaranteeFormSubmission.class)

    @Override
    void submit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        //super.submit(inVariformId, inEntityId, inGkeys, inOutFieldChanges, inNonDbFieldChanges, inParams)
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("inVariformId" + inVariformId)
        LOGGER.debug("inEntityId" + inEntityId)
        LOGGER.debug("inGkeys" + inGkeys)
        LOGGER.debug("inOutFieldChanges" + inOutFieldChanges)
        LOGGER.debug("inNonDbFieldChanges" + inNonDbFieldChanges)
        LOGGER.debug("inParams" + inParams)
        if (inGkeys != null) {
            MessageCollector mc = MessageCollectorFactory.createMessageCollector();
            StringBuilder builder = new StringBuilder()
            boolean isError = false
            PersistenceTemplate pt = new PersistenceTemplate(userContext);
            pt.invoke(new CarinaPersistenceCallback() {
                protected void doInTransaction() {
                    for (Serializable gkey : inGkeys) {
                        //Serializable gkey = gkeys.get(0)
                        UnitFacilityVisit ufv = UnitFacilityVisit.hydrate(gkey)
                        LOGGER.debug("ufv" + ufv)
                        Unit unit = ufv.getUfvUnit()
                        LOGGER.debug("unit" + unit)
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

                        EFieldChange lfdFc = inOutFieldChanges.findFieldChange(MetafieldIdFactory.valueOf("ufvLastFreeDay"))
                        EFieldChange notesFc = inOutFieldChanges.findFieldChange(MetafieldIdFactory.valueOf("ufvFlexString04"))
                        if (lfdFc != null && notesFc != null) {
                            if (ufv.getUfvGuaranteeThruDay() != null) {
                                Date lfdNewVal = (Date) lfdFc.getNewValue()
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
                                    //DateUtils.isSameDay(lfdDate,guaranteeDate)
                                    ufv.setUfvLastFreeDay(ufv.getUfvGuaranteeThruDay())
                                }
                                if (notesFc.getNewValue() != null) {
                                    String notes = notesFc.getNewValue()
                                    ufv.setUfvFlexString04(notes)
                                }
                            }
                            if (lfdFc.getNewValue() != null) {
                                Date lfddate = (Date) lfdFc.getNewValue()
                                ufv.setUfvLastFreeDay(lfddate)
                            }
                            if (notesFc.getNewValue() != null) {
                                String notes = notesFc.getNewValue()
                                ufv.setUfvFlexString04(notes)
                            }
                        }
                        HibernateApi.getInstance().save(ufv)
                    }
                }
            })
            if (isError) {
                OptionDialog.showMessage(builder.toString(), "LFD Mass Update", ButtonTypes.OK, MessageType.WARNING_MESSAGE, null)
            }
        }
    }
}
