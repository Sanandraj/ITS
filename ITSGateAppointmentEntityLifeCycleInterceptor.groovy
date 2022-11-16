import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.FieldChange
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.context.UserContextUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.road.RoadApptsField
import com.navis.road.business.appointment.model.GateAppointment
import com.navis.road.business.appointment.model.TruckVisitAppointment
import com.navis.road.business.atoms.TruckerFriendlyTranSubTypeEnum
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 *
 * uaarthi@weservetech.com 10/19 -- TT-31 Dual Flag Appears in Appointment List
 * ELI on Appointment to set Dual Flag for Truck Visit Appointments associated to multiple Appointments
 */

class ITSGateAppointmentEntityLifeCycleInterceptor extends AbstractEntityLifecycleInterceptor {
    private static Logger LOGGER = Logger.getLogger(this.class)

    @Override
    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        this.onCreateOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges)
    }

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        this.onCreateOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges)
    }


    private void onCreateOrUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug(" onCreateOrUpdate ")
        GateAppointment gateAppointment = inEntity._entity;
        LOGGER.debug(" onCreateOrUpdate inOriginalFieldChanges :: " + inOriginalFieldChanges)
        LOGGER.debug(" onCreateOrUpdate inMoreFieldChanges :: " + inMoreFieldChanges)

        PersistenceTemplate pt = new PersistenceTemplate(UserContextUtils.getSystemUserContext());
        pt.invoke(new CarinaPersistenceCallback() {
            protected void doInTransaction() {

                if (TruckerFriendlyTranSubTypeEnum.PUI.equals(gateAppointment.getGapptTranType())) {
                    Unit inGapptUnit = gateAppointment.getGapptUnit();
                    if (inGapptUnit == null) {


                        if (gateAppointment.getGapptCtrId() != null) {
                            DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                                    .addDqPredicate(PredicateFactory.in(UnitField.UNIT_VISIT_STATE, [UnitVisitStateEnum.ACTIVE, UnitVisitStateEnum.ADVISED]))
                                    .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_ID, gateAppointment.getGapptCtrId()))
                                    .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CATEGORY, UnitCategoryEnum.IMPORT));
                            Unit[] unitList = Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);
                            if (unitList != null && unitList.size() > 0) {
                                inGapptUnit = (Unit) unitList[0];
                            }

                        }


                    }

                    if (inGapptUnit != null) {
                        gateAppointment.setGapptUnitFlexString01(inGapptUnit.getUnitRouting() != null ? inGapptUnit.getUnitRouting().getRtgPinNbr() : null);
                    }
                }
            }
        })

        // Dual Flag implementation

        TruckVisitAppointment tva = null

        FieldChange tvaFc = inOriginalFieldChanges.hasFieldChange(RoadApptsField.GAPPT_TRUCK_VISIT_APPOINTMENT) ? (FieldChange) inOriginalFieldChanges.findFieldChange(RoadApptsField.GAPPT_TRUCK_VISIT_APPOINTMENT) : null;
        LOGGER.warn(" tvaFc :: " + tvaFc)

        if (tvaFc != null) {
            if (tvaFc.getNewValue()) {
                tva = (TruckVisitAppointment) tvaFc.getNewValue()
                reloadDualFlag(tva, gateAppointment, inMoreFieldChanges)

            }
            if (tvaFc.getPriorValue()) {
                tva = (TruckVisitAppointment) tvaFc.getPriorValue()
                reloadDualFlag(tva, gateAppointment, inMoreFieldChanges)
            }
        }
    }

    void reloadDualFlag(TruckVisitAppointment tva, GateAppointment gappt, EFieldChanges inMoreFieldChanges) {
        LOGGER.warn(" tva :: " + tva)

        if (tva != null) {
            Set<GateAppointment> apptSet = tva.getAppointments(GateAppointment.AppointmentStateGroupEnum.ACTIVE)
            LOGGER.warn("apptSet " + apptSet)
            String isDual = (apptSet != null && apptSet.size() >= 2) ? 'Y' : 'N'
            LOGGER.warn(" isDual " + isDual)
            apptSet.each {
                it.setGapptUnitFlexString02(isDual)
            }
            //gappt.setGapptUnitFlexString02(isDual)
            inMoreFieldChanges.setFieldChange(RoadApptsField.GAPPT_UNIT_FLEX_STRING02, isDual)
        } else {
            //gappt.setGapptUnitFlexString02('N')
            inMoreFieldChanges.setFieldChange(RoadApptsField.GAPPT_UNIT_FLEX_STRING02, 'N')

        }
    }
}
