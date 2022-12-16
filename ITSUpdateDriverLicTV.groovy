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
import com.navis.road.business.util.RoadBizUtil
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

        if(tvWS != null && StringUtils.isBlank(tvWS.getTvdtlsDriverLicenseNbr())) {
            LOGGER.debug("ITSUpdateDriverLicTV - Inside if condition")
            String flexStringDriverLicense = tvWS.getTvdtlsFlexString01()
            if(StringUtils.isNotBlank(flexStringDriverLicense)){
                LOGGER.debug("ITSUpdateDriverLicTV - flexStringDriverLicense is not blank")
                tvWS.setTvdtlsDriverLicenseNbr(flexStringDriverLicense)
            }
        }
        executeInternal(inWfCtx)

    }


    private static final Logger LOGGER = Logger.getLogger(this.class)
}
