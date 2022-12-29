package ITS.Enhancements

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
 * @Author: annalakshmig@weservetech.com, Annalakshmi G; Date: 12-12-2022
 *
 *  Requirements:
 *
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

    private static final Logger LOG = Logger.getLogger(this.class)

}
