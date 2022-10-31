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
 * @Author <a href="mailto:skishore@weservetech.com">Kishore.S.K.</a>
 */

class ITSReeferEventRecordGeneralNotice extends  AbstractGeneralNoticeCodeExtension {
    @Override
    void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSReeferEventRecordGeneralNotice Starts :: ")
        Unit unit = (Unit) inGroovyEvent.getEntity()
        LOGGER.debug("Unit ::"+unit)
        UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
        LOGGER.debug("ufv ::"+ufv)
        boolean unitIsReefer= unit.getUnitIsReefer()
        if (unitIsReefer){
            ufv.setFieldValue(MetafieldIdFactory.valueOf("ufvUnit.unitIsPowered"),true)
            LOGGER.debug("1111")
            UnitEventExtractManager.createReeferEvent(unit,inGroovyEvent.getEvent())
            LOGGER.debug("2222")
            unit.recordEvent(EventType.findEventType("UNIT_POWER_CONNECT"),null,null,null)
            LOGGER.debug("3333")
            HibernateApi.getInstance().flush()
        }
    }
    private static final Logger LOGGER = Logger.getLogger(ITSReeferEventRecordGeneralNotice.class)
}