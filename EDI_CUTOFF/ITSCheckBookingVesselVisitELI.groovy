import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.external.framework.util.ExtensionUtils
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.Entity
import com.navis.framework.persistence.HibernatingEntity
import com.navis.framework.util.BizViolation
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.orders.business.eqorders.Booking
import com.navis.services.business.event.Event
import com.navis.vessel.business.api.VesselFinder
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.vessel.business.schedule.VesselVisitLine
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.NotNull

import java.text.SimpleDateFormat

/*
*
* @Author <a href="mailto:sanandaraj@servimostech.com">S Anandaraj</a>, 26/OCT/2022
*
* Requirements : This groovy is used to validate an EDI Booking Vessel Visit Cut Off/Line Cut Off and also to send the booking details to Emodal while deleting the booking.
*
* @Inclusion Location	: Incorporated as a code extension of the type ENTITY_LIFECYCLE_INTERCEPTOR.Copy --> Paste this code (ITSCheckBookingVesselVisitELI.groovy)
*
*/

public class ITSCheckBookingVesselVisitELI extends AbstractEntityLifecycleInterceptor {

    VesselFinder vesselFinder = Roastery.getBean(VesselFinder.BEAN_ID)
    private static Logger LOGGER = Logger.getLogger(ITSCheckBookingVesselVisitELI.class)

    @Override
    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        this.onCreateOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges, "onCreate")
    }

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        this.onCreateOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges, "onUpdate")
    }

    @Override
    public void preDelete(Entity inEntity) {
        Object library = ExtensionUtils.getLibrary(ContextHelper.getThreadUserContext(), "ITSEmodalLibrary")
        Event event = null
        LOGGER.debug("preDelete" + inEntity)
        if (inEntity != null) {
            library.execute((HibernatingEntity) inEntity, event)
        }
    }

    private void onCreateOrUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges, String inType) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSCheckBookingVesselVisitELI: Started")
        Booking thisBooking = inEntity._entity
        DataSourceEnum thisDataSource = ContextHelper.getThreadDataSource()
        this.CheckForBookingLock(thisBooking, thisDataSource)

    }

    private void CheckForBookingLock(@NotNull Booking thisBooking,
                                     @NotNull DataSourceEnum thisDataSource) {
        if (thisDataSource == DataSourceEnum.EDI_BKG) {
            CarrierVisit carrierVisit = thisBooking.getEqoVesselVisit()
            if (carrierVisit != null) {
                VesselVisitDetails vvd = vesselFinder.findVvByVisitDetails(carrierVisit?.getCvCvd())
                Date cutOffDate = vvd.getVvFlexDate02();

                if (vvd != null) {
                    VesselVisitLine vvl = VesselVisitLine.findVesselVisitLine(vvd, thisBooking?.getEqoLine())
                    if (vvl != null && vvl.getVvlineTimeActivateYard() !=null) {
                        cutOffDate = vvl.getVvlineTimeActivateYard()
                    }


                    TimeZone timeZone = ContextHelper.getThreadUserTimezone()
                    Date currentDate = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone)
                    cutOffDate=ArgoUtils.convertDateToLocalDateTime(cutOffDate, timeZone)

                    //Date currentDate = ArgoUtils.timeNow()

                    LOGGER.debug("currentDate" + currentDate)


                    LOGGER.debug("ediCutoffDate" + cutOffDate)

                    //validating Vessel visit Edi cut off date and Line Cut off date

                    if(cutOffDate != null && currentDate!=null){
                        if(currentDate.after(cutOffDate)){
                            LOGGER.debug("currentDate after cutOffDate ::" )
                               getMessageCollector().registerExceptions(BizViolation.create(PropertyKeyFactory.valueOf("VesselVisit EDI CutOff/Line CutOff is Locked. Could not post EDI ."), (BizViolation) null))
                        }
                    }

                }
            }
        }

    }
}
