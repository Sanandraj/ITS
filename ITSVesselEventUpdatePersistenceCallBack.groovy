import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.services.business.rules.EventType
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

class ITSVesselEventUpdatePersistenceCallBack extends AbstractExtensionPersistenceCallback{
    @Override
    void execute(@Nullable Map input, @Nullable Map inOutResults) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSVesselEventUpdatePersistenceCallBack starts :: ")
        VesselVisitDetails vvd = (VesselVisitDetails) input?.get("input")
        if (vvd!=null){
            EventType event = EventType.findEventType("TO_BE_DETERMINE")
            LOGGER.debug("EventType :: "+event.getId())
            TimeZone timeZone = ContextHelper.getThreadUserTimezone()
            vvd.recordEvent(event,null,ContextHelper.getThreadUserId(), ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))
        }
    }
    private final static Logger LOGGER = Logger.getLogger(ITSVesselEventUpdatePersistenceCallBack.class)
}
