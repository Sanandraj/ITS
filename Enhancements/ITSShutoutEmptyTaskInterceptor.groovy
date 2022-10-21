import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.Container
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Logger

/**
 * 09/15 uaarthi@weservetech.com - Back 8-5 Shutout Empties based on Line./Arch ISO
 */
class ITSShutoutEmptyTaskInterceptor extends AbstractGateTaskInterceptor implements EGateTaskInterceptor {
    private PropertyKey MTY_REFUSED = PropertyKeyFactory.valueOf("MTY_REFUSED")
    private static final Logger LOGGER = Logger.getLogger(this.class);

    void execute(TransactionAndVisitHolder inWfCtx) {
        executeInternal(inWfCtx);
        TruckTransaction truckTransaction = inWfCtx.getTran()
        if (truckTransaction != null) {
            blockMtyReceive(truckTransaction);
        }
    }

    def blockMtyReceive(tran){
        Container container = tran.getTranContainer()
        String lineOp = null;
        String iso = null
        if (tran.getTranLine() != null) {
            lineOp = tran.getTranLine().getBzuId()
        } else if (container != null) {
            lineOp = container.getEquipmentOperatorId()
        }
        if (tran.getTranEquipType() != null && tran.getTranEquipType().getEqtypArchetype() != null) {
            iso = tran.getTranEquipType().getEqtypArchetype().getEqtypId()
        } else if (container != null && container.getEqEquipType() != null) {
            iso = container.getEqEquipType().getEqtypArchetype().getEqtypId()
        }
        LOGGER.warn("iso "+iso)
        LOGGER.warn("lineOp "+lineOp)
        if (iso != null && lineOp != null) {
            GeneralReference generalReference = GeneralReference.findUniqueEntryById("MTY_QUOTA", "RCV_EMPTIES", lineOp, iso)
            LOGGER.warn("generalReference "+generalReference)
            // LOGGER.warn("generalReference "+generalReference.getRefValue1())

            if (generalReference != null && !("YES".equalsIgnoreCase(generalReference.getRefValue1()))) {
                Object[] params = new Object[2];
                params[0] = lineOp;
                params[1] = iso;
                getMessageCollector().appendMessage(MessageLevel.SEVERE, MTY_REFUSED, "Empty Container for Line ${lineOp} and ISO ${iso} not allowed.", params)
            }
        }
    }
}
