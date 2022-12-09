/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.inventory.business.units.Unit
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.atoms.TransactionClassEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Logger

/**
 *
 * @Author <a href="mailto:skishore@weservetech.com">KISHORE KUMAR S</a>
 *
 * Requirements : Gate 4-23 Complete Drop-off at out-gate for Dual Transaction
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *   1. Go to Administration --> System -->  Code Extension
 *   2. Click Add (+)
 *   3. Enter the values as below:
 Code Extension Name:  ITSCompleteDropOffOutGate
 Code Extension Type:  GATE_TASK_INTERCEPTOR
 Groovy Code: Copy and paste the contents of groovy code.
 *   4. Click Save button
 *
 * @Set up groovy code in one of the business task then execute this code extension (ITSCompleteDropOffOutGate).
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */
class ITSCompleteDropOffOutGate extends AbstractGateTaskInterceptor {
    private static Logger LOGGER = Logger.getLogger(ITSCompleteDropOffOutGate.class)

    void execute(TransactionAndVisitHolder inWfCtx) {
        TruckTransaction truckTransaction = inWfCtx.getTran()
        if (truckTransaction != null && truckTransaction?.isDelivery()) {
            TruckVisitDetails truckVisitDetails = truckTransaction?.getTranTruckVisit()
            if (truckVisitDetails != null) {
                Set<TruckTransaction> dropOffContainers = truckVisitDetails?.getTransactionsToBeHandled(TransactionClassEnum.DROPOFF)
                for (TruckTransaction tran : dropOffContainers) {
                    Unit unit = tran?.getTranUnit()
                    if (unit != null) {
                        if (TranStatusEnum.OK.equals(tran?.getTranStatus())) {
                            tran.setTranStatus(TranStatusEnum.COMPLETE)
                            unit.setFieldValue(MetafieldIdFactory.valueOf("unitFlexString04"), "YES")
                        } else {
                            unit.setFieldValue(MetafieldIdFactory.valueOf("unitFlexString04"), "NO")
                        }
                    }
                }
            }
        }
    }
}
