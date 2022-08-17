import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckTransactionStage
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
* @Author <a href="mailto:skishore@weservetech.com">KISHORE KUMAR S</a>
* Date: 19/04/2022
* Requirements:- Appointment validation should look at Visit Start time if Driver is late at the ingate
* @Inclusion Location	: Incorporated as a code extension of the type GATE_TASK_INTERCEPTOR --> Paste this code (ITSApptmntGateInApprovalGateTaskInterceptor.groovy)
*/


class ITSApptmntGateInApprovalGateTaskInterceptor extends AbstractGateTaskInterceptor{

    void execute(TransactionAndVisitHolder inWfCtx){
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug( "ITSApptmntGateInApprovalGateTaskInterceptor STARTS::")
        TruckTransaction tran = inWfCtx.getTran()
        TruckVisitDetails truckDetails = inWfCtx.getTv()
        TruckTransactionStage truckVisitDetailsList= truckDetails.findTruckVisitsTranStages().get(0)
        long timeStart= truckVisitDetailsList.getTtstageStart().time
        LOGGER.debug("timeStart ::"+timeStart)
        Date timeStartDate=new Date(timeStart)
        LOGGER.debug("timeStartDate :: "+timeStartDate)
        String dateStringFormat =  timeStartDate.format("yyyy-MM-dd hh:mm").toString()
        LOGGER.debug("dateStringFormat :: "+dateStringFormat)
        if (tran.getTranAppointment() != null){
            LOGGER.debug("Gappt Time Slot booked ::"+tran.getTranAppointment().getGapptTimeSlot())
            boolean driverIsLate = tran.getTranAppointment().getGapptTimeSlot().isLate(timeStartDate)
            LOGGER.debug("Is Driver Late:::"+driverIsLate)
            if (!driverIsLate){
                LOGGER.debug("***** In correctTime Loop *****")
                tran.recordStageCompleted("ingate")
                LOGGER.debug("*** Saved Transaction ***")
            }
            else {
                LOGGER.debug("***** In lateTime Loop *****")
                tran.cancelTransaction()
                getMessageCollector().appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("Driver is Late to the Terminal against fixed appointment slot"),"Please contact Administration")
            }
        }
    }
    private static Logger LOGGER = Logger.getLogger(ITSApptmntGateInApprovalGateTaskInterceptor.class)
}
