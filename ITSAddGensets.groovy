package ITSIntegration

import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.reference.Accessory
import com.navis.argo.business.reference.Chassis
import com.navis.argo.business.reference.Equipment
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.atoms.EqUnitRoleEnum
import com.navis.inventory.business.units.Unit
import com.navis.road.business.atoms.GateClientTypeEnum
import com.navis.road.business.model.Bundle
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * Author: <a href="mailto:smohanbabu@weservetech.com">Mohan Babu</a>
 *
 * Description: This groovy script will attach additional gensets
 */
class ITSAddGensets  extends AbstractGateTaskInterceptor{

    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        LOGGER.setLevel(Level.DEBUG)
        TruckTransaction truckTran = inWfCtx.getTran()
        boolean autoGate = GateClientTypeEnum.AUTOGATE == inWfCtx.getGateClientType()
        if(truckTran != null && autoGate){
            String additionalGensets = truckTran.getTranFlexString04()
            if(additionalGensets != null && StringUtils.isNotEmpty(additionalGensets)){
               String[] gensets = additionalGensets.split(",")
                Unit unit = truckTran.getTranUnit()
                DataSourceEnum currentDataSource = ContextHelper.getThreadDataSource()
                ContextHelper.setThreadDataSource(DataSourceEnum.USER_LCL)
                for (String genset : gensets){
                    Accessory accessory = Accessory.findOrCreateAccessory(genset,"GS", DataSourceEnum.AUTO_GATE)
                    if(accessory != null){

                        Unit attachedEq = unit.attachEquipment(accessory, EqUnitRoleEnum.ACCESSORY_ON_CHS,false,false,true,true)
                        //HibernateApi.getInstance().save(unit)
                    }
                    else{
                        registerError("Cannot find or create accessory-"+genset)
                        break
                    }
                }
                ContextHelper.setThreadDataSource(currentDataSource)
                truckTran.setTranFlexString04(null)
            }
        }
    }

    private static Logger LOGGER = Logger.getLogger(this.class);
}
