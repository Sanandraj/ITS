package ITSIntegration

import com.navis.extension.portal.ExtensionBeanUtils
import com.navis.extension.portal.IExtensionTransactionHandler
import com.navis.external.framework.beans.EBean
import com.navis.framework.extension.FrameworkExtensionTypes
import com.navis.framework.portal.UserContext
import com.navis.framework.presentation.context.PresentationContextUtils
import com.navis.framework.presentation.context.RequestContext
import com.navis.framework.presentation.util.PresentationConstants
import com.navis.framework.util.message.MessageCollector
import com.navis.road.presentation.controller.CancelTranFormController
import org.apache.log4j.Level
import org.apache.log4j.Logger

class customBeanITSTranCancelFormController extends CancelTranFormController implements EBean {

    private static final String MAP_KEY = "gkeys";
    private static final String TRANSACTION_BUSINESS = "ITSGateTranCancelFormCallback";

    @Override
    protected void submit() {
        LOG.setLevel(Level.DEBUG)
        super.submit();
        LOG.info("Entered customBeanITSTranCancelFormController")
        List<Serializable> tranGkeys = (List<Serializable>) getAttribute(PresentationConstants.SOURCE);
        final IExtensionTransactionHandler handler = ExtensionBeanUtils.getExtensionTransactionHandler();
        RequestContext requestContext = PresentationContextUtils.getRequestContext();
        UserContext context = requestContext.getUserContext();
        Map parms = new HashMap();
        parms.put(MAP_KEY, tranGkeys);

        Map results = new HashMap();
        MessageCollector mc = handler.executeInTransaction(context,
                FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, TRANSACTION_BUSINESS, parms, results);
    }
    private static final Logger LOG = Logger.getLogger(this.class)
}
