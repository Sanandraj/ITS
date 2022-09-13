import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEventExtractManager
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author <a href="mailto:skishore@weservetech.com">KISHORE KUMAR S</a>
 */

class ITSReeferEventRecordGeneralNotice extends  AbstractGeneralNoticeCodeExtension {
    @Override
    void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSReeferEventRecordGeneralNotice Starts :: ")
        Unit unit = (Unit) inGroovyEvent.getEntity()
        LOGGER.debug("unit :: "+unit)
        String eventType = inGroovyEvent.getEvent().getEventTypeId()
        LOGGER.debug("eventType ::"+eventType)
        for (UnitFacilityVisit ufv : (unit.getUnitUfvSet() as List<UnitFacilityVisit>)){
            LOGGER.debug("Inside for")
            ufv.setFieldValue(MetafieldIdFactory.valueOf("ufvUnit.unitIsPowered"),true)
            LOGGER.debug("Field Id 1")
            UnitEventExtractManager.createReeferEvent(unit,inGroovyEvent.getEvent())
            LOGGER.debug("Settings 1")
            unit.recordEvent(EventType.findEventType("UNIT_POWER_CONNECT"),null,null,null)
            //UnitEventExtractManager.createChargeableReeferStorageEvent(ufv,unit,inGroovyEvent.getEvent(),null,null)
            //unit.recordUnitEvent(EventType.findEventType("REEFER"),null,null)
            HibernateApi.getInstance().flush()
            LOGGER.debug("COde Ends")
        }
    }
    private static final Logger LOGGER = Logger.getLogger(ITSReeferEventRecordGeneralNotice.class)
}
