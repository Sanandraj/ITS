package ITSIntegration

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.internationalization.UserMessage
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageLevel
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger

class ITSTurnAroundHolds extends AbstractGateTaskInterceptor{

    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        super.execute(inWfCtx)
    }

    @Override
    void postProcessError(TransactionAndVisitHolder inWfCtx) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.info("Entered post process error method")
        MessageCollector collector = getMessageCollector()
        List<UserMessage> errorMessages = collector.getMessages()
        if(errorMessages != null && !errorMessages.isEmpty()){
            LOGGER.info("Contains error messages")
            for(UserMessage um in errorMessages){
                String message = um.toString()
                boolean tmfHoldExists = message.contains("Hold TMF BKG exists")
                LOGGER.info(message)
                if(tmfHoldExists){
                    LOGGER.info("Contains tmf hold")
                    PropertyKey key = PropertyKeyFactory.valueOf("gate.export_booking_tmf_hold")
                    collector.appendMessage(MessageLevel.SEVERE, key,"",null)
                    LOGGER.info("TMF hold message appended")
                    break
                }
            }
        }

    }

    private static final Logger LOGGER = Logger.getLogger(this.class)
}
