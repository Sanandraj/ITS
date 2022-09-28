package ITSIntegration

import com.navis.argo.business.reference.Chassis
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils
/**
 * Author: <a href="mailto:smohanbabu@weservetech.com">Mohan Babu</a>
 *
 * Description: This groovy script will ignore chassis number required validation if equipment order number is passed
 */
class ITSRejectChassisNumberReqdIngate extends AbstractGateTaskInterceptor{

    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        TruckTransaction tran = inWfCtx.getTran()
        if(tran != null){
            EquipmentOrder order = tran.getTranEqo()
            if(order == null || StringUtils.isBlank(order.getEqboNbr())){
                executeInternal(inWfCtx)
            }
        }
    }
}
