package ITS.Enhancements


import com.navis.argo.business.api.ArgoUtils
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.inventory.business.units.Unit
import com.navis.services.business.api.EventManager
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import org.apache.log4j.Level
import org.apache.log4j.Logger

/* created by annalakshmig@weservetech.com on 08/Nov/2022
@Inclusion Location    : Incorporated as a code extension of the type GENERAL_NOTICES_CODE_EXTENSION --> Paste this code (ITSRecordDwellEventGenNotice.groovy)
* Configured against UNIT_DISCH
* */

class ITSRecordDwellEventGenNotice extends AbstractGeneralNoticeCodeExtension {
    @Override
    void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.DEBUG)
        // LOGGER.debug("ITSRecordDwellEventGenNotice started")
        Unit unit = (Unit) inGroovyEvent.getEntity();
        Event event = inGroovyEvent.getEvent()
        if (event == null || unit == null) {
            return
        }
        EventType unitExtendedDwell = EventType.findEventType("UNIT_EXTENDED_DWELL")
        if (unitExtendedDwell != null) {
            EventManager eventManager = (EventManager) Roastery.getBean(EventManager.BEAN_ID)
            Event dwellEvent = eventManager.getMostRecentEventByType(unitExtendedDwell, unit);
            if (dwellEvent == null) {
                unit.recordEvent(unitExtendedDwell, null, "Recorded by Groovy", ArgoUtils.timeNow())
            }
        }
    }
    private static final Logger LOGGER = Logger.getLogger(this.class);
}

