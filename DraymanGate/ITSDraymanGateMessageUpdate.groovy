package DraymanGate

import com.navis.argo.ContextHelper
import com.navis.external.framework.util.ExtensionUtils
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @author <a href="mailto:sramasamy@weservetech.com"> Ramasamy Sathappan</a>
 * @since 18-May-2022
 *
 * Configure under General Notice tab against MOVE_TV event
 *
 * */
// not in use - as MOVE_TV is not recording at InGate processing
class ITSDraymanGateMessageUpdate extends AbstractGeneralNoticeCodeExtension {

    @Override
    void execute(GroovyEvent inGroovyEvent) {
        try {
            LOGGER.setLevel(Level.DEBUG)
            LOGGER.debug("ITSDraymanGateMessageUpdate BEGIN - MOVE_TV event")
            def library = ExtensionUtils.getLibrary(ContextHelper.getThreadUserContext(), LIBRARY);
            if (library)
                library.execute(inGroovyEvent.getEntity());
            LOGGER.debug("ITSDraymanGateMessageUpdate END")

        } catch (Exception e) {
            LOGGER.error("Exception in ITSDraymanGateMessageUpdate : " + e.getMessage());
        }
    }

    private static final String LIBRARY = "ITSDraymanGateAdaptor";
    private static final Logger LOGGER = Logger.getLogger(ITSDraymanGateMessageUpdate.class);
}
