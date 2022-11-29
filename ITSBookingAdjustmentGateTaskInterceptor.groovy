/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.AggregateFunctionType
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.query.common.api.QueryResult
import com.navis.orders.OrdersEntity
import com.navis.orders.OrdersField
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.xpscache.business.atoms.EquipBasicLengthEnum
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author: Kishore Kumar S <a href= skishore@weservetech.com / >, 28/10/2022
 * Requirements : Gate 4-22 Automatic booking item adjustment for40-foot containers --> Adjustment booking against Gate_In unit.
 * @Inclusion Location	: Incorporated as a code extension of the type GATE_TASK_INTERCEPTOR.
 *  Load Code Extension to N4:
 1. Go to Administration --> System -->  Code Extension
 2. Click Add (+)
 3. Enter the values as below:
 Code Extension Name:  ITSBookingAdjustmentGateTaskInterceptor.
 Code Extension Type:  GATE_TASK_INTERCEPTOR.
 Groovy Code: Copy and paste the contents of groovy code.
 4. Click Save button
 *
 *  Set up configuration against RejectOrdItemReceiveMismatch - IN_GATE Transaction.
 */

class ITSBookingAdjustmentGateTaskInterceptor extends AbstractGateTaskInterceptor {
    private static final Logger LOGGER = Logger.getLogger(ITSBookingAdjustmentGateTaskInterceptor.class)

    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        LOGGER.setLevel(Level.INFO)
        LOGGER.info("ITSBookingAdjustmentGateTaskInterceptor starts :: ")
        TruckTransaction truckTransaction = inWfCtx.getTran()
        if (truckTransaction != null){
            Booking bookingOrdr = Booking.findBookingWithoutLine(truckTransaction?.getTranEqo()?.getEqboNbr(), truckTransaction?.getCarrierVisit())
            Set bookingItems = bookingOrdr.getEqboOrderItems()
            if (bookingItems != null){
                if (truckTransaction?.getEquipment()?.getEqEquipType()?.getEqtypBasicLength()?.equals(EquipBasicLengthEnum.BASIC40)){
                    for (EquipmentOrderItem eqoItem : (bookingItems as List<EquipmentOrderItem>)) {
                        if (truckTransaction.getEquipment().getEqEquipType().getEqtypArchetype().equals(eqoItem?.getEqoiSampleEquipType()?.getEqtypArchetype())){
                            if (truckTransaction.getEquipment().getEqEquipType().getEqtypIsoGroup().equals(eqoItem?.getEqoiSampleEquipType()?.getEqtypIsoGroup())) {
                                if (!eqoItem?.getEqoiSampleEquipType()?.getEqtypNominalHeight()?.equals(truckTransaction?.getEquipment()?.getEqEquipType()?.getEqtypNominalHeight())) {
                                    Long seqNumber = eqoItem?.getEqoiSeqNbr();
                                    if (seqNumber == null) {
                                        seqNumber = getBkgItemMaxSeqNbr(bookingItems);
                                        EquipmentOrderItem orderItem = EquipmentOrderItem.createOrderItem(truckTransaction?.getTranEqo(), 1, truckTransaction?.getEquipment()?.getEqEquipType(), ++seqNumber);
                                        if (bookingOrdr.isHazardous()) {
                                            orderItem?.setFieldValue(MetafieldIdFactory.valueOf("eqoiHazards"), eqoItem?.getEqoiHazards())
                                        }
                                        if (eqoItem?.eqoiIsOog) {
                                            orderItem.setEqoiIsOog(true)
                                        }
                                        if (eqoItem.hasReeferRequirements()){
                                            orderItem.setFieldValue(MetafieldIdFactory.valueOf("eqoiTempRequired"),eqoItem.getReeferRqmnts().getRfreqTempRequiredC())
                                        }
                                    }
                                    if (eqoItem.getEqoiTally() == 0) {
                                        eqoItem?.purge();
                                        break;
                                    } else if (eqoItem.getEqoiTally() > 0) {
                                        eqoItem.setFieldValue(MetafieldIdFactory.valueOf("eqoiQty"),eqoItem.getEqoiQty() - 1)
                                        break;
                                    }
                                }
                            }
                        }
                        else {
                            executeInternal(inWfCtx);
                        }
                    }
                }
            }
        }
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
