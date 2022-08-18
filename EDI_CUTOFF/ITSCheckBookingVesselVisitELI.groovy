import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.business.Roastery
import com.navis.framework.util.BizFailure
import com.navis.framework.util.message.MessageCollector
import com.navis.orders.business.eqorders.Booking
import com.navis.vessel.business.api.VesselFinder
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger
import java.text.SimpleDateFormat

/*
*
* @Author <a href="mailto:sanandaraj@servimostech.com">S Anandaraj</a>, 12/JUL/2022
*
* Requirements : This groovy is used to validate an EDI Booking Vessel Visit Cut Off.
*
* @Inclusion Location	: Incorporated as a code extension of the type ENTITY_LIFECYCLE_INTERCEPTOR.Copy --> Paste this code (ITSCheckBookingVesselVisitELI.groovy)
*
*/

public class ITSCheckBookingVesselVisitELI extends AbstractEntityLifecycleInterceptor {

    def vesselFinder = Roastery.getBean(VesselFinder.BEAN_ID)
    private static Logger LOGGER = Logger.getLogger(ITSCheckBookingVesselVisitELI.class);

    @Override
    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        this.onCreateOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges, "onCreate")
    }

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        this.onCreateOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges, "onUpdate")
    }

    @Override
    void preDelete(EEntityView inEntity) {
        Booking thisBooking = inEntity._entity;
        DataSourceEnum thisDataSource = ContextHelper.getThreadDataSource();

        this.CheckForBookingLock(thisBooking, thisDataSource, null);

    }

    private void onCreateOrUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges, String inType) {
        //LOGGER.setLevel(Level.DEBUG)
            LOGGER.debug("ITSCheckBookingVesselVisitELI: Started");
        Booking thisBooking = inEntity._entity;
        DataSourceEnum thisDataSource = ContextHelper.getThreadDataSource();
        this.CheckForBookingLock(thisBooking, thisDataSource, inOriginalFieldChanges);

    }

    private void CheckForBookingLock(Booking thisBooking, DataSourceEnum thisDataSource, EFieldChangesView inFieldChange) {
        boolean isNotValid = Boolean.FALSE;
        def sdf = new SimpleDateFormat("yyyy-MM-dd")
       if (thisDataSource == DataSourceEnum.EDI_BKG) {
           CarrierVisit carrierVisit = thisBooking.getEqoVesselVisit();
           VesselVisitDetails vvd = vesselFinder.findVvByVisitDetails(carrierVisit.getCvCvd());
           if (carrierVisit != null && carrierVisit.getCvCvd() != null) {
               if (vvd != null && vvd.getVvFlexDate02()!=null && sdf.format(ArgoUtils.timeNow()) >= sdf.format(vvd.getVvFlexDate02())) {
                   isNotValid = Boolean.TRUE;
               }
           }
       }

        if (isNotValid) {
            MessageCollector messageCollector = ContextHelper.getThreadMessageCollector();
            messageCollector.appendMessage(BizFailure.create("EDI Booking Vessel Visit Cut Off locked"));
        }
    }
}
