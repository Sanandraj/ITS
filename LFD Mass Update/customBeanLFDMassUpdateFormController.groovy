import com.navis.extension.portal.ExtensionBeanUtils
import com.navis.extension.portal.IExtensionTransactionHandler
import com.navis.external.framework.beans.EBean
import com.navis.framework.extension.FrameworkExtensionTypes
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.portal.BizResponse
import com.navis.framework.portal.FieldChanges
import com.navis.framework.presentation.context.PresentationContextUtils
import com.navis.framework.presentation.ui.FormController
import com.navis.framework.presentation.ui.ICarinaWidget
import com.navis.framework.presentation.ui.command.ISubmitFormCommand
import com.navis.framework.presentation.ui.command.SubmitFormCommand
import com.navis.framework.presentation.ui.event.CarinaFormValueEvent
import com.navis.framework.presentation.ui.event.listener.AbstractCarinaFormValueListener
import com.navis.framework.presentation.ui.event.listener.AbstractCarinaOptionCommand
import com.navis.framework.presentation.ui.message.ButtonType
import com.navis.framework.presentation.ui.message.ButtonTypes
import com.navis.framework.presentation.ui.message.MessageType
import com.navis.framework.presentation.ui.message.OptionDialog
import com.navis.framework.util.message.MessageCollector
import org.apache.log4j.Level
import org.apache.log4j.Logger

class customBeanLFDMassUpdateFormController extends FormController implements EBean {
    @Override
    boolean initForm() {
        LOGGER.warn("customBeanLFDMassUpdateFormController initform.")
        return super.initForm()
    }

    @Override
    protected void configure() {
        super.configure()

        LOGGER.setLevel(Level.DEBUG)
        ICarinaWidget flexWidget = getFormWidget(MetafieldIdFactory.valueOf("ufvFlexString01"));
        ICarinaWidget lfdWidget = getFormWidget(MetafieldIdFactory.valueOf("ufvLastFreeDay"));

        if (flexWidget != null) {
            flexWidget.setVisible(false);
            flexWidget.setRequired(false);
        }
        if (lfdWidget != null) {
            lfdWidget.addFormValueListener(new AbstractCarinaFormValueListener() {
                protected void safeValueChanged(CarinaFormValueEvent inEvent) {

                    String lfdValue = lfdWidget.getUiValue();
                    if (lfdValue != null) {
                        //flexWidget.setEnabled(true);
                        flexWidget.setVisible(true);
                        flexWidget.setRequired(true);
                    }
                }
            });
        }

    }


    @Override
    ISubmitFormCommand getCreateUpdateDataCommand() {
        new SubmitFormCommand(this) {
            @Override
            BizResponse doBeforeSubmit(String inEntityName, Serializable inEntityGkey, FieldChanges inOutFieldChanges) {
                return super.doBeforeSubmit(inEntityName, inEntityGkey, inOutFieldChanges)
            }

            @Override
            BizResponse submit(String inEntityName, Serializable inEntityGkey, FieldChanges inOutFieldChanges) {
                Logger LOGGER1 = Logger.getLogger(this.class)
                LOGGER1.setLevel(Level.DEBUG)
                Map map = new HashMap()
                Map outResult = new HashMap()
                map.put("GKEY", getPrimaryKeys())
                map.put("FIELD_CHANGES", inOutFieldChanges);
                LOGGER1.warn("map" + map)
                OptionDialog.showMessage("Do you still want to update LFD?", "LFD Mass Update", ButtonTypes.YES_NO, MessageType.QUESTION_MESSAGE, new AbstractCarinaOptionCommand() {
                    @Override
                    protected void safeExecute(ButtonType inOption) {
                        if (ButtonType.YES.equals(inOption)) {

                            IExtensionTransactionHandler handler = ExtensionBeanUtils.getExtensionTransactionHandler()
                            MessageCollector messageCollector = handler.executeInTransaction(PresentationContextUtils.getRequestContext().getUserContext(), FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, "ITSLFDUpdatePersisitanceCallBack", map, outResult)

                            LOGGER1.warn("  error" + outResult.get("ERROR"))
                            LOGGER1.warn("STRING_BUILDER" + outResult.get("STRING_BUILDER"))

                            if (outResult.get("ERROR") != null) {
                                LOGGER1.warn(" inside error" + outResult.get("ERROR"))
                                if (outResult.get("STRING_BUILDER") != null && !outResult.get("STRING_BUILDER").toString().isEmpty()) {
                                    LOGGER1.warn("map" + outResult.get("STRING_BUILDER"))
                                    OptionDialog.showMessage(outResult.get("STRING_BUILDER").toString(), "LFD Mass Update", ButtonTypes.OK, MessageType.WARNING_MESSAGE, null)
                                    return
                                }
                            }
                        }
                    }
                })
                getFormUiProcessor().hideWindow(this.getFormController())
                return new BizResponse()
            }

            @Override
            void doAfterSubmit(BizResponse inOutBizResponse, String inEntityName, Serializable inEntityGkey, FieldChanges inOutFieldChanges) {
                super.doAfterSubmit(inOutBizResponse, inEntityName, inEntityGkey, inOutFieldChanges)
            }
        }
    }


    private static final Logger LOGGER = Logger.getLogger(customBeanLFDMassUpdateFormController.class);

}
