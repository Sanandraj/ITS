/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/
import com.navis.extension.portal.ExtensionBeanUtils
import com.navis.extension.portal.IExtensionTransactionHandler
import com.navis.external.framework.beans.EBean
import com.navis.framework.extension.FrameworkExtensionTypes
import com.navis.framework.portal.BizResponse
import com.navis.framework.portal.FieldChanges
import com.navis.framework.presentation.context.PresentationContextUtils
import com.navis.framework.presentation.ui.FormController
import com.navis.framework.presentation.ui.IFormController
import com.navis.framework.presentation.ui.command.ISubmitFormCommand
import com.navis.framework.presentation.ui.command.SubmitFormCommand
import com.navis.framework.presentation.ui.event.listener.AbstractCarinaOptionCommand
import com.navis.framework.presentation.ui.message.ButtonType
import com.navis.framework.presentation.ui.message.ButtonTypes
import com.navis.framework.presentation.ui.message.MessageType
import com.navis.framework.presentation.ui.message.OptionDialog
import com.navis.framework.util.message.MessageCollector
import org.apache.log4j.Logger

/*
* @Author: mailto:mharikumar@weservetech.com, Harikumar M; Date: 20/10/2022
*
*  Requirements: To bulk update Line Last free Day for Units with reason
*
* @Inclusion Location: Incorporated as a code extension of the type
*
*  Load Code Extension to N4:
*  1. Go to Administration --> System --> Code Extensions
*  2. Click Add (+)
*  3. Enter the values as below:
*     Code Extension Name: customBeanLFDMassUpdateFormController
*     Code Extension Type: BEAN_PROTOTYPE
*     Groovy Code: Copy and paste the contents of groovy code.
*  4. Click Save button
*
* @Setup Create a custom Variform CUSTOM_INV075
*
*  S.No    Modified Date   Modified By     Jira      Description
*
*/

class customBeanLFDMassUpdateFormController extends FormController implements EBean {


    @Override
    boolean initForm() {
        LOGGER.debug("customBeanLFDMassUpdateFormController initform.")
        return super.initForm()
    }

    @Override
    protected void configure() {
        super.configure()
    }


    @Override
    ISubmitFormCommand getCreateUpdateDataCommand() {
        new SubmitFormCommand(this as IFormController) {
            @Override
            BizResponse doBeforeSubmit(String inEntityName, Serializable inEntityGkey, FieldChanges inOutFieldChanges) {
                return super.doBeforeSubmit(inEntityName, inEntityGkey, inOutFieldChanges)
            }

            @Override
            BizResponse submit(String inEntityName, Serializable inEntityGkey, FieldChanges inOutFieldChanges) {
                final String LIB = "ITSLFDUpdatePersisitanceCallBack"
                final String STRING_BUILDER = "STRING_BUILDER"
                Map map = new HashMap()
                Map outResult = new HashMap()
                map.put("GKEY", getPrimaryKeys())
                map.put("FIELD_CHANGES", inOutFieldChanges)
                OptionDialog.showMessage("Do you still want to update LFD?", "LFD Mass Update", ButtonTypes.YES_NO, MessageType.QUESTION_MESSAGE, new AbstractCarinaOptionCommand() {
                    @Override
                    protected void safeExecute(ButtonType inOption) {
                        if (ButtonType.YES.equals(inOption)) {
                            IExtensionTransactionHandler handler = ExtensionBeanUtils.getExtensionTransactionHandler()
                            MessageCollector messageCollector = handler.executeInTransaction(PresentationContextUtils.getRequestContext().getUserContext(), FrameworkExtensionTypes.TRANSACTED_BUSINESS_FUNCTION, LIB, map, outResult)

                            if (outResult.get("ERROR") != null) {
                                if (outResult.get(STRING_BUILDER) != null && !outResult.get(STRING_BUILDER).toString().isEmpty()) {
                                    OptionDialog.showMessage(outResult.get(STRING_BUILDER).toString(), "LFD Mass Update", ButtonTypes.OK, MessageType.WARNING_MESSAGE, null)
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


    private static final Logger LOGGER = Logger.getLogger(customBeanLFDMassUpdateFormController.class)

}
