package ITSIntegration

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.road.business.atoms.TruckVisitStatusEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger

/** Date: 31-10-2022
 *  @Author <a href="mailto:skishore@weservetech.com">Kishore Kumar S</a>
 * Requirements:- Verifying whether previous truck visit for the same Truck-Id is Complete or closed, else changing status to close while revisiting
 * @Inclusion Location	: Incorporated as a code extension of the type GATE_TASK_INTERCEPTOR --> Paste this code (ITSManageOpenTVToCompleteGateTaskInterceptor.groovy)
*/

class ITSManageOpenTVToCompleteGateTaskInterceptor extends AbstractGateTaskInterceptor{
    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
       // super.execute(inWfCtx)
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSManageOpenTVToCompleteGateTaskInterceptor STARTS :: ")
        TruckTransaction tran = inWfCtx.getTran()
        String truckId = tran.getTranTruckVisit().getTvdtlsTruckId()
        LOGGER.debug("Truck License Nbr :: "+truckId)
        List<TruckVisitDetails> truckVisitDetails = TruckVisitDetails.findTruckVisitByTruckId(truckId,0)
        LOGGER.debug("truckVisitDetails :: "+truckVisitDetails)
        for (TruckVisitDetails tvd : truckVisitDetails as List<TruckVisitDetails> ){
            if (tvd.getTvdtlsStatus().equals(TruckVisitStatusEnum.OK) || tvd.getTvdtlsStatus().equals(TruckVisitStatusEnum.TROUBLE)){
                tvd.setTvdtlsStatus(TruckVisitStatusEnum.CLOSED)
            }
        }
    }
    private static Logger LOGGER = Logger.getLogger(ITSManageOpenTVToCompleteGateTaskInterceptor.class)
}