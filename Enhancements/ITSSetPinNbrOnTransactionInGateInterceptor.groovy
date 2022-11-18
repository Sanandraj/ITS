/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.road.business.atoms.TruckerFriendlyTranSubTypeEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Logger

/**
 *
 * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 16/July/2022
 *
 * Requirements : This groovy is used to update PIN number in transaction, by fetching it from ApptFlexString 01
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *   1. Go to Administration --> System -->  Code Extension
 *   2. Click Add (+)
 *   3. Enter the values as below:
 Code Extension Name:  ITSSetPinNbrOnTransactionInGateInterceptor
 Code Extension Type:  GATE_TASK_INTERCEPTOR
 Groovy Code: Copy and paste the contents of groovy code.
 *   4. Click Save button
 *
 * @Set up groovy code in one of the business task then execute this code extension (ITSSetPinNbrOnTransactionInGateInterceptor).
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */


class ITSSetPinNbrOnTransactionInGateInterceptor extends AbstractGateTaskInterceptor implements EGateTaskInterceptor {

    private static Logger LOGGER = Logger.getLogger(ITSSetPinNbrOnTransactionInGateInterceptor.class)

    @Override
    public void execute(TransactionAndVisitHolder inWfCtx) {
        TruckTransaction tran = inWfCtx.getTran();
        boolean executeDefaultTask = true;
        if (tran != null && TruckerFriendlyTranSubTypeEnum.PUI.equals(tran.getTranTruckerTranSubType())) {

            String tranPinNbr = tran.getTranPinNbr();
            if (tranPinNbr == null) {
                tran.setTranPinNbr(tran.getTranAppointment() != null ? tran.getTranAppointment().getGapptUnitFlexString01() : null);
                executeDefaultTask = true;
            } else if (tranPinNbr != null && !tranPinNbr.equalsIgnoreCase(tran.getTranAppointment() != null ? tran.getTranAppointment().getGapptUnitFlexString01() : null)) {
                executeDefaultTask = false;
            }
        }
        if (executeDefaultTask) {
            executeInternal(inWfCtx);
        }
    }

}
