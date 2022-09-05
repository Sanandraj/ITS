package ITSIntegration

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.util.DateUtil
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.internationalization.PropertyKeyImpl
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageLevel
import com.navis.road.RoadPropertyKeys
import com.navis.road.business.atoms.TruckVisitStatusEnum
import com.navis.road.business.model.TruckDriver
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
/**
 * Author: <a href="mailto:smohanbabu@weservetech.com">Mohan Babu</a>
 *
 * Description: This groovy script will set driver license number in truck visit
 */
class ITSUpdateDriverLicTV extends AbstractGateTaskInterceptor{

    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        LOGGER.setLevel(Level.DEBUG)
        TruckVisitDetails tvWS = inWfCtx.getTv()
        if(tvWS != null){
            tvWS.setTvdtlsFlexString01(null)
            String driverLicense = tvWS.getTvdtlsFlexString01()
            Boolean hasTruckVisit = false
            if(StringUtils.isNotBlank(driverLicense)){
                LOGGER.info("Driver Lic. - " + driverLicense)
                List<TruckVisitDetails> tvList = (List<TruckVisitDetails>)TruckVisitDetails.findTVActiveByGosTvKey(tvWS.getTvdtlsGosTvKey())
                if(tvList != null && !tvList.isEmpty()){
                    LOGGER.info("Truck visit list found for gos tv key - " + tvWS.getTvdtlsGosTvKey())
                    for(TruckVisitDetails tvd in tvList){
                        if((tvd.getTvdtlsStatus() == TruckVisitStatusEnum.OK || tvd.getTvdtlsStatus() == TruckVisitStatusEnum.TROUBLE) && tvd.getTruckLicenseNbr() == tvWS.getTruckLicenseNbr()){
                            hasTruckVisit = true
                            if(StringUtils.isBlank(tvd.getTvdtlsDriverLicenseNbr())){
                                LOGGER.info("Driver Lic is null/empty")
                                TruckDriver driver = TruckDriver.findDriverByLicNbr(driverLicense)
                                boolean isValidDriver = validateDriver(driver, driverLicense)
                                if(isValidDriver){
                                    LOGGER.info("Found driver with lic. - " + driverLicense)
                                    tvWS.setTvdtlsDriver(driver)
                                    HibernateApi.getInstance().save(tvWS)
                                    LOGGER.info("Driver details saved")
                                }
                            }
                            break
                        }
                    }
                }
                tvWS.setTvdtlsFlexString01(null)
            }
        }
        super.execute(inWfCtx)
    }

    boolean validateDriver(TruckDriver driver, String driverLicense){
        MessageCollector mc = getMessageCollector()
        Object[] param = new Object[1]
        param[0] = driverLicense
        if(driver == null){
            PropertyKey unKnownDriver = PropertyKeyFactory.valueOf(RoadPropertyKeys.GATE__DRIVER_UNKNOWN_LICENSE_NBR)
            mc.appendMessage(MessageLevel.SEVERE, unKnownDriver,null,param)
            return false
        }
        if(driver.isTruckDriverBanned()){
            PropertyKey bannedDriver = PropertyKeyFactory.valueOf(RoadPropertyKeys.GATE__DRIVER_BANNED)
            mc.appendMessage(MessageLevel.SEVERE, bannedDriver,null,param)
            return false
        }
        Date rightNow = DateUtil.getDSTSafeCalendarTime(Calendar.getInstance())
        if(driver.getDriverCardExpiration().before(rightNow)){
            PropertyKey driverCardExpired = PropertyKeyFactory.valueOf(RoadPropertyKeys.GATE__DRIVER_CARD_EXPIRED)
            mc.appendMessage(MessageLevel.SEVERE, driverCardExpired,null,param)
            return false
        }
        return true
    }

    private static final Logger LOGGER = Logger.getLogger(this.class)
}
