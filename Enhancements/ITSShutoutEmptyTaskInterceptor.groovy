/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.Container
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Logger

/**
 *
 * @Author: mailto:uaarthi@weservetech.com, Aarthi U; Date:15/09/2022
 *
 * Requirements : Back 8-5 Shutout Empties based on Line./Arch ISO
 *
 * @Inclusion Location	: Incorporated as a code extension of the type GATE_TASK_INTERCEPTOR.
 *
 *  Load Code Extension to N4:
 1. Go to Administration --> System -->  Code Extension
 2. Click Add (+)
 3. Enter the values as below:
 Code Extension Name:  ITSShutoutEmptyTaskInterceptor
 Code Extension Type:  GATE_TASK_INTERCEPTOR
 Groovy Code: Copy and paste the contents of groovy code.
 4. Click Save button

 @Set up groovy code in Gate Configuration for RM Transaction type at Ingate
  *  S.No    Modified Date   Modified By     Jira      Description
  *
 */

class ITSShutoutEmptyTaskInterceptor extends AbstractGateTaskInterceptor implements EGateTaskInterceptor {
    private PropertyKey MTY_REFUSED = PropertyKeyFactory.valueOf("MTY_REFUSED")
    private static Logger LOGGER = Logger.getLogger(this.class);

    void execute(TransactionAndVisitHolder inWfCtx) {
        executeInternal(inWfCtx);
        TruckTransaction truckTransaction = inWfCtx.getTran()
        if (truckTransaction != null) {
            blockMtyReceive(truckTransaction);
        }
    }

    void blockMtyReceive(tran) {
        Container container = tran.getTranContainer()
        String lineOp = null;
        String iso = null
        if (tran.getTranLine() != null) {
            lineOp = tran.getTranLine().getBzuId()
        } else if (container != null) {
            lineOp = container.getEquipmentOperatorId()
        }
        if (tran.getTranEquipType() != null && tran.getTranEquipType().getEqtypArchetype() != null) {
            iso = tran.getTranEquipType().getEqtypArchetype().getEqtypId()
        } else if (container != null && container.getEqEquipType() != null) {
            iso = container.getEqEquipType().getEqtypArchetype().getEqtypId()
        }

        if (iso != null && lineOp != null) {
            GeneralReference generalReference = GeneralReference.findUniqueEntryById("MTY_QUOTA", "RCV_EMPTIES", lineOp, iso)

            boolean throwError = false;

            if (generalReference == null) {
                throwError = true
            }
            if (generalReference != null && "NO".equalsIgnoreCase(generalReference.getRefValue1())) {
                throwError = true
            }

            if (throwError) {
                Object[] params = new Object[2];
                params[0] = lineOp;
                params[1] = iso;
                getMessageCollector().appendMessage(MessageLevel.SEVERE, MTY_REFUSED, "Empty Container for Line ${lineOp} and ISO ${iso} not allowed.", params)
            }
        }
    }
}
