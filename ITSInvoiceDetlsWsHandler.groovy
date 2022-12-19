/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.business.api.ArgoUtils
import com.navis.billing.BillingField
import com.navis.billing.business.atoms.InvoiceStatusEnum
import com.navis.billing.business.model.*
import com.navis.external.argo.AbstractArgoCustomWSHandler
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.message.MessageCollector
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jdom.Element

import java.text.ParsePosition
import java.text.SimpleDateFormat

/** @Author: Kishore Kumar S <a href= skishore@weservetech.com / >, 05/11/2022
 * Requirements : IP-277, ITS Job for JMS Queue Services (Microsoft GP)
 * @Inclusion Location	: Incorporated as a code extension of the type WS_ARGO_CUSTOM_HANDLER.
 *  Load Code Extension to N4:
 1. Go to Administration --> System -->  Code Extension
 2. Click Add (+)
 3. Enter the values as below:
 Code Extension Name:  ITSInvoiceDetlsWsHandler.
 Code Extension Type:  WS_ARGO_CUSTOM_HANDLER.
 Groovy Code: Copy and paste the contents of groovy code.
 4. Click Save button
 *
 *  Request:
    <custom class= "ITSInvoiceDetlsWsHandler" type="extension">
    <Invoices>
    <InputDate>?</InputDate>
    </Invoices>
    </custom>
*/

class ITSInvoiceDetlsWsHandler extends AbstractArgoCustomWSHandler {
    @Override
    void execute(final UserContext userContext,
                 final MessageCollector messageCollector,
                 final Element inECustom, final Element inOutEResponse, final Long inWsLogGKey) {
        LOGGER.setLevel(Level.DEBUG)
        Element rootElement = inECustom.getChild("Invoices")
        Element inputDate = rootElement.getChild("InputDate")
        inputDate.getNamespace()
        Element responseRoot = new Element("invoice-status")
        inOutEResponse.addContent(responseRoot)

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd")
        ParsePosition pp = new ParsePosition(0)
        Date invoiceCreatedDate = dateFormat.parse(inputDate.getValue(), pp)

        if (invoiceCreatedDate != null) {
            responseRoot.setAttribute(STATUS, OK_STATUS_NO)
            responseRoot.setAttribute(STATUS_ID, OK_STATUS)
            MetafieldId metaFieldIdInvCreatedDate = MetafieldIdFactory.valueOf("invoiceCreated")
            DomainQuery domainQuery = QueryUtils.createDomainQuery("Invoice")
                    .addDqPredicate(PredicateFactory.gt(metaFieldIdInvCreatedDate, invoiceCreatedDate))
                    .addDqPredicate(PredicateFactory.eq(BillingField.INVOICE_STATUS, InvoiceStatusEnum.FINAL))
                    .addDqPredicate(PredicateFactory.eq(BillingField.INVOICE_FLEX_STRING05, null))
            List<Invoice> OutputList = HibernateApi.getInstance().findEntitiesByDomainQuery(domainQuery)
            if (OutputList != null && !OutputList.isEmpty()) {
                for (Invoice invoice : (OutputList as List<Invoice>)) {
                    responseRoot.addContent(fetchInvoiceDetails(invoice))
                    invoice.setFieldValue(MetafieldIdFactory.valueOf("invoiceFlexString05"), "COMPLETE")
                }
            } else {
                Element messages = new Element("messages")
                responseRoot.addContent(messages)
                Element message = new Element("message")
                messages.addContent(message)
                responseRoot.setText("No Data found for the Given Input Date : ${inputDate.getValue()}")
                responseRoot.setAttribute(STATUS, NOT_OK_STATUS_NO)
                responseRoot.setAttribute(STATUS_ID, NOT_OK_STATUS)
            }
        }
    }

