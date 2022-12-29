package ITS

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.*
import com.navis.argo.business.integration.IntegrationServiceMessage
import com.navis.argo.business.model.ArgoSequenceProvider
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.reference.Equipment
import com.navis.carina.integrationservice.business.IntegrationService
import com.navis.extension.invocation.dynamiccode.IExtension
import com.navis.extension.invocation.dynamiccode.IExtensionClassProvider
import com.navis.external.framework.util.ExtensionUtils
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.ExtensionField
import com.navis.framework.IntegrationServiceField
import com.navis.framework.business.Roastery
import com.navis.framework.business.atoms.IntegrationServiceDirectionEnum
import com.navis.framework.business.atoms.IntegrationServiceTypeEnum
import com.navis.framework.extension.FrameworkExtensionTypes
import com.navis.framework.metafields.MetafieldIdList
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.context.PortalApplicationContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.portal.query.PredicateIntf
import com.navis.framework.presentation.internationalization.MessageTranslator
import com.navis.framework.util.internationalization.TranslationUtils
import com.navis.framework.util.scope.ScopeCoordinates
import com.navis.framework.zk.util.JSONBuilder
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.units.ReeferRecord
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.RoadEntity
import com.navis.road.RoadField
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.services.business.api.EventManager
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import com.navis.services.business.rules.Flag
import com.navis.services.business.rules.FlagType
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import wslite.json.JSONObject

import java.text.DateFormat
import java.text.SimpleDateFormat

class ITSEquipmentStatusGenNotice extends AbstractGeneralNoticeCodeExtension {

    @Override
    void execute(GroovyEvent inEvent) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("Inside the EquipmentStatusGenNotice :: Start")
        def library = ExtensionUtils.getLibrary(getUserContext(), "ITSEmodalLibrary");
        if (inEvent == null) {
            return
        }
        if (library == null) {
            IExtension extension
            DomainQuery extDomainQuery = QueryUtils.createDomainQuery("Extension")
                    .addDqPredicate(PredicateFactory.eq(ExtensionField.EXT_NAME, "ITSEmodalLibrary"))
                    .addDqPredicate(PredicateFactory.eq(ExtensionField.EXT_TYPE, FrameworkExtensionTypes.LIBRARY.getTypeId()))
            if (extDomainQuery != null) {
                extension = (IExtension) HibernateApi.getInstance().getUniqueEntityByDomainQuery(extDomainQuery)
                if (extension != null) {
                    LOGGER.debug("Extension Name::" + extension.toString())
                    IExtensionClassProvider provider = (IExtensionClassProvider) PortalApplicationContext.getBean("extensionClassProvider");
                    if (provider != null) {
                        library = provider.getExtensionClassInstance(extension);
                    }
                }
            }        }

        Unit unit = (Unit) inEvent.getEntity()
        if (unit == null) {
            return
        }
        Event event = inEvent.getEvent()
        if(event != null){
            library.execute((Unit) inEvent.getEntity(), inEvent.getEvent())
        }


    }


    private static final Logger LOGGER = Logger.getLogger(this.class)

}
