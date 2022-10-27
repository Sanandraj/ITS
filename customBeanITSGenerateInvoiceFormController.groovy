package ITSIntegration

import com.navis.argo.business.model.GeneralReference
import com.navis.billing.business.model.Customer
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

/**Author: <a href="mailto:skishore@weservetech.com"> KISHORE KUMAR S </a>
 * Date : 27-01-2022
 * Description: This Code will be paste against BEAN_PROTOTYPE Code Extension Type - This code performs updating cutDebitCode value(Customer Number - Customer Entity) by general reference value.
 **/

class customBeanITSGenerateInvoiceFormController extends GenerateInvoiceFormController implements EBean{
    @Override
    ISubmitFormCommand getCreateUpdateDataCommand(){
        new SubmitFormCommand(this) {
            @Override
            BizResponse doBeforeSubmit(String inEntityName, Serializable inEntityGkey, FieldChanges inFieldChanges) {
                Logger LOGGER1 = Logger.getLogger(this.class)
                LOGGER1.setLevel(Level.DEBUG)
                LOGGER1.debug("customBeanITSGenerateInvoiceFormController Starts :: ")
                FieldChanges fieldChanges = (FieldChanges) inFieldChanges
                String invTypeNewValueGKEY = fieldChanges?.getFieldChange(MetafieldIdFactory.valueOf(INVOICE_INVOICE_TYPE))?.getNewValue()
                String payeeCustomer = fieldChanges?.getFieldChange(MetafieldIdFactory.valueOf(INVOICE_PAYEE_CUSTOMER))?.getNewValue()
                if (fieldChanges?.hasFieldChange(MetafieldIdFactory.valueOf(INVOICE_INVOICE_TYPE)) && invTypeNewValueGKEY != null) {
                    PersistenceTemplate persistenceTemplate = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext())
                    persistenceTemplate.invoke(new CarinaPersistenceCallback() {
                        @Override
                        protected void doInTransaction() {
                            InvoiceType invType = InvoiceType.hydrate(invTypeNewValueGKEY as Serializable)
                            GeneralReference gn = GeneralReference.findUniqueEntryById(ITS_CUSTOMER_SERVICE,invType?.getInvtypeId())
                            if (gn!=null && payeeCustomer != null){
                                Customer customer= Customer.hydrate(payeeCustomer as Serializable)
                                if (customer.getCustDebitCode()==null || customer.getCustDebitCode().isEmpty() || !customer.getCustDebitCode().equals(gn?.getRefValue1())){
                                    customer?.setFieldValue(MetafieldIdFactory.valueOf(CUST_DEBIT_CODE),gn?.getRefValue1())
                                }
                            }
                        }
                    })
                }
                getFormUiProcessor()?.hideWindow(this.getFormController())
                return new BizResponse()
            }
        }
    }
    @Override
    String getDetailedDiagnostics() {
        return "customBeanITSGenerateInvoiceFormController"
    }
    public static final String INVOICE_INVOICE_TYPE = "invoiceInvoiceType"
    public static final String INVOICE_PAYEE_CUSTOMER = "invoicePayeeCustomer"
    public static final String CUST_DEBIT_CODE = "custDebitCode"
    public static final String ITS_CUSTOMER_SERVICE = "ITS_CUSTOMER_SERVICE"
}