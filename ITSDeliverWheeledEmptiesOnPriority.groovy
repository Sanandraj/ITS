package ITS

import com.navis.argo.business.atoms.EquipClassEnum
import com.navis.argo.business.atoms.EquipNominalLengthEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.units.Unit
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger


/**
 * @author <ahref="mailto:vsathish@weservetech.com"      >      Sathish V</a>
 *
 * Wheeled empties take priority for delivery when a truck arrives bobtail.
 *
 * Configuration: Add under 'Code Extension' tab as GATE_TASK_INTERCEPTOR type
 *
 */

class ITSDeliverWheeledEmptiesOnPriority extends AbstractGateTaskInterceptor {

    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSDeliverWheeledEmptiesOnPriority STARTS::")
        TruckTransaction inTran = inWfCtx.getTran()
        ScopedBizUnit inLinOp = inTran.getTranAppointment()?.getGapptLineOperator()
        String isoCd = null
        EquipmentOrderItem odrItm = inTran.getTranEqoItem()
        if (odrItm != null) {
            isoCd = odrItm.getEqoiSampleEquipType()?.getEqtypId()
        }
        if (inLinOp != null && isoCd != null) {
            if (inTran.getTranCtrNbrAssigned() == null && inTran.getTranChsNbr() == null) {
                Unit inUnit = findWheeledUnitsPriority(inLinOp, isoCd, true, false)
                if (inUnit != null) {
                    inTran.setTranCtrNbrAssigned(inUnit.getUnitId())
                    if (inUnit.getUnitCurrentlyAttachedChassisId()) {
                        inTran.setTranChsNbr(inUnit.getUnitCurrentlyAttachedChassisId())
                    } else {
                        Unit inChsUnit = findEquivalentChs(inUnit.getUnitEquipment()?.getEqEquipType()?.getEqtypNominalLength())
                        if (inChsUnit != null) {
                            inTran.setTranChsNbr(inChsUnit.getUnitId())
                        }
                    }
                }
            } else if (inTran.getTranCtrNbrAssigned() == null) {
                Unit inUnit = findWheeledUnitsPriority(inLinOp, isoCd, false, true)
                if (inUnit != null) {
                    inTran.setTranCtrNbrAssigned(inUnit.getUnitId())
                }
            }
        }
        executeInternal(inWfCtx);
    }

    private Unit findWheeledUnitsPriority(ScopedBizUnit lineOp, String isoCd, Boolean Wheeled, Boolean Grounded) {

        DomainQuery dq = QueryUtils.createDomainQuery("Unit")
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CATEGORY, UnitCategoryEnum.STORAGE))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_LINE_OPERATOR, lineOp.getPrimaryKey()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_EQ_ISO_CODE, "4510"))

        LOGGER.debug("Dq units ::" + dq)
        List<Unit> units = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        LOGGER.debug("units ::" + units)
        if (units != null && units.size() > 0) {
            if (Wheeled) {
                for (Unit unit : (units as List<Unit>)) {
                    //Unit chssisUnit = ((unit.unitRelatedUnit != null) && (unit.unitRelatedUnit.unitEqRole == EqUnitRoleEnum.CARRIAGE) ? unit.unitRelatedUnit : null)
                    if (unit.getUnitActiveUfvNowActive()?.getFinalPlannedPosition() == null && unit.findCurrentPosition()?.isWheeled() && unit.getUnitCurrentlyAttachedChassisId() != null) {
                        LOGGER.debug("unit Attached Chassis ::" + unit.getUnitCurrentlyAttachedChassisId())
                        return unit
                    }
                }
                Grounded = true
            }
            if (Grounded) {
                for (Unit unit : (units as List<Unit>)) {
                    LOGGER.debug("unit ::" + unit)
                    if (unit.getUnitActiveUfvNowActive()?.getFinalPlannedPosition() == null && unit.getUnitCurrentlyAttachedChassisId() == null)
                        return unit
                }
            }
        }
        return null;
    }

    private Unit findEquivalentChs(EquipNominalLengthEnum inLngth) {
        DomainQuery dq = QueryUtils.createDomainQuery("Unit")
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_EQ_NOMINAL_LENGTH, inLngth))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_EQ_CLASS, EquipClassEnum.CHASSIS))
        LOGGER.debug("Dq units ::" + dq)
        List<Unit> units = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        LOGGER.debug("units ::" + units)
        if (units != null && units.size() > 0) {
            for (Unit unit : (units as List<Unit>)) {
                LOGGER.debug("Is Chs Free::" + unit.getUnitCurrentlyAttachedEquipIds())
                if (unit.getUnitCurrentlyAttachedEquipIds() == null) {
                    return unit
                }
            }
        }
        return null
    }

    private static Logger LOGGER = Logger.getLogger(ITSDeliverWheeledEmptiesOnPriority.class)

}
