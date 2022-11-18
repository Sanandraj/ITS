/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.road.business.workflow.TransactionAndVisitHolder

/**
 *
 * @Author <a href="mailto:mmadhavan@weservetech.com">Madhavan M</a>, 10/OCT/2022
 *
 * Requirements : 1. VGM should be applied to the export container  at the time it is successfully processed at the in-gate if VERMAS EDI has been received prior to container arrival.
                  2. If no VGM has been received for the container when it arrives at the in-gate, then the scale weight is applied as the VGM.
                  3. When the VERMAS is received, this scale weight is replaced with the VERMAS weight.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *   1. Go to Administration --> System -->  Code Extension
 *   2. Click Add (+)
 *   3. Enter the values as below:
 Code Extension Name:  ITSSetVGMWeightUpdateTaskInterceptor
 Code Extension Type:  GATE_TASK_INTERCEPTOR
 Groovy Code: Copy and paste the contents of groovy code.
 *   4. Click Save button
 *
 * @Set up groovy code in one of the business task then execute this code extension (ITSSetVGMWeightUpdateTaskInterceptor).
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */

class ITSSetVGMWeightUpdateTaskInterceptor extends AbstractGateTaskInterceptor {

    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        super.execute(inWfCtx)

        if(inWfCtx.getTran()== null){
            return;
        }

        if(inWfCtx.getTran().getTranCtrVGMWeight()==null){
            inWfCtx.getTran().setTranCtrVGMWeight(inWfCtx.getTran().getTranScaleWeight())
            inWfCtx.getTran().getTranUnit().updateVerifiedGrossMassWtKg(inWfCtx.getTran().getTranScaleWeight())
        }
    }
}
