import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.model.CarrierVisit
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
        LOGGER.debug( "ITSRejectGateInOnLateReceivalCutOff STARTS::")
        TruckTransaction tran = inWfCtx.getTran()
        if (tran.getTranAppointment().getGapptVesselVisit() != null){
            Serializable cvGkey = tran.getTranAppointment().getGapptVesselVisit().getCvGkey()
            LOGGER.debug("cvGkey :: "+cvGkey)
            String cv = CarrierVisit.hydrate(cvGkey).getCvId()
            LOGGER.debug("vesselVisitDetails :: "+cv)
            VesselVisitDetails vvd= VesselVisitDetails.resolveVvdFromCv(tran.getTranAppointment().getGapptVesselVisit())
            TimeZone timeZone = ContextHelper.getThreadUserTimezone()
            if (vvd.getVvFlexDate01() != null){
                if ((ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone)).after(vvd.getVvFlexDate01())){
                    LOGGER.debug("Inside after cutoff")
                    executeInternal(inWfCtx)
                }
                /*else {
                    new GroovyApi().registerError("Contact Administration - Late-Receival Cutoff Past")
                }*/
            }
            else {
                executeInternal(inWfCtx)
            }
        }
    }
    private static Logger LOGGER = Logger.getLogger(ITSRejectGateInOnLateReceivalCutOff.class)
}
