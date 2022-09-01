import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.atoms.TransactionClassEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author <a href="mailto:skishore@weservetech.com">KISHORE KUMAR S</a>
 */
class ITSCompleteDropOffOutGate extends AbstractGateTaskInterceptor{
    private static final Logger LOGGER = Logger.getLogger(ITSCompleteDropOffOutGate.class)
    void execute(TransactionAndVisitHolder inWfCtx) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSCompleteDropOffOutGate Starts::")
        TruckTransaction truckTransaction = inWfCtx.getTran()
        if (truckTransaction != null && truckTransaction.isDelivery()) {
            TruckVisitDetails truckVisitDetails = truckTransaction.getTranTruckVisit()
            LOGGER.debug("truckVisitDetails :: "+truckVisitDetails)
            if (truckVisitDetails != null) {
                Set<TruckTransaction> dropOffContainers = truckVisitDetails.getTransactionsToBeHandled(TransactionClassEnum.DROPOFF)
                LOGGER.debug("dropOffContainers :: "+dropOffContainers)
                for (TruckTransaction tran : dropOffContainers) {
                    if(TranStatusEnum.OK.equals(tran.getTranStatus())) {
                        tran.setTranStatus(TranStatusEnum.COMPLETE)
                        LOGGER.debug("ITSCompleteDropOffOutGate Code Ends")
                    }
                }
            }
        }
    }
}
