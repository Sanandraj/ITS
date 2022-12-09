import com.navis.argo.ContextHelper
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.util.ExtensionUtils
import com.navis.framework.persistence.Entity
import com.navis.framework.persistence.HibernatingEntity
import com.navis.services.business.event.Event
import org.apache.log4j.Logger

class ITSCheckBookingVesselVisitELI extends AbstractEntityLifecycleInterceptor {

    private static Logger LOGGER = Logger.getLogger(ITSCheckBookingVesselVisitELI.class);

    @Override
    public void preDelete(Entity inEntity) {
        def library = ExtensionUtils.getLibrary(ContextHelper.getThreadUserContext(), "ITSEmodalLibrary");
        Event event = null
        if (inEntity != null) {
            library.execute((HibernatingEntity) inEntity, event)
        }
    }

}


