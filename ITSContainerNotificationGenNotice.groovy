package ITS

import com.navis.argo.business.atoms.EventEnum
import com.navis.external.framework.util.ExtensionUtils
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.inventory.business.units.Unit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
**/
class ITSContainerNotificationGenNotice extends AbstractGeneralNoticeCodeExtension {

    @Override
    void execute(GroovyEvent inGroovyEvent) {

        logger.setLevel(Level.DEBUG)
        logger.debug("Started Executing");
        if (inGroovyEvent != null) {
            Unit inUnit = (Unit) inGroovyEvent.getEntity();
            Event event = inGroovyEvent.getEvent()
            boolean isCtrNotification = true
            logger.debug("Log inUnit " + inUnit);
            if (inUnit == null || event == null) {
                logger.debug(" Unit or event is Null returning !!!")
                return
            }
           if(library != null) {
                if (EventEnum.UNIT_ENABLE_ROAD.getKey().equalsIgnoreCase(event.getEventTypeId()) || EventEnum.UNIT_ENABLE_RAIL.getKey().equalsIgnoreCase(event.getEventTypeId())) {
                        if(inUnit.getUnitFlexString05() != null){
                            library.execute(inUnit, event, isCtrNotification);
                            inUnit.setUnitFlexString05("Y")
                        }
                }
               else{
                    library.execute(inUnit, event, isCtrNotification);
                }
            }

        }
    }


    private final static Logger logger = Logger.getLogger(this.class)
    def library = ExtensionUtils.getLibrary(getUserContext(), "ITSEmodalLibrary");


}