    private static fetchInvoiceDetails(Invoice invoice) {
        final Element responseRoot = new Element("invoice")
        responseRoot.setAttribute("update_time", ArgoUtils.timeNow().toString())
        // type is always "TB" hence hard-coded
        responseRoot.setAttribute("type", "TB")

        Element company = new Element("company")
        responseRoot.addContent(company)
        Element companyNbr = new Element("companyNbr")
        company.addContent(companyNbr)
        // the company Nbr is standard hence hardcoded
        companyNbr.addContent("1")

        Element batchDate = new Element("batchDate")
        company.addContent(batchDate)
        /** the batch date fetch the present groovy job run time */
        batchDate.addContent(ArgoUtils.timeNow().toString())

        InvoiceType invType = InvoiceType.findInvoiceType(invoice.getInvoiceInvoiceType().getInvtypeId())
        Set invSavedFieldsSet = invType.getInvtypeSavedFields()
        LOGGER.debug("invSavedFieldsSet :: " + invSavedFieldsSet)
        for (InvoiceTypeSavedField savedField : (invSavedFieldsSet as List<InvoiceTypeSavedField>)) {
            LOGGER.debug("savedFieldDetail :: " + savedField.getFieldValue(MetafieldIdFactory.valueOf("invtypfldName")))
        }

        /** getting invoice param values from the customized field details(invoice generation) - reportable entity */
        List<InvoiceParmValue> invParmValue = InvoiceParmValue.getInvoiceParmValueByInvoice(invoice)
        LOGGER.debug("invParmValue :: " + invParmValue)
        List<String> parmList = new ArrayList<String>()
        if (invParmValue != null) {
            for (InvoiceParmValue invoiceParmValue : invParmValue) {
                String parmListValue = invoiceParmValue.getInvparmValue()
                parmList.add(parmListValue)
            }
        }
        Element invoices = new Element("invoice")
        responseRoot.addContent(invoices)
        Element invoiceDraftNumber = new Element("invoiceDraftNumber")
        invoices.addContent(invoiceDraftNumber)
        Element invoiceType = new Element("invoiceType")
        invoices.addContent(invoiceType)
        Element invoiceContractName = new Element("invoiceContractName")
        if (invoice?.getInvoiceContract()?.getContractId()?.toString() != null) {
            invoices.addContent(invoiceContractName)
        }
        Element invoiceStatus = new Element("invoiceStatus")
        invoices.addContent(invoiceStatus)
        Element invoiceComplexId = new Element("invoiceComplexId")
        invoices.addContent(invoiceComplexId)
        Element invoiceFinalNumber = new Element("invoiceFinalNumber")
        Element invoiceDate = new Element("invoiceFinalDate")
        if (invoice?.getInvoiceFinalizedDate()?.toString() != null && invoice?.getInvoiceFinalNbr()?.toString() != null) {
            invoices.addContent(invoiceFinalNumber)
            invoices.addContent(invoiceDate)
        }
        Element invoiceVesselCode = new Element("vesselCode")
        if (parmList != null && parmList.size() > 0) {
            invoices.addContent(invoiceVesselCode)
        }
        Element invoiceTotalCharge = new Element("invoiceTotalCharge")
        invoices.addContent(invoiceTotalCharge)
        Element invoiceTotalTaxes = new Element("invoiceTotalTaxes")
        invoices.addContent(invoiceTotalTaxes)
        Element invoiceTotalDiscounts = new Element("invoiceTotalDiscounts")
        invoices.addContent(invoiceTotalDiscounts)
        Element invoiceTotalCredits = new Element("invoiceTotalCredits")
        invoices.addContent(invoiceTotalCredits)
        Element invoiceTotalOwed = new Element("invoiceTotalOwed")
        invoices.addContent(invoiceTotalOwed)
        Element invoicePaidThroughDate = new Element("invoicePaidThroughDate")
        if (invoice?.getInvoicePaidThruDay()?.toString() != null) {
            invoices.addContent(invoicePaidThroughDate)
        }
        Element invoiceDueDate = new Element("invoiceDueDate")
        if (invoice?.getInvoiceDueDate()?.toString() != null) {
            invoices.addContent(invoiceDueDate)
        }
        Element invoiceEffectiveDate = new Element("invoiceEffectiveDate")
        if (invoice?.getInvoiceEffectiveDate()?.toString() != null) {
            invoices.addContent(invoiceEffectiveDate)
        }
        Element invoiceIbId = new Element("invoiceIbId")
        Element invoiceObId = new Element("invoiceObId")
        if (invoice?.getInvoiceKeyWord1()?.toString() != null && invoice?.getInvoiceKeyWord2()?.toString() != null) {
            invoices.addContent(invoiceIbId)
            invoices.addContent(invoiceObId)
        }
        Element invoiceIsMerged = new Element("invoiceIsMerged")
        invoices.addContent(invoiceIsMerged)
        Element invoiceGKey = new Element("invoiceGKey")
        invoices.addContent(invoiceGKey)
        Element invoiceCurrency = new Element("invoiceCurrency")
        invoices.addContent(invoiceCurrency)
        Element invoiceCreated = new Element("invoiceCreated")
        invoices.addContent(invoiceCreated)
        Element invoiceNotes = new Element("invoiceNotes")
        if (invoice?.getInvoiceNotes()?.toString() != null) {
            invoices.addContent(invoiceNotes)
        }
        Element invoiceCustomerNbr = new Element("invoiceCustomerNbr")
        Element invoiceCustomerName = new Element("invoiceCustomerName")
        if (invoice?.getInvoicePayeeCustomer()?.getCustDebitCode()?.toString() != null) {
            invoices.addContent(invoiceCustomerNbr)
            if (invoice?.getInvoicePayeeCustomer()?.getCustName()?.toString() != null) {
                invoices.addContent(invoiceCustomerName)
            }
        }

        invoiceDraftNumber?.addContent(invoice?.getInvoiceDraftNbr()?.toString())
        invoiceType?.addContent(invoice?.getInvoiceInvoiceType()?.getInvtypeId()?.toString())

        if (invoice?.getInvoiceContract()?.getContractId()?.toString() != null) {
            invoiceContractName?.addContent(invoice?.getInvoiceContract()?.getContractId()?.toString())
        }
        invoiceStatus?.addContent(invoice?.getInvoiceStatus()?.getKey()?.toString())
        invoiceComplexId?.addContent(invoice?.getLogEntityComplex()?.getCpxId()?.toString())

        if (invoice?.getInvoiceFinalizedDate()?.toString() != null && invoice?.getInvoiceFinalNbr()?.toString() != null) {
            invoiceDate?.addContent(invoice?.getInvoiceFinalizedDate()?.toString())
            invoiceFinalNumber?.addContent(invoice?.getInvoiceFinalNbr()?.toString())
        }
        if (parmList != null && parmList.size() > 0 && parmList?.get(0) != null) {
            invoiceVesselCode?.addContent(parmList?.get(0)?.replaceAll("[^A-Z]", ""))
        }
        invoiceTotalCharge?.addContent(invoice.getInvoiceTotalCharges().toString())
        invoiceTotalTaxes?.addContent(invoice?.getInvoiceTotalTaxes()?.toString())
        invoiceTotalDiscounts?.addContent(invoice?.getInvoiceTotalDiscounts()?.toString())
        invoiceTotalCredits?.addContent(invoice?.getInvoiceTotalCredits()?.toString())
        invoiceTotalOwed?.addContent(invoice?.getInvoiceTotalOwed()?.toString())
        if (invoice?.getInvoicePaidThruDay()?.toString() != null) {
            invoicePaidThroughDate?.addContent(invoice?.getInvoicePaidThruDay()?.toString())
        }
        if (invoice?.getInvoiceDueDate()?.toString() != null) {
            invoiceDueDate?.addContent(invoice?.getInvoiceDueDate()?.toString())
        }
        if (invoice?.getInvoiceEffectiveDate()?.toString() != null) {
            invoiceEffectiveDate?.addContent(invoice?.getInvoiceEffectiveDate()?.toString())
        }
        if (invoice?.getInvoiceKeyWord1()?.toString() != null && invoice?.getInvoiceKeyWord2()?.toString() != null) {
            invoiceIbId?.addContent(invoice?.getInvoiceKeyWord1()?.toString())
            invoiceObId?.addContent(invoice?.getInvoiceKeyWord2()?.toString())
        }
        invoiceIsMerged?.addContent(invoice?.getIsInvoiceMerged()?.toString())
        invoiceGKey?.addContent(invoice?.getInvoiceGkey()?.toString())
        invoiceCurrency?.addContent(invoice?.getInvoiceCurrency()?.getCurrencyId()?.toString())
        if (invoice?.getInvoicePayeeCustomer()?.getCustDebitCode()?.toString() != null) {
            invoiceCustomerNbr?.addContent(invoice?.getInvoicePayeeCustomer()?.getCustDebitCode()?.toString())
            if (invoice?.getInvoicePayeeCustomer()?.getCustName()?.toString() != null) {
                invoiceCustomerName?.addContent(invoice?.getInvoicePayeeCustomer()?.getCustName()?.toString())
            }
        }
        invoiceCreated?.addContent(invoice?.getInvoiceCreated()?.toString())
        if (invoice?.getInvoiceNotes()?.toString() != null) {
            invoiceNotes?.addContent(invoice?.getInvoiceNotes()?.toString())
        }
        List<InvoiceItem> invoiceItemList = InvoiceItem.getInvoiceItemByInvoice(invoice)
        LOGGER.debug("invoiceItemList :: " + invoiceItemList)
        if (invoiceItemList != null) {
            for (InvoiceItem invItem : invoiceItemList) {
                Element distribution = new Element("distribution")
                responseRoot.addContent(distribution)
                Element distributionCategoryNbr = new Element("distributionCategoryNbr")
                distribution.addContent(distributionCategoryNbr)
                Tariff tariff = Tariff.findTariff(invItem?.getItemTariff())
                LOGGER.debug("tariff :: " + tariff)
                distributionCategoryNbr.addContent(tariff?.getTariffFlexString01()?.toString())
                Element majorAccNbr = new Element("majorAccNbr")
                distribution.addContent(majorAccNbr)
                majorAccNbr.addContent(invItem?.getItemGlCode())
                Element subAccNbr = new Element("subAccNbr")
                distribution.addContent(subAccNbr)
                // ITS Sub-Acc Nbr is Standard - hence "0" is hardCoded
                subAccNbr.addContent("0")
                Element distributionAmount = new Element("distributionAmount")
                distribution.addContent(distributionAmount)
                distributionAmount.addContent(invItem.getItemAmount().toString())
            }
        }
        return responseRoot
    }
    private static Logger LOGGER = Logger.getLogger(this.class)
    private static final String STATUS = "status"
    private static final String STATUS_ID = "status-id"
    private static final String OK_STATUS = "ok"
    private static final String OK_STATUS_NO = "ok"
    private static final String NOT_OK_STATUS = "no_invoices_found"
    private static final String NOT_OK_STATUS_NO = "error"
}
