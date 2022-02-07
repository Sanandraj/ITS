
package ITSIntegration
/*
 * Copyright (c) 2018 WeServe LLC. All Rights Reserved.
 *
 */
import com.navis.extension.portal.ExtensionBeanUtils
import com.navis.extension.portal.IExtensionTransactionHandler
import com.navis.external.framework.request.AbstractSimpleRequest
import com.navis.framework.extension.FrameworkExtensionTypes
import com.navis.framework.portal.UserContext
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageLevel
import org.apache.log4j.Logger

/*
 * @Author <a href="mailto:annalakshmig@weservetech.com">ANNALAKSHMI G</a>
 * Date: 19/01/2022
 * Requirements:- Receives one or more Container Number(s) via Http Get & Returns Container Event Histories in JSON format
 *  @Inclusion Location	: Incorporated as a code extension of the type REQUEST_SIMPLE_READ --> Paste this code (ITSGetContainerEventHistoryWS.groovy)
 */


class ITSGetContainerEventHistoryWS extends AbstractSimpleRequest {
    @Override
    String execute(UserContext paramUserContext, Map paramMap) {
        LOG.warn("Availability.." + paramMap + "Context $paramUserContext")
        Map input = new HashMap();
        input.putAll(paramMap)
        Map outPut = new HashMap();
        IExtensionTransactionHandler handler = ExtensionBeanUtils.getExtensionTransactionHandler()
        MessageCollector collector = handler.executeInTransaction(paramUserContext, FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, "ITSGetContainerEventHistoryWSCallback", input, outPut)
        LOG.warn("collector " + collector)

        if (collector != null && collector.containsMessageLevel(MessageLevel.SEVERE)) {
            return collector.getMessages().toString()
        }

        return outPut.get("RESPONSE")
    }

    private static final Logger LOG = Logger.getLogger(this.class)
}
