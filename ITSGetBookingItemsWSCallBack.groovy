package ITSIntegration


import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.business.Roastery
import com.navis.framework.util.unit.LengthUnit
import com.navis.framework.zk.util.JSONBuilder
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.orders.business.eqorders.EquipmentOrderItem
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable
import com.navis.orders.business.api.OrdersFinder

import java.text.DecimalFormat

/*
 * @Author <a href="mailto:smohanbabug@weservetech.com">Mohan Babu</a>
 * Date:
 * Requirements:- Receives Order Number and returns Equipment order item details
 *  @Inclusion Location	: Incorporated as a code extension of the type TRANSACTED_BUSINESS_FUNCTION --> Paste this code (ITSGetBookingItemsWSCallback.groovy)
 */
class ITSGetBookingItemsWSCallBack extends AbstractExtensionPersistenceCallback{


    @Override
    void execute(@Nullable Map inMap, @Nullable Map outMap){
        LOGGER.setLevel(Level.DEBUG)
        String orderNbr = inMap.containsKey("orderNbr")?inMap.get("orderNbr"):""
        LOGGER.debug("Order Number-"+ orderNbr)
        String response = getEquipmentOrderItems(orderNbr)
        outMap.put("RESPONSE", response)
    }

    private String getEquipmentOrderItems(String orderNbr){
        JSONBuilder bookingObj = JSONBuilder.createObject();
        if (StringUtils.isBlank(orderNbr)) {
            bookingObj.put("errorMessage", "Order number is null or empty")
        } else {
            LOGGER.debug("Enter else")
            OrdersFinder ordersFinder = Roastery.getBean(OrdersFinder.BEAN_ID)
            List<EquipmentOrder> orders = ordersFinder.findEquipmentOrderByNbr(orderNbr)
            if(orders != null && !orders.isEmpty()){
                LOGGER.debug("Found booking")
                bookingObj.put("bookingNbr",orderNbr)
                JSONBuilder jsonArray = JSONBuilder.createArray()
                for (EquipmentOrder order:orders){
                    JSONBuilder jsonObject = JSONBuilder.createObject()
                    jsonObject.put("line",order.getEqoLine().getBzuId())
                    jsonObject.put("vesselId",order.getEqoVesselVisit() == null ? null : order.getEqoVesselVisit().getCarrierVehicleId())
                    jsonObject.put("voyageNbr",order.getEqoVesselVisit() == null ? null : order.getEqoVesselVisit().getCarrierIbVoyNbrOrTrainId())
                    jsonObject.put("callNbr",order.getEqoVesselVisit() == null ? null : order.getEqoVesselVisit().getCarrierIbVisitCallNbr())

                    Set<EquipmentOrderItem> eqBoOrderItems= order.getEqboOrderItems()
                    if(eqBoOrderItems != null && !eqBoOrderItems.isEmpty()){
                        JSONBuilder itemsJsonArray = JSONBuilder.createArray()
                        for (EquipmentOrderItem eqBoOrderItem : eqBoOrderItems){
                            JSONBuilder eqItemJsonObject = JSONBuilder.createObject()
                            eqItemJsonObject.put("length",eqBoOrderItem.getEqoiEqSize() == null ? null : (int)eqBoOrderItem.getEqoiEqSize().getValueInUnits(LengthUnit.FEET))
                            eqItemJsonObject.put("iso",eqBoOrderItem.getEqoiSampleEquipType() == null ? null : eqBoOrderItem.getEqoiSampleEquipType().getEqtypId())
                            eqItemJsonObject.put("archeTypeISO",(eqBoOrderItem.getEqoiSampleEquipType() == null || eqBoOrderItem.getEqoiSampleEquipType().getEqtypArchetype() == null) ? null : eqBoOrderItem.getEqoiSampleEquipType().getEqtypArchetype().getEqtypId())
                            //DecimalFormat dfFormat = new DecimalFormat("#.#")
                            //eqItemJsonObject.put("height",eqBoOrderItem.getEqoiEqHeight() == null ? null : eqBoOrderItem.getEqoiEqHeight().getValueInUnits(LengthUnit.MILLIMETERS))
                            eqItemJsonObject.put("height",eqBoOrderItem.getEqoiSampleEquipType().getEqtypNominalHeight() == null ? null : eqBoOrderItem.getEqoiSampleEquipType().getEqtypNominalHeight().getKey().replace("NOM",""))
                            eqItemJsonObject.put("Qty",eqBoOrderItem.getEqoiQty())
                            eqItemJsonObject.put("tallyOut",eqBoOrderItem.getEqoiTally())
                            itemsJsonArray.add(eqItemJsonObject)
                        }
                        jsonObject.put("items",itemsJsonArray)
                    }
                    jsonArray.add(jsonObject)
                }
                bookingObj.put("details",jsonArray)
            }
        }
        return bookingObj.toJSONString()
    }

    private static final Logger LOGGER = Logger.getLogger(ITSGetBookingItems.class)
}