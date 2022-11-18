/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.cargo.business.model.BillOfLading
import com.navis.cargo.business.model.GoodsBl
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.inventory.business.units.Unit
import com.navis.road.business.adaptor.unit.RejectPinNbrNotAssigned
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger

/**
 *
 * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 16/July/2022
 *
 * Requirements : This groovy is used to validate the PIN Nbr entered in Appt with the associated Unit.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *   1. Go to Administration --> System -->  Code Extension
 *   2. Click Add (+)
 *   3. Enter the values as below:
 Code Extension Name:  ITSPinValidationGateInterceptor
 Code Extension Type:  GATE_TASK_INTERCEPTOR
 Groovy Code: Copy and paste the contents of groovy code.
 *   4. Click Save button
 *
 * @Set up groovy code in one of the business task then execute this code extension (ITSPinValidationGateInterceptor).
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */

class ITSPinValidationGateInterceptor extends AbstractGateTaskInterceptor implements EGateTaskInterceptor {

    @Override
    public void execute(TransactionAndVisitHolder inWfCtx) {
        TruckTransaction tran = inWfCtx.getTran();
        boolean executeTask = true;
        String pinNbr = null;
        if (tran == null) {
            return
        }
        if (tran.getTranGroupId() != null) {
            executeTask = false;
        }
        pinNbr = tran.getTranPinNbr()
        Unit unit = tran.getTranUnit();
        if (!StringUtils.isBlank(pinNbr) && pinNbr.length() == 5) {
            if (unit != null) {
                GoodsBl goodsBl = GoodsBl.resolveGoodsBlFromGoodsBase(unit.getUnitGoods());
                Set<BillOfLading> blSet = new HashSet<>()
                if (goodsBl != null) {
                    blSet = goodsBl.getGdsblBillsOfLading();
                    if (blSet != null && !blSet.isEmpty() && blSet.size() >= 1) {
                        Iterator iterator = blSet.iterator();
                        while (iterator.hasNext()) {
                            BillOfLading bl = (BillOfLading) iterator.next();
                            if (bl != null && (bl.getBlNbr().endsWith(pinNbr))) {
                                executeTask = false;
                            }
                        }
                    }
                }
            }
        }
        if (executeTask) {
            if (unit != null) {
                pinNbr = unit.getUnitRouting() != null ? unit.getUnitRouting().getRtgPinNbr() : null;
                if (pinNbr == null) {
                    new RejectPinNbrNotAssigned().execute(inWfCtx);
                }
            }
            executeInternal(inWfCtx);
        }
    }
    private static Logger LOGGER = Logger.getLogger(ITSPinValidationGateInterceptor.class)
}
