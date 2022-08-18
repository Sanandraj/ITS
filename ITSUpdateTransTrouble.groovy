package ITSIntegration

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils

/**
 * Author: <a href="mailto:smohanbabu@weservetech.com">Mohan Babu</a>
 *
 * Description: This groovy script will set transaction to trouble manually/forcibly
 */

class ITSUpdateTransTrouble  extends AbstractGateTaskInterceptor{

    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        TruckTransaction truckTrans = inWfCtx.getTran();
        if(truckTrans != null){
            String troubleFlag = truckTrans.getTranFlexString03();
            if(StringUtils.equalsIgnoreCase(troubleFlag,"Y")){
                registerError("Forced to trouble");
                truckTrans.setTranFlexString03(null);
            }
        }
        //super.execute(inWfCtx)
    }

}
