package ITSIntegration

import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.zk.util.JSONBuilder
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.imdg.HazardousGoods
import com.navis.inventory.business.imdg.Hazards
import com.navis.inventory.business.imdg.Placard
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrderItem
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

class ITSGetBookingHazardItemsWSCallBack extends AbstractExtensionPersistenceCallback{
    @Override
    void execute(@org.jetbrains.annotations.Nullable Map inMap, @org.jetbrains.annotations.Nullable Map outMap) {
        LOGGER.setLevel(Level.DEBUG)
        String bookingNbr = inMap.containsKey("bookingNbr")?inMap.get("bookingNbr"):""
        String lineId = inMap.containsKey("lineId")?inMap.get("lineId"):""
        String equipmentTypeCd = inMap.containsKey("typeCd")?inMap.get("typeCd"):""
        LOGGER.debug("Booking Number-"+ bookingNbr)
        String response = getHazardItems(bookingNbr, lineId, equipmentTypeCd)
        outMap.put("RESPONSE", response)
    }
    private String getHazardItems(String bookingNbr, String lineId, String equipmentTypeCd){
        JSONBuilder jsonObject = JSONBuilder.createObject()
        if(bookingNbr != null && StringUtils.isNotEmpty(bookingNbr)){
            ScopedBizUnit bizUnit = ScopedBizUnit.findScopedBizUnit(lineId, BizRoleEnum.LINEOP)
            if(bizUnit != null){
                LOGGER.debug("Found bizunit-"+ bizUnit.toString())
                Booking booking = Booking.findBookingWithoutVesselVisit(bookingNbr, bizUnit)
                if(booking != null){
                    LOGGER.debug("Found hazardous booking")
                    jsonObject.put("bookingNbr", bookingNbr)
                    Iterator<HazardItem> hazardItems = getHazardItems(booking, equipmentTypeCd)
                    JSONBuilder jsonArray = JSONBuilder.createArray()
                    if(hazardItems != null){
                        for(HazardItem hazardItem: hazardItems){
                            JSONBuilder hzdItemJsonObject = JSONBuilder.createObject()
                            hzdItemJsonObject.put("unNum",hazardItem.getHzrdiUNnum())
                            if(hazardItem.getHzrdiImdgClass() != null){
                                hzdItemJsonObject.put("imdgClass",hazardItem.getHzrdiImdgClass().getName())
                            }
                            hzdItemJsonObject.put("properName",hazardItem.getHzrdiProperName())
                            hzdItemJsonObject.put("technicalName",hazardItem.getHzrdiTechName())
                            hzdItemJsonObject.put("pageNumber",hazardItem.getHzrdiPageNumber())
                            Map<Placard,Boolean> placards = hazardItem.getPlacards()
                            JSONBuilder placardJsonArray = JSONBuilder.createArray()
                            if(placards != null && !placards.isEmpty()){
                                for(Placard placard:placards.keySet()){
                                    JSONBuilder hzdPlacardJsonObject = JSONBuilder.createObject()
                                    hzdPlacardJsonObject.put("text",placard.getPlacardText())
                                    hzdPlacardJsonObject.put("explanation",placard.getPlacardFurtherExplanation())
                                    hzdPlacardJsonObject.put("minWeight",placard.getPlacardMinWtKg())
                                    placardJsonArray.add(hzdPlacardJsonObject)
                                }
                            }
                            hzdItemJsonObject.put("placards",placardJsonArray)
                            jsonArray.add(hzdItemJsonObject)
                        }
                    }
                    jsonObject.put("hazardItems", jsonArray)
                }
            }
        }
        return jsonObject.toJSONString()
    }

    private Iterator<HazardItem> getHazardItems(Booking booking,String equipmentTypeCd){
        Iterator<HazardItem> hazardItems = null
        if(booking.isHazardous()){
            Hazards hazard = booking.getEqoHazards()
            if(hazard != null){
                hazardItems = hazard.getHazardItemsIterator()
            }
        }
        else{
           Set<EquipmentOrderItem> bookingItems = booking.getEqboOrderItems()
            for(EquipmentOrderItem item: bookingItems){
                if(equipmentTypeCd == item.getEqoiSampleEquipType().getEqtypId() && item.isHazardous()){
                    Hazards hazard = item.getEqoiHazards()
                    hazardItems = hazard.getHazardItemsIterator()
                    break
                }
            }
        }

        return  hazardItems
    }

    private static final Logger LOGGER = Logger.getLogger(ITSGetBookingHazardItemsWSCallBack.class)
}
