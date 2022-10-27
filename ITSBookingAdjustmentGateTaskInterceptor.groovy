import com.navis.argo.ArgoPropertyKeys
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.GroovyApi
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.util.BizViolation
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.inventory.business.units.EqBaseOrder
import com.navis.orders.OrdersField
import com.navis.orders.business.api.OrdersFinder
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.services.business.rules.EventType
import com.navis.xpscache.business.atoms.EquipBasicLengthEnum
import com.navis.orders.business.eqorders.Booking
import org.apache.log4j.Level
import org.apache.log4j.Logger
/**
 * @Author <a href="mailto:skishore@weservetech.com">KISHORE KUMAR S</a>
 * @CodeExtension : GATE_TASK_INTERCEPTOR
 * This Groovy class is to perform Booking Adjustment during Gate_In an Unit based on Booking Order Item.
 */

class ITSBookingAdjustmentGateTaskInterceptor extends AbstractGateTaskInterceptor{
    private static final Logger LOGGER = Logger.getLogger(ITSBookingAdjustmentGateTaskInterceptor.class)
    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSBookingAdjustmentGateTaskInterceptor starts :: ")
        TruckTransaction truckTransaction = inWfCtx.getTran()
        Booking bookingOrdr = Booking.findBookingWithoutLine(truckTransaction?.getTranEqo()?.getEqboNbr(),truckTransaction?.getCarrierVisit())
        if (truckTransaction?.getEquipment()?.getEqEquipType()?.getEqtypBasicLength()?.equals(EquipBasicLengthEnum.BASIC40)){
            if (bookingOrdr.getFieldValue(MetafieldIdFactory.valueOf("eqoIsCompleteReceive"))?.equals(true)){
                truckTransaction.setTranStatus(TranStatusEnum.TROUBLE)
                getMessageCollector().registerExceptions(BizViolation.create(PropertyKeyFactory.valueOf("Booking Adjustment Execution Failure"),
                        (BizViolation)null,
                        "Booking is full ${truckTransaction.getTranEqo().getEqboNbr()} - to TROUBLE"));
                return
            }
            EqBaseOrder eqboNbr= truckTransaction.getTranUnit()?.getUnitDepartureOrderItem()?.getEqboiOrder()
            LOGGER.debug("eqboNbr :: "+eqboNbr)
            TimeZone timeZone = ContextHelper.getThreadUserTimezone()
            OrdersFinder ordersFinder= Roastery.getBean(OrdersFinder.BEAN_ID)
            EquipmentOrderItem eqoItem= ordersFinder.findEqoItemByEqType(truckTransaction?.getTranEqo(),
                    truckTransaction?.getEquipment()?.getEqEquipType())
            if (eqoItem!=null){
                if (!eqoItem?.getEqoiEqIsoGroup()?.equals(truckTransaction?.getTranEq()?.getEqIsoGroup()) &&
                        !eqoItem?.getEqoiSampleEquipType()?.getEqtypNominalLength()?.equals(truckTransaction?.getEquipment()?.getEqEquipType()?.getEqtypNominalLength()) &&
                        !eqoItem?.getEqoiSampleEquipType()?.getEqtypNominalHeight()?.equals(truckTransaction?.getEquipment()?.getEqEquipType()?.getEqtypNominalHeight())){
                    truckTransaction.setTranStatus(TranStatusEnum.TROUBLE)
                    getMessageCollector().registerExceptions(BizViolation.create(PropertyKeyFactory.valueOf("Booking Adjustment Execution Failure"),
                            (BizViolation)null,
                            "The Booking is for ${eqoItem.getEqoiSampleEquipType().getEqtypArchetype()} and the Truck arrives with an ${truckTransaction.getEquipment().getEqEquipType().getEqtypArchetype()}"))
                    return
                }
                if (eqoItem?.getEqoiEqIsoGroup()?.equals(truckTransaction?.getTranEq()?.getEqIsoGroup())){
                    if (eqoItem?.getEqoiSampleEquipType()?.getEqtypNominalLength()?.equals(truckTransaction?.getEquipment()?.getEqEquipType()?.getEqtypNominalLength())){
                        if (!eqoItem?.getEqoiSampleEquipType()?.getEqtypNominalHeight()?.equals(truckTransaction?.getEquipment()?.getEqEquipType()?.getEqtypNominalHeight())){
                            if (truckTransaction?.getTranEqo()?.getEqboOrderItems()!=null && truckTransaction?.getTranEqo()?.getEqboOrderItems()?.size()==1){
                                EquipmentOrderItem orderItem= EquipmentOrderItem.createOrderItem(truckTransaction?.getTranEqo(),1,truckTransaction?.getEquipment()?.getEqEquipType())
                                LOGGER.debug("orderItem :: "+orderItem)
                                truckTransaction.getTranEqo().recordEvent(EventType.findEventType("TBD"),null,"Booking Adjustment", ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))
                                if (eqoItem.isHazardous()){
                                    orderItem.setFieldValue(MetafieldIdFactory.valueOf("eqoiHazards"),eqoItem.getEqoiHazards())
                                }
                                eqoItem.purge()
                            }
                        }
                    }
                }
                else if (!eqoItem?.getEqoiEqIsoGroup()?.equals(truckTransaction?.getTranEq()?.getEqIsoGroup()) &&
                        truckTransaction?.getTranEqo()?.getEqboOrderItems()?.size() == 0){
                    truckTransaction.setTranStatus(TranStatusEnum.TROUBLE)
                    getMessageCollector().registerExceptions(BizViolation.create(PropertyKeyFactory.valueOf("Booking Adjustment Execution Failure"),
                            (BizViolation)null,
                            "The Booking is for ${eqoItem.getEqoiSampleEquipType().getEqtypArchetype()} and the Truck arrives with an ${truckTransaction.getEquipment().getEqEquipType().getEqtypArchetype()}"))
                }
            }
            else {
                if (truckTransaction?.getTranEqo()?.getEqboOrderItems()?.size()>1){
                    if (truckTransaction?.getTranEqo()?.getEqboOrderItems()?.contains(eqoItem)){
                        eqoItem.setFieldValue(MetafieldIdFactory.valueOf("eqoiQty"),eqoItem.getEqoiQty()-1)
                        EquipmentOrderItem orderItem= EquipmentOrderItem.createOrderItem(eqboNbr,1,truckTransaction?.getEquipment()?.getEqEquipType())
                        LOGGER.debug("orderItem :: "+orderItem)
                        if (eqoItem.isHazardous()){
                            orderItem.setFieldValue(MetafieldIdFactory.valueOf("eqoiHazards"),eqoItem.getEqoiHazards())
                        }
                    }
                }
            }
        }
    }
}
