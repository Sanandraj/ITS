package ITSIntegration

import com.navis.argo.business.model.GeneralReference
import com.navis.billing.business.model.Customer
import com.navis.billing.business.model.InvoiceType
import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.FieldChanges
import com.navis.framework.presentation.FrameworkPresentationUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

class ITSInvoiceGenerateFormSubmissionCommand extends AbstractFormSubmissionCommand{
    private static Logger LOGGER = Logger.getLogger(ITSInvoiceGenerateFormSubmissionCommand.class)

    @Override
    void doBeforeSubmit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        Logger LOGGER1 = Logger.getLogger(this.class)
        LOGGER1.setLevel(Level.DEBUG)
        LOGGER1.debug("customBeanITSGenerateInvoiceFormController Starts :: ")
        FieldChanges fieldChanges = (FieldChanges) inOutFieldChanges
        LOGGER1.debug("fieldchanges form ::: "+fieldChanges)
        String invTypeNewValueGKEY = fieldChanges?.getFieldChange(MetafieldIdFactory.valueOf("invoiceInvoiceType"))?.getNewValue()
        LOGGER1.debug("invTypeNewValueGKEY :: "+invTypeNewValueGKEY)
        String payeeCustomer = fieldChanges?.getFieldChange(MetafieldIdFactory.valueOf("invoicePayeeCustomer"))?.getNewValue()
        LOGGER1.debug("payeeCustomer :: "+payeeCustomer)
        if (fieldChanges?.hasFieldChange(MetafieldIdFactory.valueOf("invoiceInvoiceType")) && invTypeNewValueGKEY != null) {
            PersistenceTemplate persistenceTemplate = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext())
            persistenceTemplate.invoke(new CarinaPersistenceCallback() {
                @Override
                protected void doInTransaction() {
                    InvoiceType invType = InvoiceType.hydrate(invTypeNewValueGKEY as Serializable)
                    LOGGER1.debug("invType :: "+invType?.getInvtypeId())
                    GeneralReference gn = GeneralReference.findUniqueEntryById("ITS_CUSTOMER_SERVICE",invType?.getInvtypeId()?.toString())
                    if (gn!=null && payeeCustomer != null){
                        LOGGER1.debug("Not null gn")
                        Customer customer= Customer.hydrate(payeeCustomer as Serializable)
                        LOGGER1.debug("customer ::"+customer)
                        if (customer.getCustDebitCode()==null || customer.getCustDebitCode().isEmpty() || !customer.getCustDebitCode().equals(gn?.getRefValue1())){
                            customer?.setFieldValue(MetafieldIdFactory.valueOf("custDebitCode"),gn?.getRefValue1())
                        }
                    }
                }
            })
        }
    }
}