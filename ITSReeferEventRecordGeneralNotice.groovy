/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/

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
 * @Author: mailto:skishore@weservetech.com, Kishore S; Date: 31/10/2022
 *
 *  Requirements: B 4-1 Insert REEFER event in CUE for containers when they enter the terminal (gate/vessel/rail)
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSReeferEventRecordGeneralNotice
 *     Code Extension Type: GENERAL_NOTICES_CODE_EXTENSION
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup Create the code extension with Extension type GENERAL_NOTICES_CODE_EXTENSION
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */

class ITSReeferEventRecordGeneralNotice extends AbstractGeneralNoticeCodeExtension {
    @Override
    void execute(GroovyEvent inGroovyEvent) {
        Unit unit = (Unit) inGroovyEvent.getEntity()
        if(unit == null){
            return
        }
        UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
        if(ufv == null){
            return
        }
        boolean unitIsReefer = unit?.getUnitIsReefer()
        if (unitIsReefer) {
            ufv.setFieldValue(MetafieldIdFactory.valueOf("ufvUnit.unitIsPowered"), true)
            UnitEventExtractManager.createReeferEvent(unit, inGroovyEvent.getEvent())
            unit.recordEvent(EventType.findEventType("UNIT_POWER_CONNECT"), null, null, null)
            HibernateApi.getInstance().flush()
        }
    }
    private static  Logger LOGGER = Logger.getLogger(ITSReeferEventRecordGeneralNotice.class)
}
