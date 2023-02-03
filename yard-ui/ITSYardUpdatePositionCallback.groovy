/*
* Copyright (c) 2022 WeServe LLC. All Rights Reserved.
*
*/

import com.navis.argo.ContextHelper
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.model.Yard
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.units.MoveInfoBean
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

/*
* @Author <a href="mailto:rkarthikeyan@weservetech.com">Karthikeyan R</a>,
*
* @Inclusion Location	: Incorporated as a code extension of the type .Copy --> Paste this code (ITSYardUpdatePositionCallback.groovy)
*
*/

class ITSYardUpdatePositionCallback  extends AbstractExtensionPersistenceCallback {
    @Override
    void execute(@Nullable Map inMap, @Nullable Map outMap) {
        LOG.info( " inMap .............................. "  + inMap.toString() )
        String ufgGkey =  inMap.get("ufgGkey")
        String newSlotNum  = inMap.get("newSlotNum")
        String userName  = inMap.get("userName")
        String result = ""
        LOG.info( " ufgGkey.................................. "  + ufgGkey )
        LOG.info( " newSlotNum .............................. "  + newSlotNum )
        LOG.info( " userName ................................ "  + userName )

        try {

            UnitFacilityVisit unitFacilityVisit = UnitFacilityVisit.hydrate(Long.valueOf(ufgGkey))
            if (unitFacilityVisit != null) {

                LocPosition locPosition  =  unitFacilityVisit.getUfvLastKnownPosition()
                String oldSlotNum = locPosition.getPosSlot()
                LOG.info( " oldSlotNum .............................. "  + oldSlotNum )

                if (userName != null) {
                    ContextHelper.setThreadExternalUser("tms:" + userName.toLowerCase())
                }

                Yard yard = ContextHelper.getThreadYard()
                Unit unit = unitFacilityVisit.getUfvUnit()

                String updateSlotNum = newSlotNum.replace(".","")
                updateSlotNum = updateSlotNum.substring(0,updateSlotNum.length()-1) + "." + updateSlotNum.substring(updateSlotNum.length() - 1)
                LOG.info( " updateSlotNum .............................. "  + updateSlotNum )
                LocPosition pos = LocPosition.createYardPosition(yard, updateSlotNum, null, unit.getBasicLength(), true);
                unitFacilityVisit.move(pos, (MoveInfoBean)null);
                HibernateApi.getInstance().save(unitFacilityVisit);
                result = "Position updated successfully - [" + unit.getUnitId() +" : "+newSlotNum+"]"
                LOG.info(result)

            }
            outMap.put("responseMessage", result)
        }catch(Exception  e){
            LOG.error("Exception "+ e.getMessage())
            outMap.put("responseMessage", " {'Error': [{'ErrNbr': 90001 } ] } ")
        }
    }
    private static final Logger LOG = Logger.getLogger(ITSYardUpdatePositionCallback.class)
}
