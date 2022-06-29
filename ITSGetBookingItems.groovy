package ITSIntegration

import com.navis.extension.portal.ExtensionBeanUtils
import com.navis.extension.portal.IExtensionTransactionHandler
import com.navis.external.framework.request.AbstractSimpleRequest
import com.navis.framework.extension.FrameworkExtensionTypes
import com.navis.framework.portal.UserContext
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageLevel
import org.apache.log4j.Logger

class ITSGetBookingItems extends AbstractSimpleRequest {
    @Override
    String execute(UserContext userContext, Map map) {
        LOG.warn("Availability.." + map + "Context $userContext")
        Map input = new HashMap();
        input.putAll(map)
        Map outPut = new HashMap();
        IExtensionTransactionHandler handler = ExtensionBeanUtils.getExtensionTransactionHandler()
        MessageCollector collector = handler.executeInTransaction(userContext, FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, "ITSGetBookingItemsWSCallBack", input, outPut)
        LOG.warn("collector " + collector)

        if (collector != null && collector.containsMessageLevel(MessageLevel.SEVERE)) {
            return collector.getMessages().toString()
        }

        return outPut.get("RESPONSE")
    }
    private static final Logger LOG = Logger.getLogger(this.class)
}
