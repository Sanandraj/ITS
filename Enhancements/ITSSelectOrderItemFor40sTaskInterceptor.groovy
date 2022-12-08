/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.AggregateFunctionType
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.query.common.api.QueryResult
import com.navis.framework.util.MetafieldUserMessage
import com.navis.framework.util.TransactionParms
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageCollectorFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.business.api.UnitFinder
import com.navis.orders.OrdersEntity
import com.navis.orders.OrdersField
import com.navis.orders.OrdersPropertyKeys
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.xpscache.business.atoms.EquipBasicLengthEnum
import org.apache.log4j.Logger

/**
 * @Author: uaarthi@weservetech.com; Date: 07-12-2022
 *
 * Requirements : Gate 4-22 Automatic booking item adjustment for40-foot containers --> Adjustment booking against Gate_In unit.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSSelectOrderItemFor40sTaskInterceptor
 *     Code Extension Type:  GATE_TASK_INTERCEPTOR
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *  Configured against SelectOrderItem of ingate - RE
 *
 */
class ITSSelectOrderItemFor40sTaskInterceptor extends AbstractGateTaskInterceptor {

    private static final Logger LOGGER = Logger.getLogger(ITSSelectOrderItemFor40sTaskInterceptor.class)

    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        super.execute(inWfCtx)

        TruckTransaction tran = inWfCtx.getTran()
        MessageCollector mc = MessageCollectorFactory.createMessageCollector()
        MessageCollector oldMc = getMessageCollector()
        TransactionParms parms = TransactionParms.getBoundParms();
        parms.setMessageCollector(mc)

        executeInternal(inWfCtx)
        mc = getMessageCollector()

        MetafieldUserMessage eqoiMismatch = null
        parms.setMessageCollector(oldMc)
        if (mc != null && mc.getMessages(MessageLevel.SEVERE).size() > 0) {
            for (MetafieldUserMessage userMessage : mc.getMessages()) {
                if (MessageLevel.SEVERE.equals(userMessage.getSeverity()) && OrdersPropertyKeys.ERRKEY__MATCHING_EQOI_NOT_FOUND == userMessage.getMessageKey()) {
                    // RoadBizUtil.removeMessageFromMessageCollector(userMessage.getMessageKey());
                    eqoiMismatch = userMessage
                    break
                    // Perform addition of the new item and update tran eqo item
                }
            }
        }

        LOGGER.warn("tran EQO -- "+tran.getTranEqo())
        if (TranSubTypeEnum.RE == tran.getTranSubType() && tran.getTranEqo() != null && tran.getTranEqoItem() == null) {
            EquipmentOrder order = tran.getTranEqo()
            Booking bkg = Booking.resolveBkgFromEqo(order);

            Set bookingItems = bkg.getEqboOrderItems()

            if (tran?.getEquipment()?.getEqEquipType()?.getEqtypBasicLength()?.equals(EquipBasicLengthEnum.BASIC40)) {
                for (EquipmentOrderItem eqoItem : (bookingItems as List<EquipmentOrderItem>)) {
                  //  if (tran.getEquipment().getEqEquipType().getEqtypArchetype().equals(eqoItem?.getEqoiSampleEquipType()?.getEqtypArchetype())) { //TODO Arch type ISO should be same?
                        if (tran.getEquipment().getEqEquipType().getEqtypIsoGroup().equals(eqoItem?.getEqoiSampleEquipType()?.getEqtypIsoGroup())) {
                            if (!eqoItem?.getEqoiSampleEquipType()?.getEqtypNominalHeight()?.equals(tran?.getEquipment()?.getEqEquipType()?.getEqtypNominalHeight())) {
                                Long seqNumber = eqoItem?.getEqoiSeqNbr();
                                if (seqNumber == null) {
                                    seqNumber = getBkgItemMaxSeqNbr(bookingItems);
                                    EquipmentOrderItem orderItem = EquipmentOrderItem.createOrderItem(tran?.getTranEqo(), 1, tran?.getEquipment()?.getEqEquipType(), ++seqNumber);
                                    if (bkg.isHazardous()) {
                                        orderItem?.setFieldValue(MetafieldIdFactory.valueOf("eqoiHazards"), eqoItem?.getEqoiHazards())
                                    }
                                    if (eqoItem?.eqoiIsOog) {
                                        orderItem.setEqoiIsOog(true)
                                    }
                                    if (eqoItem.hasReeferRequirements()){
                                        orderItem.setFieldValue(MetafieldIdFactory.valueOf("eqoiTempRequired"),eqoItem.getReeferRqmnts().getRfreqTempRequiredC())
                                    }
                                    if(orderItem){
                                        tran.setTranEqoItem(orderItem)
                                        tran.setTranCtrTypeId(orderItem.getEqoiSampleEquipType().getEqtypId())
                                    }
                                }

                                boolean isItemMty = Boolean.FALSE
                                Collection unitsForItem = getFinder().findUnitsForOrderItem(eqoItem)
                                if(unitsForItem != null && unitsForItem.size() == 0){
                                    isItemMty = Boolean.TRUE
                                }
                               /* eqoItem.setFieldValue(MetafieldIdFactory.valueOf("eqoiQty"),eqoItem.getEqoiQty() - 1)
                                break*/
                                   if (eqoItem.getEqoiTally() == 0 && isItemMty) {
                                     //  order.recordEvent() // TODO Record a event to purge the item
                                      // eqoItem?.purge(); // TODO Throwing error, order item has reference to other transaction [refers current transaction];
                                       break;
                                   } else if (eqoItem.getEqoiQty() > (eqoItem.getEqoiTallyReceive() + eqoItem.getEqoiTally())) {
                                       eqoItem.setFieldValue(MetafieldIdFactory.valueOf("eqoiQty"),eqoItem.getEqoiQty() - 1)
                                       break;
                                   }
                            }


                            //TODO record event when new item is created.
                        }
                  //  }
                }
            }
        }

    }

    private UnitFinder getFinder() {
        return (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
    }

    private static long getBkgItemMaxSeqNbr(Set<EquipmentOrderItem> inEqoiSet) {
        Long axseqNbr = (long) 0;
        for (EquipmentOrderItem eqoi : inEqoiSet) {
            Long seqNbr = eqoi.getEqoiSeqNbr();
            if (seqNbr != null && seqNbr > axseqNbr) {
                axseqNbr = seqNbr;
            }
        }
        //step: find max of seqNbr from eqoi table
        Long tableMaxseqNbr = (long) 0;
        DomainQuery dq = QueryUtils.createDomainQuery(OrdersEntity.EQUIPMENT_ORDER_ITEM)
                .addDqAggregateField(AggregateFunctionType.MAX, OrdersField.EQOI_SEQ_NBR);
        QueryResult qr = HibernateApi.getInstance().findValuesByDomainQuery(dq);
        if (qr.getTotalResultCount() > 0) {
            Object valueObj = qr.getValue(0, 0);
            if (valueObj != null) {
                tableMaxseqNbr = (Long) valueObj;
            }
        }
        return tableMaxseqNbr > axseqNbr ? tableMaxseqNbr : axseqNbr;
    }
}
