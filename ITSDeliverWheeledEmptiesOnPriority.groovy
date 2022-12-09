import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @author <ahref="mailto:vsathish@weservetech.com">Sathish V</a>
 * Wheeled empties take priority for delivery when a truck arrives bobtail, if not found deliver grounded one. If comes with Chassis deliver Grounded one.
 * Configuration: Add under 'Code Extension' tab as GATE_TASK_INTERCEPTOR type
 * Include the Business task SelectEmptyContainer in the TRANTYPE DM and add this CodeExtension
 * <ahref="mailto:mnithish@weservetech.com">Nithish M</a>
 * Send to Trouble if no units found
 */

class ITSDeliverWheeledEmptiesOnPriority extends AbstractGateTaskInterceptor {

    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSDeliverWheeledEmptiesOnPriority STARTS::")
        TruckTransaction inTran = inWfCtx.getTran()
        if (inTran == null) {
            return;
        }
        ScopedBizUnit inLinOp = inTran.getTranAppointment()?.getGapptLineOperator()
        EquipmentOrderItem odrItm = inTran.getTranEqoItem()
        if (inLinOp != null && odrItm != null) {
            if (inTran.getTranCtrNbrAssigned() == null && inTran.getTranChsNbr() == null) {
                Unit inUnit = findWheeledUnitsPriority(inLinOp, odrItm, true, false)
                if (inUnit != null) {
                    inTran.setTranCtrNbrAssigned(inUnit.getUnitId())
                    if (inUnit.getUnitCurrentlyAttachedChassisId()) {
                        inTran.setTranChsNbr(inUnit.getUnitCurrentlyAttachedChassisId())
                    } else {
                        inTran.setTranStatus(TranStatusEnum.TROUBLE)
                        String errorMsg = "Suitable Wheeled/Grounded Containers Not Found";
                        RoadBizUtil.messageCollector.appendMessage(MessageLevel.SEVERE, AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, errorMsg);
                    }
                }
            } else if (inTran.getTranCtrNbrAssigned() == null) {
                Unit inUnit = findWheeledUnitsPriority(inLinOp, odrItm, false, true)
                if (inUnit != null) {
                    inTran.setTranCtrNbrAssigned(inUnit.getUnitId())
                } else {
                    inTran.setTranStatus(TranStatusEnum.TROUBLE)
                    String errorMsg = "Suitable Grounded Containers Not Found";
                    RoadBizUtil.messageCollector.appendMessage(MessageLevel.SEVERE, AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, errorMsg);
                }
            }
        }
        executeInternal(inWfCtx);
    }

    private Unit findWheeledUnitsPriority(ScopedBizUnit lineOp, EquipmentOrderItem OrdrItm, Boolean Wheeled, Boolean Grounded) {

        DomainQuery dq = QueryUtils.createDomainQuery("Unit")
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CATEGORY, UnitCategoryEnum.STORAGE))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_LINE_OPERATOR, lineOp.getPrimaryKey()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_EQ_ISO_GROUP, OrdrItm.getEqoiEqIsoGroup()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_EQ_NOMINAL_LENGTH, OrdrItm.getEqoiEqSize()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_EQTYP_HEIGHT, OrdrItm.getEqoiEqHeight()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_VISIT_STATE, UnitVisitStateEnum.ACTIVE))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CURRENT_UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD))

        LOGGER.debug("Dq units ::" + dq)
        List<Unit> units = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        //LOGGER.debug("units ::" + units)
        if (units != null && units.size() > 0) {
            if (Wheeled) {
                for (Unit unit : (units as List<Unit>)) {
                    if (unit?.getUnitActiveUfvNowActive()?.getFinalPlannedPosition() == null && unit?.findCurrentPosition()?.isWheeled() && unit?.getUnitCurrentlyAttachedChassisId() != null) {
                        return unit
                    }
                }
                Grounded = true
            }
            if (Grounded) {
                for (Unit unit : (units as List<Unit>)) {
                    if (unit?.getUnitActiveUfvNowActive()?.getFinalPlannedPosition() == null && unit?.getUnitCurrentlyAttachedChassisId() == null)
                        return unit
                }
            }
        }
        return null;
    }
    private static Logger LOGGER = Logger.getLogger(ITSDeliverWheeledEmptiesOnPriority.class)
}
