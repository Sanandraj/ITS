import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
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
                    Unit unit = tran.getTranUnit()
                    if (unit != null){
                        if(TranStatusEnum.OK.equals(tran.getTranStatus())) {
                            tran.setTranStatus(TranStatusEnum.COMPLETE)
                            LOGGER.debug("Unit :: "+unit)
                            unit.setFieldValue(MetafieldIdFactory.valueOf("unitFlexString04"),"YES")
                            LOGGER.debug("ITSCompleteDropOffOutGate Code Ends")
                        }else {
                            unit.setFieldValue(MetafieldIdFactory.valueOf("unitFlexString04"),"NO")
                            LOGGER.debug("ITSCompleteDropOffOutGate Code Ends")
                        }
                    }
                }
            }
        }
    }
}
