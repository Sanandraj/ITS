/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/

package ITSIntegration

import com.navis.extension.portal.ExtensionBeanUtils
import com.navis.extension.portal.IExtensionTransactionHandler
import com.navis.external.framework.request.AbstractSimpleRequest
import com.navis.framework.extension.FrameworkExtensionTypes
import com.navis.framework.portal.UserContext
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageLevel

/*
 *  @Author: mailto:annalakshmig@weservetech.com, Annalakshmi G; Date: 28/12/2021
 *
 *  Requirements: Returns a list of Vessel Schedules arriving between -7 days and +27 days from today in JSON format
 *
 *  @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSGetVesselScheduleWS
 *     Code Extension Type: REQUEST_SIMPLE_READ
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */


class ITSGetVesselScheduleWS extends AbstractSimpleRequest {
    @Override
    String execute(UserContext paramUserContext, Map paramMap) {
        Map input = new HashMap();
        input.putAll(paramMap)
        Map outPut = new HashMap();
        IExtensionTransactionHandler handler = ExtensionBeanUtils.getExtensionTransactionHandler()
        MessageCollector collector = handler.executeInTransaction(paramUserContext, FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, "ITSGetVesselScheduleWSCallback", input, outPut)

        if (collector != null && collector.containsMessageLevel(MessageLevel.SEVERE)) {
            return collector.getMessages().toString()
        }

        return outPut.get("RESPONSE")
    }
}
