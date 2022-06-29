package ITSIntegration

import com.navis.argo.ContextHelper
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.reference.Accessory
import com.navis.argo.business.reference.Chassis
import com.navis.argo.business.reference.Equipment
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.orders.business.eqorders.Booking
import com.navis.road.business.atoms.GateClientTypeEnum
import com.navis.road.business.model.TruckDriver
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import com.navis.framework.business.Roastery;

/**
 * Author: <a href="mailto:smohanbabu@weservetech.com">Mohan Babu</a>
 *
 * Description: This groovy script will depart additional gensets out of facility
 */
class ITSDepartAdditionalGensets extends AbstractGateTaskInterceptor{

    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        TruckTransaction tran = inWfCtx.getTran()
        boolean autoGate = GateClientTypeEnum.AUTOGATE == inWfCtx.getGateClientType()
        if(tran != null && autoGate && tran.isDelivery() && tran.getConfig().isOutStage(tran.getTranStageId()) ){
            TruckVisitDetails tv = inWfCtx.getTv()
            if(tv != null){
                String additionalGensets = tv.getTvdtlsFlexString01()
                if(additionalGensets != null && StringUtils.isNotEmpty(additionalGensets)){
                    UnitFinder unitFinder = getUnitFinder()
                    String [] gensets = additionalGensets.split(",")
                    CarrierVisit cv = tv.getCvdCv()
                    LocPosition pos = LocPosition.createLocPosition(cv,null,null)
                    for(String genset : gensets){
                        Equipment equipment = Equipment.findEquipment(genset)
                        if(equipment != null){
                            Unit accUnit = unitFinder.findActiveUnit(ContextHelper.getThreadComplex(), equipment)
                            if(accUnit != null){
                                if(!"TRUCK".equalsIgnoreCase(accUnit.getUnitActiveUfvNowActive().getUfvLastKnownPosition().getPosLocType().getKey())){
                                    accUnit.move(pos)
                                }
                                accUnit.deliverOutOfFacility(ContextHelper.getThreadFacility())
                            }
                            else{
                                LOGGER.error("Accessory Unit not found - " + genset)
                                registerError("Accessory Unit not found - " + genset)
                            }
                        }
                        else{
                            LOGGER.error("Equipment not found")
                            registerError("Equipment - " + genset + "not found")
                        }
                    }
                }
                tv.setTvdtlsFlexString01(null)
            }
            else{
                LOGGER.warn("Tran unit null")
            }
        }
        else{
            LOGGER.debug("Tran is null or not auto gate -" + autoGate.toString())
        }
    }
    private UnitFinder getUnitFinder() {
        return (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID)
    }
    private static Logger LOGGER = Logger.getLogger(this.class);
}
