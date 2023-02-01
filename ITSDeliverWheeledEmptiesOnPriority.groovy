import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.EquipClassEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.atoms.WiMoveKindEnum
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.reference.EquipType
import com.navis.argo.business.reference.LineOperator
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
import com.navis.inventory.business.pools.PoolMemberEqClass
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.spatial.business.model.AbstractBin
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
        LOGGER.debug("ITSDeliverWheeledEmptiesOnPriority STARTS::" + new Date())
        TruckTransaction inTran = inWfCtx.getTran()
        inWfCtx.getTv().getActiveTransactions()
        if (inTran == null) {
            return;
        }
        ScopedBizUnit inLinOp = inTran.getTranAppointment()?.getGapptLineOperator()
        LOGGER.debug("inLinOp:: " + inLinOp)
        EquipmentOrderItem odrItm = inTran.getTranEqoItem()
        LOGGER.debug("odrItm:: " + odrItm)
        if (inLinOp == null) {
            LineOperator line = inTran.getTranLine()
            if (line) {
                inLinOp = ScopedBizUnit.findScopedBizUnit(line.getBzuId(), BizRoleEnum.LINEOP)
            }
        }

        if (inLinOp != null && odrItm != null) {
            if (inTran.getTranCtrNbrAssigned() == null && (inTran.getTranChsNbr() == null)) {            // bobtails
                Unit inUnit = findWheeledUnitsPriority(inLinOp, odrItm, true, false)
                if (inUnit != null) {
                    inTran.setTranCtrNbrAssigned(inUnit.getUnitId())
                    if (inUnit.getUnitCurrentlyAttachedChassisId()) {
                        inTran.setTranChsNbr(inUnit.getUnitCurrentlyAttachedChassisId())
                    }
                } else {
                    inTran.setTranStatus(TranStatusEnum.TROUBLE)
                    String errorMsg = "Suitable Wheeled/Grounded Containers Not Found";
                    RoadBizUtil.messageCollector.appendMessage(MessageLevel.SEVERE, AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, errorMsg);
                }
            } else if (inTran.getTranCtrNbrAssigned() == null) {      //chassis
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

        List<Serializable> memberLineList = new ArrayList<>()
        memberLineList.add(lineOp.getPrimaryKey())

        /*start equipment pools validation*/
        DomainQuery df = QueryUtils.createDomainQuery(PoolsEntity.POOL)
                .addDqPredicate(PredicateFactory.eq(PoolField.POOL_ADMIN_LIFE_CYCLE_STATE, LifeCycleStateEnum.ACTIVE))
                .addDqOrdering(Ordering.asc(PoolField.POOL_NAME))
        List<Pool> listPools = Roastery.getHibernateApi().findEntitiesByDomainQuery(df);
        List<ScopedBizUnit> equPoolFinalMembers = new ArrayList<>()
        List<ScopedBizUnit> tempMembers = new ArrayList<>();
        for (Pool pool : listPools) {
            LOGGER.debug("pool ::" + pool)
            boolean eqmLineFind = false
            tempMembers.clear()
            Set poolMembers = pool.getPoolMembers()
            for (PoolMember member : poolMembers) {
                ScopedBizUnit eqOperator = (ScopedBizUnit) member.getPoolmbrEqOper();
                if (member.getLifeCycleState() == LifeCycleStateEnum.ACTIVE && eqOperator.equals(lineOp)) {
                    eqmLineFind = true
                }
                Set poolMbrEqClasses = member.getPoolmbrEqClasses()
                for (PoolMemberEqClass eqClass : poolMbrEqClasses) {
                    if (eqOperator != null && eqClass.getPooleqclassEqClassEnum().equals(EquipClassEnum.CONTAINER)) {
                        tempMembers.add(eqOperator)
                    }
                }
            }

            if (eqmLineFind) {
                for (ScopedBizUnit memberLists : tempMembers) {
                    equPoolFinalMembers.add(memberLists)
                }
            }
        }
        /*End EquipmentPools validation*/

        for (ScopedBizUnit scopePrimaryKey : equPoolFinalMembers) {
            memberLineList.add(scopePrimaryKey.getPrimaryKey())
        }


        /* Find order item archetype */
        EquipType archType = null
        EquipType orderEquipType = OrdrItm.getEqoiSampleEquipType()
        if (orderEquipType != null) {
            String archTypeId = orderEquipType?.getEqtypArchetype()?.getEqtypId()
            if (archTypeId != null) {
                archType = EquipType.findEquipType(archTypeId)
                /*if (archType != null) {
                    Collection eqIsoList = EquipType.findEquipTypesByArchType(archType)
                }*/
            }
        }
        DomainQuery dq = QueryUtils.createDomainQuery("Unit")
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CATEGORY, UnitCategoryEnum.STORAGE))
                .addDqPredicate(PredicateFactory.in(UnitField.UNIT_LINE_OPERATOR, memberLineList))

                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_EQTYPE_ARCHETYPE, archType?.getEqtypGkey()))
        // .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_EQTYPE_ISO_GROUP, OrdrItm.getEqoiEqIsoGroup()))

        // .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_EQ_ISO_GROUP, OrdrItm.getEqoiEqIsoGroup()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_EQ_NOMINAL_LENGTH, OrdrItm.getEqoiEqSize()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_EQTYP_HEIGHT, OrdrItm.getEqoiEqHeight()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_VISIT_STATE, UnitVisitStateEnum.ACTIVE))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CURRENT_UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD))
        //final Equipment pools list
        LOGGER.debug("dq::" + dq)
        List<ScopedBizUnit> listScopedBizUnit = new ArrayList<>()
        for (Serializable serializable : memberLineList) {
            ScopedBizUnit scopedBizUnit = ScopedBizUnit.hydrate(serializable)
            listScopedBizUnit.add(scopedBizUnit)
        }

        List<Unit> units = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        if (units != null && units.size() > 0) {
            for (ScopedBizUnit line : (listScopedBizUnit as List<ScopedBizUnit>)) {
                if (Wheeled) {
                    for (Unit unit : (units as List<Unit>)) {
                        if (unit != null && unit.getUnitLineOperator().equals(line)) {
                            UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
                            if (ufv != null) {
                                List unitDeli = ufv.getWorkInstsWithMoveKind(WiMoveKindEnum.Delivery)
                                if ((unitDeli == null || unitDeli.isEmpty()) && unit.findCurrentPosition()?.isWheeled() && unit.getUnitCurrentlyAttachedChassisId() != null) {
                                    if (ufv.getUfvLastKnownPosition() != null) {
                                        LocPosition locPosition = ufv.getUfvLastKnownPosition()
                                        if (locPosition != null) {
                                            AbstractBin abstractBin = locPosition.getPosBin()
                                            if (abstractBin != null) {
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
                    Grounded = true
                }
                if (Grounded) {
                    for (Unit unit : (units as List<Unit>)) {
                        if (unit != null && unit.getUnitLineOperator().equals(line)) {
                            UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
                            if (ufv != null) {
                                List unitDeli = ufv.getWorkInstsWithMoveKind(WiMoveKindEnum.Delivery)
                                if ((unitDeli == null || unitDeli.isEmpty()) && unit.getUnitCurrentlyAttachedChassisId() == null) {
                                    if (ufv.getUfvLastKnownPosition() != null) {
                                        LocPosition locPosition = ufv.getUfvLastKnownPosition()
                                        if (locPosition != null) {
                                            AbstractBin abstractBin = locPosition.getPosBin()
                                            if (abstractBin != null) {
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
                }
            }
        }
        return null;
    }
    private static Logger LOGGER = Logger.getLogger(ITSDeliverWheeledEmptiesOnPriority.class)
}
