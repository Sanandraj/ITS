package ITSIntegration

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.persistence.HibernateApi
import com.navis.road.business.appointment.model.TruckVisitAppointment
import com.navis.road.business.model.TruckCompanyDriver
import com.navis.road.business.model.TruckDriver
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.model.TruckingCompany
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

class ITSUpdateTruckingCompany extends AbstractGateTaskInterceptor{
    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        TruckTransaction tran = inWfCtx.getTran()
        TruckVisitDetails tvd = inWfCtx.getTv()
        _logger.setLevel(Level.DEBUG)
        _logger.debug("TV appointment number - " + tvd.getTvdtlsTvAppointmentNbr().toString())
        _logger.debug("TV GOS number - " + tvd.getTvdtlsGosTvKey().toString())

        if (tvd != null){
            TruckDriver driver = TruckDriver.findDriverByLicNbr(tvd.getTvdtlsDriverLicenseNbr())
            _logger.debug("Trucking Company - " + (StringUtils.isBlank(tvd.getTvdtlsTruckingCoId())  ? "" : tvd.getTvdtlsTruckingCoId()))
            if(StringUtils.isBlank(tvd.getTvdtlsTruckingCoId())){
                _logger.debug("Inside trucking company blank")
                TruckVisitAppointment tvAppt = tvd.getTvdtlsTruckVisitAppointment()
                if(tvAppt != null && tvAppt.getTruckingCompany() != null){
                    //tvd.setTvdtlsTruckingCoId(tvAppt.getTruckingCompany().getBzuId())
                    tvd.setTvdtlsTrkCompany(tvAppt.getTruckingCompany())
                    //HibernateApi.getInstance().save(tvd)
                    if(driver != null && tvAppt.getTruckingCompany().getBzuId() != driver.getDriverFlexString01()){
                        _logger.debug("Inside tv save")
                        driver.setDriverFlexString01(tvAppt.getTruckingCompany().getBzuId())
                        HibernateApi.getInstance().save(driver)
                    }
                }
                else if(driver != null && StringUtils.isNotBlank(driver.getDriverFlexString01())){
                    String truckerCode = driver.getDriverFlexString01()
                    TruckingCompany trkCo = TruckingCompany.findTruckingCompany(truckerCode)
                    if(trkCo != null){
                        //tvd.setTvdtlsTruckingCoId(truckerCode)
                        tvd.setTvdtlsTrkCompany(trkCo)
                        //HibernateApi.getInstance().save(tvd)
                    }
                    if(tran != null && tran.getTranTruckingCompany() == null){
                        tran.setTranTruckingCompany(trkCo)
                        tran.setTranTrkcId(truckerCode)
                    }
                }
             }
            else if(driver != null && tvd.getTvdtlsTruckingCoId() != driver.getDriverFlexString01()){
                _logger.debug("Inside save")
                driver.setDriverFlexString01(tvd.getTvdtlsTruckingCoId())
                HibernateApi.getInstance().save(driver)
            }
        }
        executeInternal(inWfCtx)
    }

    private static Logger _logger = Logger.getLogger(this.class);
}
