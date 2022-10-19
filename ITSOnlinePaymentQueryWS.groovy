package ITS


import com.navis.extension.portal.ExtensionBeanUtils
import com.navis.extension.portal.IExtensionTransactionHandler
import com.navis.external.framework.request.AbstractSimpleRequest
import com.navis.framework.extension.FrameworkExtensionTypes
import com.navis.framework.portal.UserContext
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageLevel
import org.apache.log4j.Logger

/**
 * Created by annalakshmig@weservetech.com on 10-OCT-2022
 */
class ITSOnlinePaymentQueryWS extends AbstractSimpleRequest {
    @Override
    String execute(UserContext paramUserContext, Map paramMap) {

        LOG.warn("Availability.." + paramMap + "Context $paramUserContext")
        Map input = new HashMap();
        input.putAll(paramMap)
        Map outPut = new HashMap();
        IExtensionTransactionHandler handler = ExtensionBeanUtils.getExtensionTransactionHandler()
        MessageCollector collector = handler.executeInTransaction(paramUserContext, FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, "ITSOnlinePaymentQueryWSCallback", input, outPut)
        LOG.warn("collector " + collector)

        if (collector != null && collector.containsMessageLevel(MessageLevel.SEVERE)) {
            return collector.getMessages().toString()
        }

        return outPut.get("RESPONSE")
    }
    private static final Logger LOG = Logger.getLogger(this.class)
}