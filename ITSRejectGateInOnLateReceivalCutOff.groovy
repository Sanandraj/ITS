/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/


import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.model.CarrierVisit
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author <a href="mailto:skishore@weservetech.com">Kishore Kumar S</a>
 *  Description : Validating Late Receival Cut-Off date from Vessel Visit details .
 *
 *
 * @Author: mailto:skishore@weservetech.com, Kishore S; Date: 26/09/2022
 *
 *  Requirements: Validating Late Receival Cut-Off date from Vessel Visit details
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSRejectGateInOnLateReceivalCutOff
 *     Code Extension Type: GATE_TASK_INTERCEPTOR
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup Select Transaction type RE → Edit Business task → RejectCarrierVisitPastCutoff →Actions → Customize → Paste the code name -ITSRejectGateInOnLateReceivalCutOff →Save.
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */

class ITSRejectGateInOnLateReceivalCutOff extends AbstractGateTaskInterceptor {
    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSRejectGateInOnLateReceivalCutOff STARTS::")
        TruckTransaction tran = inWfCtx.getTran()
        if (tran.getTranAppointment() != null && tran.getTranAppointment().getGapptVesselVisit() != null) {
            Serializable cvGkey = tran.getTranAppointment().getGapptVesselVisit().getCvGkey()
            String cv = CarrierVisit.hydrate(cvGkey).getCvId()
            LOGGER.debug("vesselVisitDetails :: " + cv)
            VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(tran.getTranAppointment().getGapptVesselVisit())
            TimeZone timeZone = ContextHelper.getThreadUserTimezone()
            if (vvd.getVvFlexDate01() == null || (ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone)).after(vvd.getVvFlexDate01())) {
                executeInternal(inWfCtx)
            }
        }
    }
    private static Logger LOGGER = Logger.getLogger(ITSRejectGateInOnLateReceivalCutOff.class)
}
