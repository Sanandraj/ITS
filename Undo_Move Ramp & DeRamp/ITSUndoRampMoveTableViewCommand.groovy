import com.navis.extension.portal.ExtensionBeanUtils
import com.navis.extension.portal.IExtensionTransactionHandler
import com.navis.external.framework.ui.AbstractTableViewCommand
import com.navis.framework.extension.FrameworkExtensionTypes
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.presentation.context.PresentationContextUtils
import com.navis.framework.presentation.ui.event.listener.AbstractCarinaOptionCommand
import com.navis.framework.presentation.ui.message.ButtonType
import com.navis.framework.presentation.ui.message.ButtonTypes
import com.navis.framework.presentation.ui.message.MessageType
import com.navis.framework.presentation.ui.message.OptionDialog
import com.navis.framework.util.message.MessageCollector
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author <ahref="mailto:mharikumar@weservetech.com"  >  Harikumar M</a>,
 * Date : 16/Sep/2022
 * Table view command groovy to undo a move. Calls UndoMoveCallBack
 */

class ITSUndoRampMoveTableViewCommand extends AbstractTableViewCommand {
    private static Logger LOGGER = Logger.getLogger(ITSUndoRampMoveTableViewCommand.class);

    @Override
    void execute(EntityId inEntityId, List<Serializable> inGkeys, Map<String, Object> inParams) {
        //LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSUndoRampMoveTableViewCommand Execution starts")
        Map inParam = new HashMap();
        Map outParam = new HashMap();
        if (inGkeys != null && inGkeys.size() > 0) {

            OptionDialog.showMessage("Are you sure you want to undo the selected moves?", "Undo Move", ButtonTypes.YES_NO, MessageType.QUESTION_MESSAGE, new AbstractCarinaOptionCommand() {
                @Override
                protected void safeExecute(ButtonType inOption) {
                    if (ButtonType.YES.equals(inOption)) {
                        inParam.put("gkeys", inGkeys);
                        inParam.put("action", "Ramp")

                        IExtensionTransactionHandler handler = ExtensionBeanUtils.getExtensionTransactionHandler();
                        MessageCollector mc = handler.executeInTransaction(PresentationContextUtils.getRequestContext().getUserContext(),
                                FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, "ITSUndoMoveCallBack", inParam, outParam);

                        int successCount = outParam.get("Success")
                        String errorMsg = outParam.get("ErrorMsg")
                        if (successCount > 0) {
                            OptionDialog.showMessage("Reverted UNIT_RAMP event move successfully", "Undo Move", ButtonTypes.OK, "informationMessage", null);
                        } else {
                            OptionDialog.showMessage("Unable to revert UNIT_RAMP move\n" + errorMsg, "Undo Move", ButtonTypes.OK, "informationMessage", null);
                        }
                    }
                }
            });
        }
    }
}
