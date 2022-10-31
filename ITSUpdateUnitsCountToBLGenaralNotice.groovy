package ITS

import com.navis.argo.business.atoms.EventEnum
import com.navis.cargo.business.model.BillOfLading
import com.navis.cargo.business.model.BlGoodsBl
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.portal.FieldChange
import com.navis.inventory.business.api.InventoryCargoManager
import com.navis.inventory.business.moves.MoveEvent
import com.navis.inventory.business.units.Unit
import com.navis.services.business.api.EventManager
import com.navis.services.business.event.Event
import com.navis.services.business.event.EventFieldChange
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * Date: 31-10-2022
 * UNIT_RESERVE - Naresh Kumar
 * UNIT_CANCEL_RESERVE - Kishore Kumar S
 * Requirements:-  Updating Bill of Ladding FlexString-01 Value based on number of units associated to BL.
 * Trigger: UNIT_RESERVE and UNIT_CANCEL_RESERVE
 * @Inclusion Location	: Incorporated as a code extension of the type GENERAL_NOTICE_CODE_EXTENSION --> Paste this code (ITSUpdateUnitsCountToBLGenaralNotice.groovy)
 * */

class ITSUpdateUnitsCountToBLGenaralNotice extends AbstractGeneralNoticeCodeExtension {
    @Override
    void execute(GroovyEvent inGroovyEvent) {
        LOG_MSG.setLevel(Level.DEBUG)
        LOG_MSG.debug("ITSUpdateUnitsCountToBLGenaralNotice is invoked::")
        if (inGroovyEvent.getEvent().getEvntEventType().equals(EventType.findEventType("UNIT_RESERVE"))){
            Unit unit = (Unit) inGroovyEvent.getEntity()
            if (unit.getUnitGoods() != null && unit.getUnitGoods().getGdsBlNbr()) {
                Collection blGoodsblCollection = inventoryCargoManager1.getBlGoodsBls(unit)
                for (BlGoodsBl blGoodsBl : (blGoodsblCollection as List<BlGoodsBl>)) {
                    if (blGoodsBl.getBlgdsblBl() != null) {
                        List unitList = inventoryCargoManager1.findUnitsForBillOfLading(blGoodsBl.getBlgdsblBl())
                        if (unitList != null && unitList.size() > 0) {
                            blGoodsBl.getBlgdsblBl().setBlFlexString01(String.valueOf(unitList.size()))
                        } else {
                            blGoodsBl.getBlgdsblBl().setBlFlexString01(0)
                        }
                    }
                }
            }
        }
        else if (inGroovyEvent.getEvent().getEvntEventType().equals(EventType.findEventType("UNIT_CANCEL_RESERVE"))){
            Unit unit = (Unit) inGroovyEvent.getEntity()
            LOG_MSG.debug("unit::" + unit)
            String  evntNote = inGroovyEvent.getEvent()?.getEventNote()
            LOG_MSG.debug("Event Notes :: "+evntNote)
            if (evntNote != null){
                String blNbr = getLastWordUsingSplit(evntNote)
                LOG_MSG.debug("BlNbr :: "+blNbr)
                if (blNbr != null && !blNbr.isEmpty()){
                    List<BillOfLading> billOfLading= BillOfLading.findAllBillsOfLading(blNbr)
                    for (BillOfLading bl : billOfLading){
                        Collection blGoodsblCollection = bl?.getBlBlGoodsBls()
                        for (BlGoodsBl blGoodsBl : (blGoodsblCollection as List<BlGoodsBl>)) {
                            if (blGoodsBl.getBlgdsblBl() != null) {
                                List unitList = inventoryCargoManager1.findUnitsForBillOfLading(blGoodsBl?.getBlgdsblBl())
                                if (unitList != null && unitList.size() > 0) {
                                    blGoodsBl?.getBlgdsblBl()?.setBlFlexString01(String.valueOf(unitList?.size()))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    private static final Logger LOG_MSG = Logger.getLogger(this.class);
   private static  InventoryCargoManager inventoryCargoManager1 = (InventoryCargoManager) Roastery.getBean(InventoryCargoManager.BEAN_ID);

    private static String getLastWordUsingSplit(String input) {
        String[] tokens = input.split(" ");
        return tokens[tokens.length - 3];
    }
}