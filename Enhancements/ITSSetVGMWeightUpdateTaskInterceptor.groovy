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
 * @Inclusion Location	: Code Extension Incorporated as a code extension type GATE_TASK_INTERCEPTOR. and configure in the Business Task(RejectDuplicateTransaction)
 *                        Paste this code (ITSSetVGMWeightUpdateTaskInterceptor.groovy)
 *
 *
 */

class ITSSetVGMWeightUpdateTaskInterceptor extends AbstractGateTaskInterceptor {

    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        super.execute(inWfCtx)
        if(inWfCtx.getTran().getTranCtrVGMWeight()==null){
            inWfCtx.getTran().setTranCtrVGMWeight(inWfCtx.getTran().getTranScaleWeight())
            inWfCtx.getTran().getTranUnit().updateVerifiedGrossMassWtKg(inWfCtx.getTran().getTranScaleWeight())
        }
    }
}
