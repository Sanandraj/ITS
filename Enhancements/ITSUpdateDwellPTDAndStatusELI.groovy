/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.billing.BillingField
import com.navis.billing.business.atoms.InvoiceStatusEnum
import com.navis.billing.business.model.Invoice
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.external.framework.util.ExtensionUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author: mailto:annalakshmig@weservetech.com, AnnaLakshmi G; Date: 12/DEC/2022
 *
 * Requirements :IP-407, 7-10 Waiver or Guarantee Extended Dwell Fee
 *
 * @Inclusion Location : Incorporated as a code extension of the type ENTITY_LIFECYCLE_INTERCEPTION
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name:ITSUpdateDwellPTDAndStatusELI
 *     Code Extension Type:ENTITY_LIFECYCLE_INTERCEPTION
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *
 *  S.No    Modified Date        Modified By       Jira      Description
 */

class ITSUpdateDwellPTDAndStatusELI extends AbstractEntityLifecycleInterceptor {

    public void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        LOG.setLevel(Level.DEBUG);
        Invoice invoice = (Invoice) inEntity._entity;
        if (null == invoice) {
            return
        }
        try {
            createOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges, Boolean.FALSE)
        } catch (Throwable e) {
            e.printStackTrace()
            LOG.debug("ITSUpdateDwellPTDAndStatusELI on create Exception", e)
        }
        LOG.debug("inOriginalFieldChanges on create" + inOriginalFieldChanges)

    }

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        LOG.setLevel(Level.DEBUG);
        Invoice invoice = (Invoice) inEntity._entity;
        if (null == invoice) {
            return
        }
        try {
            createOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges, Boolean.FALSE)
        } catch (Throwable e) {
            e.printStackTrace()
            LOG.error("ITSUpdateDwellPTDAndStatusELI on update Exception", e)
        }
        LOG.debug("inOriginalFieldChanges on Update " + inOriginalFieldChanges)


    }

    private void createOrUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges,
                                EFieldChanges inMoreFieldChanges, Boolean isDelete) {
        Invoice invoice = (Invoice) inEntity._entity;
        if (inOriginalFieldChanges.hasFieldChange(BillingField.INVOICE_STATUS)) {
            InvoiceStatusEnum invStatus = (InvoiceStatusEnum) inEntity.getField(BillingField.INVOICE_STATUS)
            LOG.debug("invStatus" + invStatus)
            InvoiceStatusEnum invoiceStatusEnumNew = (InvoiceStatusEnum) inOriginalFieldChanges.findFieldChange(BillingField.INVOICE_STATUS).getNewValue()
            InvoiceStatusEnum invoiceStatusEnumOld = (InvoiceStatusEnum) inOriginalFieldChanges.findFieldChange(BillingField.INVOICE_STATUS).getPriorValue()
            LOG.debug("invoiceStatusEnumNew" + invoiceStatusEnumNew)
            LOG.debug("invoiceStatusEnumOld" + invoiceStatusEnumOld)
            if (InvoiceStatusEnum.FINAL.equals(invStatus)) {
                def library = ExtensionUtils.getLibrary(getUserContext(), "ITSUpdateDwellPTDAndStatusLibrary");
                LOG.debug("calling ITSUpdateDwellPTDAndStatusLibrary on finalize")
                library.updateExtendedDwellStatusAndPTD(inEntity, isDelete)
            }
        }
    }

    @Override
    public void validateDelete(EEntityView inEntity) {
        def library = ExtensionUtils.getLibrary(getUserContext(), "ITSUpdateDwellPTDAndStatusLibrary");
        library.updateExtendedDwellStatusAndPTD(inEntity, Boolean.TRUE)
    }

    private static Logger LOG = Logger.getLogger(this.class)

}
