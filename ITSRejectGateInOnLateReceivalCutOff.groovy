import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger

class ITSRejectGateInOnLateReceivalCutOff extends AbstractGateTaskInterceptor {
    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        executeInternal(inWfCtx)
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug( "ITSRejectGateInOnLateReceivalCutOff STARTS::")
        TruckTransaction tran = inWfCtx.getTran()
        TruckVisitDetails truckDetails = inWfCtx.getTv()
        if (tran.getTranAppointment().getGapptVesselVisit() != null){
            Serializable vvd = tran.getTranAppointment().getGapptVesselVisit().getCvGkey()
            LOGGER.debug("vvd :: "+vvd)
            VesselVisitDetails vesselVisitDetails = VesselVisitDetails.hydrate(vvd)
            LOGGER.debug("vesselVisitDetails :: "+vesselVisitDetails)
            TimeZone timeZone = ContextHelper.getThreadUserTimezone();
            LOGGER.debug("timeZone :: "+timeZone)
            if (vesselVisitDetails.getVvFlexDate01().after(ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))){
                LOGGER.debug("***** correctTime Loop *****")
                tran.recordStageCompleted("ingate")
                LOGGER.debug("*** Saved Transaction ***")
            }
            else {
                LOGGER.debug("Inside past cutoff time")
                getMessageCollector().appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("Late Receival CutOff Past"),"Please contact Administration")
            }
        }
    }
    private static Logger LOGGER = Logger.getLogger(ITSRejectGateInOnLateReceivalCutOff.class)
}