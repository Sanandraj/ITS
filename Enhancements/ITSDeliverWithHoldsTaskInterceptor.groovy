import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.util.BizViolation
import com.navis.framework.util.TransactionParms
import com.navis.framework.util.internationalization.UserMessage
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageCollectorFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder

/**
 * 08/29/2022 @Author <a href="mailto:uaarthi@weservetech.com">Aarthi U</a>,
 *
 * @Inclusion Location	: Incorporated as a code extension of the type GATE_TASK_INTERCEPTOR.Copy --> Paste this code (ITSDeliverWithHoldsTaskInterceptor.groovy)
 * Called from: Gate task RejectUnitServiceRules at outgate stage for DI transaction type.
 *
 * Gate 4-3 If TMF was applied after ingate, ignore the hold.
 */

class ITSDeliverWithHoldsTaskInterceptor extends AbstractGateTaskInterceptor{
    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        MessageCollector mc = MessageCollectorFactory.createMessageCollector()
        MessageCollector oldMc = getMessageCollector()
        TransactionParms parms = TransactionParms.getBoundParms();
        parms.setMessageCollector(mc)

        executeInternal(inWfCtx)
        mc = getMessageCollector()

        parms.setMessageCollector(oldMc)
        if (mc != null && mc.getMessages(MessageLevel.SEVERE).size() > 0) {
            for (UserMessage userMessage : mc.getMessages()) {
                if (userMessage.getParms() != null) {
                    Object[] params = userMessage.getParms()
                    if(params != null && params[0] != null && params[0].toString().startsWith("TMF")){
                        continue
                    } else {
                        RoadBizUtil.appendExceptionChain(BizViolation.create(userMessage))
                    }


                }
                RoadBizUtil.appendExceptionChain(BizViolation.create(userMessage))
            }
        }
    }
}
