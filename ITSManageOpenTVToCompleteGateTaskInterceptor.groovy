package ITSIntegration

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.presentation.ui.message.OptionDialog
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.road.business.atoms.TruckVisitStatusEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger

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
                tran.cancelTransaction()
                tvd.setTvdtlsStatus(TruckVisitStatusEnum.CLOSED)
                OptionDialog.showWarning(PropertyKeyFactory.valueOf("Open Status for Truck Id ${tvd.getTruckLicenseNbr()} has closed"),PropertyKeyFactory.valueOf("Open TruckVisit Detail"))
            }
        }
    }
    private static Logger LOGGER = Logger.getLogger(ITSManageOpenTVToCompleteGateTaskInterceptor.class)
}