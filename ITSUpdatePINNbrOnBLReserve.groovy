import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.cargo.business.model.BillOfLading
import com.navis.cargo.business.model.BlGoodsBl
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.api.InventoryCargoManager
import com.navis.inventory.business.units.Unit
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger


/*
 * 
 * Requirements : This groovy is used to retrieve the last 5 letters from BL and set it as Pin number in Unit and clears the PIN Number when * BL is disassociating for a unit.
 *
 * @Inclusion Location	: Incorporated as a code extension of the type GENERAL_NOTICE_CODE_EXTENSION.
 * Copy --> Paste this code (ITSUpdatePINNbrOnBLReserve.groovy)
 *
 * @Set up in the general notice against UNIT_RESERVE and UNIT_CANCEL_RESERVE and execute the code extension - ITSUpdatePINNbrOnBLReserve.
 */

class ITSUpdatePINNbrOnBLReserve extends  AbstractGeneralNoticeCodeExtension {
    private static final Logger LOGGER = Logger.getLogger(ITSUpdatePINNbrOnBLReserve.class)

    @Override
    public void execute(GroovyEvent inGroovyEvent) {
        LOGGER.info("ITSUpdatePinNbrOnBLReserve: Started execution")
        LOGGER.setLevel(Level.DEBUG)
        Unit unit = (Unit) inGroovyEvent.getEntity()
        logMsg("Unit :"+unit)
        if (unit != null && unit.getUnitGoods() != null){
            try{
                ContextHelper.setThreadDataSource(DataSourceEnum.EDI_MNFST)
                InventoryCargoManager inventoryCargoManager = (InventoryCargoManager) Roastery.getBean(InventoryCargoManager.BEAN_ID)
                Set<BillOfLading> blSet = (Set<BillOfLading>) inventoryCargoManager.getBlsForGoodsBl(unit)
                logMsg("Associated BL(s) :"+blSet)
                if (blSet != null && blSet.size() > 0){
                    BlGoodsBl targetBL =null
                    for (int i=0; i<blSet.size(); i++){
                        BillOfLading bl = blSet.getAt(i)
                        if (bl != null){
                            BlGoodsBl blGoodsBl = BlGoodsBl.findBlGoodsBl(unit,bl)
                            if (targetBL == null){
                                targetBL = blGoodsBl
                            } else if (targetBL.getBlgdsblCreated() < blGoodsBl.getBlgdsblCreated()){
                                targetBL = blGoodsBl
                            }
                        }
                    }
                    if (targetBL != null && targetBL.getBlgdsblBl() && unit.getUnitRouting() != null){
                        logMsg("Recently assigned BL(s) : " + targetBL)
                        if (unit.getUnitRouting().getRtgPinNbr() == null || (unit.getUnitRouting().getRtgPinNbr() != null && !unit.getUnitRouting().getRtgPinNbr().equalsIgnoreCase(targetBL.getBlgdsblBl().getNbr().substring(targetBL.getBlgdsblBl().getNbr().length() - 5))))
                            unit.getUnitRouting().setRtgPinNbr(targetBL.getBlgdsblBl().getNbr().substring(targetBL.getBlgdsblBl().getNbr().length() - 5))
                        logMsg("Latest PIN : " + unit.getUnitRouting().getRtgPinNbr())
                    }
                }else {
                    logMsg("No BL(s) associated with unit.")
                    unit.getUnitRouting().setRtgPinNbr(null)
                }
            }
            catch (Exception inEx){
                logMsg("Pin number cannot be assigned to Unit " + unit.getUnitId() + " with error " + inEx.toString())
            }
            HibernateApi.getInstance().save(unit)
            HibernateApi.getInstance().flush()
        }
        LOGGER.info("ITSUpdatePinNbrOnBLReserve: Completed Execution")
    }

    private static void logMsg(String inMsg) {
        LOGGER.debug("ITSUpdatePinNbrOnBLReserve: " + inMsg)
    }
}