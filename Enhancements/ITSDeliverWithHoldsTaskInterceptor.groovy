/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/


import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.util.BizViolation
import com.navis.framework.util.TransactionParms
import com.navis.framework.util.internationalization.UserMessage
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageCollectorFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder

/**
 * @Author : mailto:uaarthi@weservetech.com, Aarthi U; Date: 08/29/2022
 *
 * Requirements: Gate 4-3 N4 validates to see if a TMF hold has been applied for pickup/delivery of a container after the ingate stage, the hold at the outgate stage is ignored.
 *
 * @Inclusion Location	: Incorporated as a code extension of the type.
 *
 * Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSDeliverWithHoldsTaskInterceptor
 *     Code Extension Type: GATE_TASK_INTERCEPTOR
 *     Groovy Code: Copy and paste the contents of groovy code
 *  4. Click Save button.
 *
 * @Setup : Select Transaction type DI → Edit Business task → RejectUnitServiceRules →Actions → Customize → Paste the code name -ITSDeliverWithHoldsTaskInterceptor →Save.
 *
 * S.No    Modified Date   Modified By     Jira      Description
 *
 *
 */

class ITSDeliverWithHoldsTaskInterceptor extends AbstractGateTaskInterceptor {
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
                    if (params != null && params[0] != null && params[0].toString().startsWith("TMF")) {
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

