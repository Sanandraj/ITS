package ITSIntegration

import com.navis.argo.business.model.GeneralReference
import com.navis.billing.business.model.InvoiceType
import com.navis.billing.presentation.controller.GenerateInvoiceFormController
import com.navis.external.framework.beans.EBean
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.BizResponse
import com.navis.framework.portal.FieldChanges
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.framework.presentation.ui.command.ISubmitFormCommand
import com.navis.framework.presentation.ui.command.SubmitFormCommand
import org.apache.log4j.Level
import org.apache.log4j.Logger

class customBeanITSGenerateInvoiceFormController extends GenerateInvoiceFormController implements EBean{

    @Override
    ISubmitFormCommand getCreateUpdateDataCommand(){
        new SubmitFormCommand(this) {
            @Override
            BizResponse doBeforeSubmit(String inEntityName, Serializable inEntityGkey, FieldChanges inFieldChanges) {
                Logger LOGGER1 = Logger.getLogger(this.class)
                LOGGER1.setLevel(Level.DEBUG)
                LOGGER1.debug("customBeanITSGenerateInvoiceFormController Starts :: ")
                FieldChanges fieldChange = (FieldChanges) inFieldChanges
                LOGGER1.debug("FieldChanges ::"+fieldChange)
                String invTypeNewValueGKEY = fieldChange.getFieldChange(MetafieldIdFactory.valueOf("invoiceInvoiceType")).getNewValue()
                LOGGER1.debug("invTypeNewValueGKEY ::"+invTypeNewValueGKEY)
                if (fieldChange.hasFieldChange(MetafieldIdFactory.valueOf("invoiceInvoiceType"))) {
                    if (invTypeNewValueGKEY != null) {
                        LOGGER1.debug("Inside If:: ")
                        PersistenceTemplate persistenceTemplate = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext())
                        persistenceTemplate.invoke(new CarinaPersistenceCallback() {
                            @Override
                            protected void doInTransaction() {
                                InvoiceType invType = InvoiceType.hydrate(invTypeNewValueGKEY as Serializable)
                                LOGGER1.debug("invType :: "+invType)
                                GeneralReference gn = GeneralReference.findUniqueEntryById("ITS_CUSTOMER_SERVICE",invType.getInvtypeId())
                                LOGGER1.debug("Gen Ref ::"+gn)
                                if (gn!=null){
                                    fieldChange.setFieldChange(MetafieldIdFactory.valueOf("invoiceInvoiceType"),gn.getRefValue1())
                                    LOGGER1.debug("Code Ends")
                                }
                            }
                        })
                    }
                }
                getFormUiProcessor().hideWindow(this.getFormController())
                return new BizResponse()
            }
        }
    }
    private static final Logger LOGGER = Logger.getLogger(customBeanITSGenerateInvoiceFormController.class)
}