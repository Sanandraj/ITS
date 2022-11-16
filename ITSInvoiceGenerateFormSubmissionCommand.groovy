/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

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

/**
 * @Author: Kishore Kumar S <a href= skishore@weservetech.com / >, 10/10/2022
 * Requirements : B-2-3 General reference to maintain customer code -- This groovy is used to update customer debit code on customer data .
 * @Inclusion Location	: Incorporated as a code extension of the type FORM_SUBMISSION_INTERCEPTOR.
 *  Load Code Extension to N4:
 1. Go to Administration --> System -->  Code Extension
 2. Click Add (+)
 3. Enter the values as below:
 Code Extension Name:  ITSInvoiceGenerateFormSubmissionCommand.
 Code Extension Type:  FORM_SUBMISSION_INTERCEPTOR.
 Groovy Code: Copy and paste the contents of groovy code.
 4. Click Save button
 *
 *  Set up override configuration in variformId - BIL006.
 */

class ITSInvoiceGenerateFormSubmissionCommand extends AbstractFormSubmissionCommand{
    private static Logger LOGGER = Logger.getLogger(ITSInvoiceGenerateFormSubmissionCommand.class)

    @Override
    void doBeforeSubmit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        Logger LOGGER1 = Logger.getLogger(this.class)
        LOGGER1.setLevel(Level.DEBUG)
        LOGGER1.debug("customBeanITSGenerateInvoiceFormController Starts :: ")
        FieldChanges fieldChanges = (FieldChanges) inOutFieldChanges
        if (fieldChanges == null){
            return;
        }
        String invTypeNewValueGKEY = fieldChanges?.getFieldChange(MetafieldIdFactory.valueOf("invoiceInvoiceType"))?.getNewValue()
        String payeeCustomer = fieldChanges?.getFieldChange(MetafieldIdFactory.valueOf("invoicePayeeCustomer"))?.getNewValue()
        if (fieldChanges?.hasFieldChange(MetafieldIdFactory.valueOf("invoiceInvoiceType")) && invTypeNewValueGKEY != null) {
            PersistenceTemplate persistenceTemplate = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext())
            persistenceTemplate.invoke(new CarinaPersistenceCallback() {
                @Override
                protected void doInTransaction() {
                    InvoiceType invType = InvoiceType.hydrate(invTypeNewValueGKEY as Serializable)
                    GeneralReference gn = GeneralReference.findUniqueEntryById("ITS_CUSTOMER_SERVICE",invType?.getInvtypeId()?.toString())
                    if (gn!=null && payeeCustomer != null){
                        Customer customer= Customer.hydrate(payeeCustomer as Serializable)
                        if (customer.getCustDebitCode()==null || customer.getCustDebitCode().isEmpty() || !customer.getCustDebitCode().equals(gn?.getRefValue1())){
                            customer?.setFieldValue(MetafieldIdFactory.valueOf("custDebitCode"),gn?.getRefValue1())
                        }
                    }
                }
            })
        }
    }
}
