/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.business.atoms.EventEnum
import com.navis.cargo.business.model.BillOfLading
import com.navis.cargo.business.model.BlGoodsBl
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.inventory.business.api.InventoryCargoManager
import com.navis.inventory.business.units.Unit
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author: Kishore Kumar S && Naresh Kumar <a href= skishore@weservetech.com / > && <a href= mnaresh@weservetech.com / >, 31-10-2022
 * Requirements : TT-17+TT-18 B/L – Stow Cross Reference -- This groovy is used to update count of units against BL .
 * @Inclusion Location	: Incorporated as a code extension of the type GENERAL_NOTICE_CODE_EXTENSION.
 *  Load Code Extension to N4:
 1. Go to Administration --> System -->  Code Extension
 2. Click Add (+)
 3. Enter the values as below:
 Code Extension Name:  ITSUpdateUnitsCountToBLGenaralNotice.
 Code Extension Type:  GENERAL_NOTICE_CODE_EXTENSION.
 Groovy Code: Copy and paste the contents of groovy code.
 4. Click Save button
 *
 *  Set up override configuration in General Notice against UNIT_RESERVE and UNIT_CANCEL_RESERVE events.
 */

class ITSUpdateUnitsCountToBLGenaralNotice extends AbstractGeneralNoticeCodeExtension {
    public static final String UNIT_RESERVE = "UNIT_RESERVE"
    public static final String UNIT_CANCEL_RESERVE = "UNIT_CANCEL_RESERVE"

    @Override
    void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSUpdateUnitsCountToBLGenaralNotice is invoked::")
        if (EventEnum.UNIT_RESERVE.getKey().equalsIgnoreCase(inGroovyEvent.getEvent()?.getEventTypeId())){
            Unit unit = (Unit) inGroovyEvent.getEntity()
            if (unit == null){
                return;
            }
            if (unit?.getUnitGoods() != null && unit?.getUnitGoods()?.getGdsBlNbr()) {
                Collection blGoodsblCollection = inventoryCargoManager1?.getBlGoodsBls(unit)
                if (blGoodsblCollection != null){
                    for (BlGoodsBl blGoodsBl : (blGoodsblCollection as List<BlGoodsBl>)) {
                        if (blGoodsBl.getBlgdsblBl() != null) {
                            List unitList = inventoryCargoManager1?.findUnitsForBillOfLading(blGoodsBl.getBlgdsblBl())
                            if (unitList != null && unitList?.size() > 0) {
                                blGoodsBl?.getBlgdsblBl()?.setBlFlexString01(String.valueOf(unitList?.size()))
                            } else {
                                blGoodsBl?.getBlgdsblBl()?.setBlFlexString01(0)
                            }
                        }
                    }
                }
            }
        }
        else if (EventEnum.UNIT_CANCEL_RESERVE.getKey().equalsIgnoreCase(inGroovyEvent?.getEvent()?.getEventTypeId())){
            Unit unit = (Unit) inGroovyEvent.getEntity()
            if (unit == null){
                return;
            }
            String  evntNote = inGroovyEvent.getEvent()?.getEventNote()
            if (evntNote != null){
                String blNbr = getLastWordUsingSplit(evntNote);
                if (blNbr != null && !blNbr.isEmpty())
                {
                    List<BillOfLading> billOfLading= BillOfLading.findAllBillsOfLading(blNbr);
                    for (BillOfLading bl : billOfLading)
                    {
                        Collection blGoodsblCollection = bl?.getBlBlGoodsBls();
                        if (blGoodsblCollection!=null)
                        {
                            for (BlGoodsBl blGoodsBl : (blGoodsblCollection as List<BlGoodsBl>))
                            {
                                if (blGoodsBl.getBlgdsblBl() != null)
                                {
                                    List unitList = inventoryCargoManager1.findUnitsForBillOfLading(blGoodsBl?.getBlgdsblBl())
                                    if (unitList != null && unitList.size() > 0)
                                    {
                                        blGoodsBl?.getBlgdsblBl()?.setBlFlexString01(String.valueOf(unitList?.size()))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    private static final Logger LOGGER = Logger.getLogger(ITSUpdateUnitsCountToBLGenaralNotice.class)
    private static  InventoryCargoManager inventoryCargoManager1 = (InventoryCargoManager) Roastery.getBean(InventoryCargoManager.BEAN_ID)

    private static String getLastWordUsingSplit(String input) {
        String[] tokens = input.split(" ");
        return tokens[tokens.length - 3]
    }
}
