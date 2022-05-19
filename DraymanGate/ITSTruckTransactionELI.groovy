import com.navis.argo.ContextHelper
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.external.framework.util.ExtensionUtils
import com.navis.road.RoadField
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.model.TruckTransaction
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @author <a href="mailto:sramasamy@weservetech.com"> Ramasamy Sathappan</a>
 * @since 18-May-2022
 *
 * On cancelling the transaction, send the drayman message
 *
 * */
class ITSTruckTransactionELI extends AbstractEntityLifecycleInterceptor {

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        process(inEntity, inOriginalFieldChanges, inMoreFieldChanges);
        //throw new Exception("Hold the process");
    }

    private void process(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        try {
            LOGGER.setLevel(Level.DEBUG);
            logMsg("inOriginalFieldChanges: "+inOriginalFieldChanges);

            def library = ExtensionUtils.getLibrary(ContextHelper.getThreadUserContext(), LIBRARY);
            if (library && inOriginalFieldChanges.hasFieldChange(RoadField.TRAN_STATUS)) {
                TruckTransaction truckTransaction = inEntity._entity;
                // On CANCEL the transaction
                logMsg("new val: "+inOriginalFieldChanges.findFieldChange(RoadField.TRAN_STATUS).getNewValue())
                if (TranStatusEnum.CANCEL == inOriginalFieldChanges.findFieldChange(RoadField.TRAN_STATUS).getNewValue()) {
                        LOGGER.debug("call prepareAndPushMessage for CANCEL tran");
                        library.prepareAndPushMessage(truckTransaction, T__CANCEL);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Exception in onUpdate : " + e.getMessage());
        }
    }


    private void logMsg(Object inMsg) {
        LOGGER.debug(inMsg);
    }

    private static final String T__CANCEL = "CANCEL";
    /*private static final String T__CREATE = "CREATE";
    private static final String T__COMPLETE = "COMPLETE";*/
    private static final String LIBRARY = "ITSDraymanGateAdaptor";

    private static final Logger LOGGER = Logger.getLogger(ITSTruckTransactionELI.class);

}