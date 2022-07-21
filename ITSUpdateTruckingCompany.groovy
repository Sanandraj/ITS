package ITSIntegration

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.persistence.HibernateApi
import com.navis.road.business.model.TruckCompanyDriver
import com.navis.road.business.model.TruckDriver
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckingCompany
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger

class ITSUpdateTruckingCompany extends AbstractGateTaskInterceptor{
    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        TruckTransaction tran = inWfCtx.getTran()
        if (tran != null){
            TruckDriver driver = TruckDriver.findDriverByLicNbr(tran.getTranTruckVisit().getTvdtlsDriverLicenseNbr())
            if(tran.getTranTruckingCompany() == null){
                if(driver != null && StringUtils.isNotBlank(driver.getDriverFlexString01())){
                    String truckerCode = driver.getDriverFlexString01()
                    TruckingCompany trkCo = TruckingCompany.findTruckingCompany(truckerCode)
                    if(trkCo != null){
                        tran.getTranTruckVisit().setTvdtlsTruckingCoId(truckerCode)
                        tran.setTranTruckingCompany(trkCo)
                        tran.setTranTrkcId(truckerCode)
                    }
                }
             }
            else if(driver != null && tran.getTranTruckingCompany().getBzuId() != driver.getDriverFlexString01()){
                driver.setDriverFlexString01(tran.getTranTruckingCompany().getBzuId())
            }
        }
        super.execute(inWfCtx)
    }
    private static Logger _logger = Logger.getLogger(this.class);
}
