package ITS

import com.navis.argo.business.reference.EquipType
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.inventory.business.units.Unit
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger
/*
 *
 * @Author <a href="mailto:mmadhavan@weservetech.com"> Madhavan M</a>, 27/Jan/2023
 *
 * Requirements: Appointment without Container Nbr not execute Buz Task.
 *
 * @Inclusion Location	: Incorporated as a code extension of the type GATE_TASK_INTERCEPTOR.
 *
 *  Load Code Extension to N4:
        1. Go to Administration --> System --> Code Extensions
        2. Click Add (+)
        3. Enter the values as below:
            Code Extension Name:  ITSCheckArchTypeInGateTaskInterceptor
            Code Extension Type:  GATE_TASK_INTERCEPTOR
            Groovy Code: Copy and paste the contents of groovy code.
        4. Click Save button
 *
 *  S.No    Modified Date     Modified By     Jira      Description
 *
 */


class ITSCheckArchTypeInGateTaskInterceptor extends AbstractGateTaskInterceptor {
    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        LOGGER.setLevel(Level.DEBUG)
       String apptCtrNbr  =inWfCtx.getTran()?.getTranAppointment()?.getGapptCtrId()
        if(apptCtrNbr == null) {
        EquipmentOrderItem ordrItm = inWfCtx.getTran()?.getTranEqoItem()
            if(ordrItm != null){
        EquipType orderEquipType = ordrItm.getEqoiSampleEquipType()
        if (orderEquipType != null) {
             String itemArchTypeId = orderEquipType?.getEqtypArchetype()?.getEqtypId()
            Unit unit= inWfCtx.getTran().getTranUnit()
            if(unit != null){
                String unitArchTypeId= unit .getPrimaryEq()?.getEqEquipType()?.getEqtypArchetype()?.getEqtypId()
            if(itemArchTypeId != null && unitArchTypeId != null && unitArchTypeId.equals(itemArchTypeId)){
                //Do nothing
            }else{
                executeInternal(inWfCtx)
               }
            }
          }
            }
    }else
        {
            executeInternal(inWfCtx)
        }
    }
    private static Logger LOGGER = Logger.getLogger(ITSCheckArchTypeInGateTaskInterceptor.class)
}
