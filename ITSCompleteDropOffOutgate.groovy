import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 *  @Author <a href="mailto:skishore@weservetech.com">Kishore Kumar S</a>
 */

class ITSRejectGateInOnLateReceivalCutOff extends AbstractGateTaskInterceptor {
    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug( "b STARTS::")
        TruckTransaction tran = inWfCtx.getTran()
        if (tran.getTranAppointment().getGapptVesselVisit() != null){
            Serializable vvd = tran.getTranAppointment().getGapptVesselVisit().getCvGkey()
            LOGGER.debug("vvd :: "+vvd)
            VesselVisitDetails vesselVisitDetails = VesselVisitDetails.hydrate(vvd)
            LOGGER.debug("vesselVisitDetails :: "+vesselVisitDetails)
            TimeZone timeZone = ContextHelper.getThreadUserTimezone();
            LOGGER.debug("timeZone :: "+timeZone)
            if (vesselVisitDetails.getVvFlexDate01().after(ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))){
                executeInternal(inWfCtx)
                LOGGER.debug("***** correctTime Loop *****")
            }
        }
    }
    private static Logger LOGGER = Logger.getLogger(ITSRejectGateInOnLateReceivalCutOff.class)
}
