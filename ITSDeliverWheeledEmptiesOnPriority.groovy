import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.atoms.WiMoveKindEnum
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.business.xps.model.StackStatus
import com.navis.argo.business.xps.util.StackStatusUtils
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.business.Roastery
import com.navis.framework.business.atoms.LifeCycleStateEnum
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.PoolsEntity
import com.navis.inventory.business.api.PoolField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.pools.Pool
import com.navis.inventory.business.pools.PoolMember
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @author <ahref="mailto:vsathish@weservetech.com">Sathish V</a>
 * Date : 20/11/2022
 * Requirements:-Wheeled empties take priority for delivery when a truck arrives bobtail, if not found deliver grounded one. If comes with Chassis deliver Grounded one.
 * @Inclusion Location	: Incorporated as a code extension of the type GATE_TASK_INTERCEPTOR --> Paste this code (ITSDeliverWheeledEmptiesOnPriority.groovy)
 * Modified : @author <ahref="mailto:mnithish@weservetech.com">Nithish M</a>
 * Modified : @author <ahref="mailto:mmadhavan@weservetech.com">Madhavan M</a>
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
        //Pool.findOrCreatePool()

        ScopedBizUnit inLinOp = inTran.getTranAppointment()?.getGapptLineOperator()
        EquipmentOrderItem odrItm = inTran.getTranEqoItem()
        if (inLinOp != null && odrItm != null) {
            if (inTran.getTranCtrNbrAssigned() == null && inTran.getTranChsNbr() == null) {  // bobtails
                Unit inUnit = findWheeledUnitsPriority(inLinOp, odrItm, true, false)
                LOGGER.debug("inUnit ::" + inUnit)
                if (inUnit != null) {
                    inTran.setTranCtrNbrAssigned(inUnit.getUnitId())
                    if (inUnit.getUnitCurrentlyAttachedChassisId()) {
                        LOGGER.debug("chassis ::" + inUnit.getUnitCurrentlyAttachedChassisId())
                        inTran.setTranChsNbr(inUnit.getUnitCurrentlyAttachedChassisId())
                    }
                }
                else {
                    inTran.setTranStatus(TranStatusEnum.TROUBLE)
                    String errorMsg = "Suitable Wheeled/Grounded Containers Not Found";
                    RoadBizUtil.messageCollector.appendMessage(MessageLevel.SEVERE, AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, errorMsg);
                }
            } else if (inTran.getTranCtrNbrAssigned() == null) {        //chassis
                Unit inUnit = findWheeledUnitsPriority(inLinOp, odrItm, false, true)
                LOGGER.debug("inUnit Grounded::" + inUnit)
                if (inUnit != null) {
                    inTran.setTranCtrNbrAssigned(inUnit.getUnitId())
                }
                else {
                    inTran.setTranStatus(TranStatusEnum.TROUBLE)
                    String errorMsg = "Suitable Grounded Containers Not Found";
                    RoadBizUtil.messageCollector.appendMessage(MessageLevel.SEVERE, AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, errorMsg);
                }
            }
        }
        executeInternal(inWfCtx);
    }

    private Unit findWheeledUnitsPriority(ScopedBizUnit lineOp, EquipmentOrderItem OrdrItm, Boolean Wheeled, Boolean Grounded) {

        List<Serializable> lineList = new ArrayList<>()
        lineList.add(lineOp.getPrimaryKey())
        LOGGER.debug("linelist1::" + lineList)

        DomainQuery df = QueryUtils.createDomainQuery(PoolsEntity.POOL)
                .addDqPredicate(PredicateFactory.eq(PoolField.POOL_ADMIN_LIFE_CYCLE_STATE, LifeCycleStateEnum.ACTIVE))
                .addDqOrdering(Ordering.asc(PoolField.POOL_NAME))
        List<Pool> listPools = Roastery.getHibernateApi().findEntitiesByDomainQuery(df);
        LOGGER.debug("listpools::" + listPools)
        List<ScopedBizUnit> agrmtMembers = new ArrayList<>()
        List<ScopedBizUnit> tempMem=new ArrayList<>();
        for (Pool pool : listPools) {
            boolean contains= false;
            tempMem.clear();
            LOGGER.debug("tempMem::" +tempMem)
            Set poolMembers = pool.getPoolMembers()
            LOGGER.debug("poolmembers::" + poolMembers)
            for (PoolMember member : poolMembers) {
                ScopedBizUnit eqOperator = (ScopedBizUnit) member.getPoolmbrEqOper();
                LOGGER.debug("eqOperator::" + eqOperator)
                if (member.getLifeCycleState() == LifeCycleStateEnum.ACTIVE && eqOperator.equals(lineOp)) {
                    LOGGER.debug("true.....")
                    contains=true
                }
                if (eqOperator != null) {
                    tempMem.add(eqOperator)
                    LOGGER.debug("tempMem::" +tempMem)
                }
            }
            if (contains){
                for(ScopedBizUnit tt:tempMem){
                    agrmtMembers.add(tt)
                }

                LOGGER.debug("agrmtMembers::" +agrmtMembers)
            }
        }
        for(ScopedBizUnit inLine2:agrmtMembers){
            lineList.add(inLine2.getPrimaryKey())
        }
        LOGGER.debug("linelist::" + lineList)
        DomainQuery dq = QueryUtils.createDomainQuery("Unit")
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CATEGORY, UnitCategoryEnum.STORAGE))
                .addDqPredicate(PredicateFactory.in(UnitField.UNIT_LINE_OPERATOR, lineList))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_EQ_ISO_GROUP, OrdrItm.getEqoiEqIsoGroup()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_EQ_NOMINAL_LENGTH, OrdrItm.getEqoiEqSize()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_EQTYP_HEIGHT, OrdrItm.getEqoiEqHeight()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_VISIT_STATE, UnitVisitStateEnum.ACTIVE))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CURRENT_UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD))
        List<ScopedBizUnit> scopList = new ArrayList<>()
        for(Serializable serializable : lineList){
            ScopedBizUnit scopedBizUnit = ScopedBizUnit.hydrate(serializable)
            scopList.add(scopedBizUnit)
        }
        LOGGER.debug("scopList::" + scopList)

        List<Unit> units = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        LOGGER.debug("units ::" + units.toString())
        if (units != null && units.size() > 0) {
            for (ScopedBizUnit line : (scopList as List<ScopedBizUnit>)) {
                if (Wheeled) {
                    LOGGER.debug("Inside Wheeled ::" + Wheeled)
                    for (Unit unit : (units as List<Unit>)) {
                        if (unit != null && unit.getUnitLineOperator().equals(line)) {
                            LOGGER.debug("unit ::" + unit.getUnitId())
                            UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
                            if (ufv != null) {
                                List unitDeli = ufv.getWorkInstsWithMoveKind(WiMoveKindEnum.Delivery)
                                LOGGER.debug("unitDeli ::" + unitDeli)
                                if ((unitDeli == null || unitDeli.isEmpty()) && unit.findCurrentPosition()?.isWheeled() && unit.getUnitCurrentlyAttachedChassisId() != null) {
                                    StackStatus stackStatus = StackStatus.findStackStatus(ufv.getUfvLastKnownPosition()?.getPosBin(), ContextHelper.getThreadYard())
                                    String deliverable = stackStatus != null ? StackStatusUtils.getProtectedStatus(stackStatus.getStackstatusStatusChars()) : null
                                    Boolean isMnWrkng = stackStatus != null ? StackStatusUtils.isMenWorking(stackStatus.getStackstatusStatusChars()) : false
                                    Boolean isTempBlockd = stackStatus != null ? StackStatusUtils.isTempBlocked(stackStatus.getStackstatusStatusChars()) : false
                                    if (stackStatus == null || (!isMnWrkng && !isTempBlockd && !'C'.equalsIgnoreCase(deliverable))) {
                                        LOGGER.debug("stackStatus ::" + stackStatus)
                                        LOGGER.debug("stackStatus ::" + stackStatus?.getStackstatusStatusChars())
                                        return unit
                                    }
                                }
                            }
                        }
                    }
                    Grounded = true
                    LOGGER.debug("Grounded ::" + Grounded)
                }
                if (Grounded) {
                    LOGGER.debug("Inside Grounded ::" + Grounded)
                    for (Unit unit : (units as List<Unit>)) {
                        if (unit != null && unit.getUnitLineOperator().equals(line)) {
                            LOGGER.debug("unit ::" + unit.getUnitId())
                            UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
                            if (ufv != null) {
                                List unitDeli = ufv.getWorkInstsWithMoveKind(WiMoveKindEnum.Delivery)
                                LOGGER.debug("unitDeli ::" + unitDeli)
                                if ((unitDeli == null || unitDeli.isEmpty()) && unit.getUnitCurrentlyAttachedChassisId() == null) {
                                    StackStatus stackStatus = StackStatus.findStackStatus(ufv.getUfvLastKnownPosition()?.getPosBin(), ContextHelper.getThreadYard())
                                    String deliverable = stackStatus != null ? StackStatusUtils.getProtectedStatus(stackStatus.getStackstatusStatusChars()) : null
                                    Boolean isMnWrkng = stackStatus != null ? StackStatusUtils.isMenWorking(stackStatus.getStackstatusStatusChars()) : false
                                    Boolean isTempBlockd = stackStatus != null ? StackStatusUtils.isTempBlocked(stackStatus.getStackstatusStatusChars()) : false
                                    if (stackStatus == null || (!isMnWrkng && !isTempBlockd && !'C'.equalsIgnoreCase(deliverable))) {
                                        LOGGER.debug("stackStatus ::" + stackStatus)
                                        LOGGER.debug("stackStatus ::" + stackStatus?.getStackstatusStatusChars())
                                        return unit
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
    private static Logger LOGGER = Logger.getLogger(ITSDeliverWheeledEmptiesOnPriority.class)
}
