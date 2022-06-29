package ITSIntegration

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.inventory.business.units.UnitEquipDamages
import com.navis.road.business.atoms.GateClientTypeEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * Author: <a href="mailto:uaarthi@weservetech.com">Mohan Babu</a>
 *
 * Description: This groovy script will set container damaged flag
 */
class ITSUpdateDmg extends AbstractGateTaskInterceptor{

    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        TruckTransaction truckTran = inWfCtx.getTran()
        boolean autoGate = GateClientTypeEnum.AUTOGATE.equals(inWfCtx.getGateClientType())
        if(truckTran != null && autoGate){
            String containerDmgFlg = truckTran.getTranFlexString01()
            if(StringUtils.equalsIgnoreCase(containerDmgFlg, "Y")){
                truckTran.setTranCtrIsDamaged(true)
                truckTran.setTranFlexString01(null)
            }
            String chsDmgFlg = truckTran.getTranFlexString02()
            if(StringUtils.equalsIgnoreCase("Y", chsDmgFlg)){
                truckTran.setTranChsIsDamaged(true)
                truckTran.setTranFlexString02(null)
            }
        }
        //super.execute(inWfCtx)
    }
    private static Logger LOGGER = Logger.getLogger(this.class);

}
