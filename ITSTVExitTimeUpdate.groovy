import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckTransactionStage
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger


/*
* @Author <a href="mailto:skishore@weservetech.com">KISHORE KUMAR S</a>
* Date: 19/04/2022
* Requirements:- Updating Exit time field in TruckVisitDetails Entity by getting the value from Security out gate
*  @Inclusion Location	: Incorporated as a code extension of the type GATE_TASK_INTERCEPTOR --> Paste this code (ITSTVExitTimeUpdate.groovy)
*/


class ITSTVExitTimeUpdate extends AbstractGateTaskInterceptor{
    private  static final Logger LOGGER = Logger.getLogger(ITSTVExitTimeUpdate.class)

    public void execute (TransactionAndVisitHolder inWfCtx){
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("***** ITSTVExitTimeUpdate Starts *****")

        TruckTransaction tran = inWfCtx.getTran()
        LOGGER.debug("Transaction ::"+tran)
        if (tran != null){
            LOGGER.debug("*** First If Loop ***")
            TruckTransactionStage visitStage = tran.getTranStages().getAt(5)
            LOGGER.debug("visitStage :::"+visitStage)
            Date startTimeSecurityGate = visitStage.getTtstageStart()
            LOGGER.debug("startTimeSecurityGate :::"+startTimeSecurityGate)

            if (startTimeSecurityGate != null){
                LOGGER.debug("*** Second If Loop ***")
                TruckVisitDetails visitDetails = inWfCtx.getTv().setTvdtlsFlexDate01(startTimeSecurityGate)
                LOGGER.debug("VisitDetails Updated to ExitTime :::"+visitDetails)
            }
        }
    }
}