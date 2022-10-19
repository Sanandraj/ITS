import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.portal.FieldChange
import com.navis.road.RoadApptsField
import com.navis.road.business.appointment.model.GateAppointment
import org.apache.log4j.Level
import org.apache.log4j.Logger

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
        LOGGER.debug(" onCreateOrUpdate inOriginalFieldChanges :: "+inOriginalFieldChanges)
        LOGGER.debug(" onCreateOrUpdate inMoreFieldChanges :: "+inMoreFieldChanges)

        FieldChange gateApptStateFc = inOriginalFieldChanges.hasFieldChange(RoadApptsField.GAPPT_UFV_FLEX_STRING01) ? (FieldChange) inOriginalFieldChanges.findFieldChange(RoadApptsField.GAPPT_UFV_FLEX_STRING01) : null;
        if(gateApptStateFc != null){
            gateAppointment.setPinNumber(gateApptStateFc.getNewValue());
        }

    }
}
