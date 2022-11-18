/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.road.business.appointment.model.GateAppointment
import com.navis.road.business.atoms.TruckerFriendlyTranSubTypeEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Logger

/**
 *
 * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 16/July/2022
 *
 * Requirements : This groovy is used to update PIN number from Appointment to GapptUnitFlexString01 for Pick Up Imports
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *   1. Go to Administration --> System -->  Code Extension
 *   2. Click Add (+)
 *   3. Enter the values as below:
 Code Extension Name:  ITSSetApptPinNumber
 Code Extension Type:  GATE_TASK_INTERCEPTOR
 Groovy Code: Copy and paste the contents of groovy code.
 *   4. Click Save button
 *
 * @Set up groovy code in one of the business task then execute this code extension (ITSSetApptPinNumber).
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */

class ITSSetApptPinNumber extends AbstractGateTaskInterceptor {
    void execute(TransactionAndVisitHolder inWfCTx) {
        GateAppointment appointment = inWfCTx.getAppt();
        if (appointment != null && TruckerFriendlyTranSubTypeEnum.PUI.equals(appointment.getGapptTranType())) {
            TruckTransaction tran = inWfCTx.getTran();
            if (tran != null) {
                String tranPinNbr = tran.getTranPinNbr();
                if (appointment != null && tranPinNbr != null) {
                    appointment.setGapptUnitFlexString01(tranPinNbr);
                }
            }
        }
    }
    private static  Logger LOG = Logger.getLogger(this.class);
}