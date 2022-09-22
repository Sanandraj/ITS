import com.navis.argo.ContextHelper
import com.navis.argo.business.reference.Container
import com.navis.argo.business.reference.Equipment
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.atoms.TransactionClassEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author <a href="mailto:skishore@weservetech.com">KISHORE KUMAR S</a>
 */
class ITSCompleteDropOffOutGate extends AbstractGateTaskInterceptor{
    private static final Logger LOGGER = Logger.getLogger(ITSCompleteDropOffOutGate.class)
    void execute(TransactionAndVisitHolder inWfCtx) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSCompleteDropOffOutGate Starts::")
        TruckTransaction truckTransaction = inWfCtx.getTran()
        if (truckTransaction != null && truckTransaction.isDelivery()) {
            TruckVisitDetails truckVisitDetails = truckTransaction.getTranTruckVisit()
            LOGGER.debug("truckVisitDetails :: "+truckVisitDetails)
            if (truckVisitDetails != null) {
                Set<TruckTransaction> dropOffContainers = truckVisitDetails.getTransactionsToBeHandled(TransactionClassEnum.DROPOFF)
                LOGGER.debug("dropOffContainers :: "+dropOffContainers)
                for (TruckTransaction tran : dropOffContainers) {
                    if(TranStatusEnum.OK.equals(tran.getTranStatus())) {
                        tran.setTranStatus(TranStatusEnum.COMPLETE)
                        String containerId= tran.getTranContainer().getEqIdFull()
                        Container container = Container.findContainer(containerId)
                        Equipment equipment = container != null ? Equipment.findEquipment(containerId) : null
                        Unit unit = getFinder().findActiveUnit(ContextHelper.getThreadComplex(),equipment)
                        LOGGER.debug("Unit :: "+unit)
                        if (unit!=null){
                            unit.setFieldValue(MetafieldIdFactory.valueOf("unitFlexString04"),"Yes")
                            LOGGER.debug("containerId :: "+containerId)
                            LOGGER.debug("ITSCompleteDropOffOutGate Code Ends")
                        }
                        
                    }else {
                        String containerId= tran.getTranContainer().getEqIdFull()
                        Container container = Container.findContainer(containerId)
                        Equipment equipment = container != null ? Equipment.findEquipment(containerId) : null
                        Unit unit = getFinder().findActiveUnit(ContextHelper.getThreadComplex(),equipment)
                        LOGGER.debug("Unit :: "+unit)
                        if (unit!=null){
                            unit.setFieldValue(MetafieldIdFactory.valueOf("unitFlexString04"),"No")
                            LOGGER.debug("containerId :: "+containerId)
                            LOGGER.debug("ITSCompleteDropOffOutGate Code Ends")
                        }
                    }
                }
            }
        }
    }
    private static UnitFinder getFinder() {
        return (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
    }
}
