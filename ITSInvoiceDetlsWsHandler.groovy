/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.business.api.ArgoUtils
import com.navis.billing.BillingField
import com.navis.billing.business.atoms.InvoiceStatusEnum
import com.navis.billing.business.model.Invoice
import com.navis.billing.business.model.InvoiceItem
import com.navis.billing.business.model.InvoiceParmValue
import com.navis.billing.business.model.Tariff
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
        Element rootElement = inECustom.getChild(INVOICES)
        Element inputDate = rootElement.getChild(INPUT_DATE)
        Element responseRoot = new Element(INVOICE_STATUS)
        inOutEResponse.addContent(responseRoot)
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT)
        ParsePosition pp = new ParsePosition(0)
        Date invoiceCreatedDate = dateFormat.parse(inputDate.getValue(), pp)

        if (invoiceCreatedDate != null) {
            responseRoot.setAttribute(STATUS, OK_STATUS_NO)
            responseRoot.setAttribute(STATUS_ID, OK_STATUS)
            MetafieldId metaFieldIdInvCreatedDate = MetafieldIdFactory.valueOf(INVOICE_CREATED)
            DomainQuery domainQuery = QueryUtils.createDomainQuery(INVOICE_ENTITY)
                    .addDqPredicate(PredicateFactory.gt(metaFieldIdInvCreatedDate, invoiceCreatedDate))
                    .addDqPredicate(PredicateFactory.eq(BillingField.INVOICE_STATUS, InvoiceStatusEnum.FINAL))
                    .addDqPredicate(PredicateFactory.eq(BillingField.INVOICE_FLEX_STRING05, null))
            List<Invoice> OutputList = HibernateApi.getInstance().findEntitiesByDomainQuery(domainQuery)
            if (OutputList != null && !OutputList.isEmpty()) {
                for (Invoice invoice : (OutputList as List<Invoice>)) {
                    responseRoot.addContent(fetchInvoiceDetails(invoice))
                    invoice.setFieldValue(MetafieldIdFactory.valueOf(INVOICE_FLEX_STRING05), COMPLETE)
                }
            } else {
                Element messages = new Element(MESSAGES)
                responseRoot.addContent(messages)
                Element message = new Element(MESSAGES)
                messages.addContent(message)
                responseRoot.setText(FAILURE_RESPONSE_TAG + "${inputDate.getValue()}")
                responseRoot.setAttribute(STATUS, NOT_OK_STATUS_NO)
                responseRoot.setAttribute(STATUS_ID, NOT_OK_STATUS)
            }
        }
    }

    private static fetchInvoiceDetails(Invoice invoice) {
        final Element responseRoot = new Element(INVOICE)
        responseRoot.setAttribute(UPDATE_TIME, ArgoUtils.timeNow().toString())
        /** type is always "TB" hence hard-coded*/
        responseRoot.setAttribute(BATCH_TYPE, TYPE_TB)

        Element company = new Element(COMPANY)
        responseRoot.addContent(company)
        Element companyNbr = new Element(COMPANY_NBR_STATUS)
        company.addContent(companyNbr)
        /** the company Nbr is standard hence hardcoded*/
        companyNbr.addContent(COMPANY_NBR)

        Element batchDate = new Element(BATCH_DATE)
        company.addContent(batchDate)
        /** the batch date fetch the present groovy job run time */
        batchDate.addContent(ArgoUtils.timeNow().toString())

        /** getting invoice param values from the customized field details(invoice generation) - reportable entity */
        List<InvoiceParmValue> invParmValue = InvoiceParmValue.getInvoiceParmValueByInvoice(invoice)
        List<String> parmList = new ArrayList<String>()
        if (invParmValue != null) {
            for (InvoiceParmValue invoiceParmValue : invParmValue) {
                String parmListValue = invoiceParmValue.getInvparmValue()
                parmList.add(parmListValue)
            }
        }
        Element invoices = new Element(INVOICE)
        responseRoot.addContent(invoices)
        Element invoiceDraftNumber = new Element(INVOICE_DRAFT_NUMBER)
        invoices.addContent(invoiceDraftNumber)
        Element invoiceType = new Element(INVOICE_TYPE)
        invoices.addContent(invoiceType)
        Element invoiceContractName = new Element(INVOICE_CONTRACT_NAME)
        if (invoice?.getInvoiceContract()?.getContractId()?.toString() != null) {
            invoices.addContent(invoiceContractName)
        }
        Element invoiceStatus = new Element(INV_STATUS)
        invoices.addContent(invoiceStatus)
        Element invoiceComplexId = new Element(INVOICE_COMPLEX_ID)
        invoices.addContent(invoiceComplexId)
        Element invoiceFinalNumber = new Element(INVOICE_FINAL_NUMBER)
        Element invoiceDate = new Element(INVOICE_FINAL_DATE)
        if (invoice?.getInvoiceFinalizedDate()?.toString() != null && invoice?.getInvoiceFinalNbr()?.toString() != null) {
            invoices.addContent(invoiceFinalNumber)
            invoices.addContent(invoiceDate)
        }
        Element invoiceVesselCode = new Element(VESSEL_CODE)
        if (parmList != null && parmList.size() > 0) {
            invoices.addContent(invoiceVesselCode)
        }
        Element invoiceTotalCharge = new Element(INVOICE_TOTAL_CHARGE)
        invoices.addContent(invoiceTotalCharge)
        Element invoiceTotalTaxes = new Element(INVOICE_TOTAL_TAXES)
        invoices.addContent(invoiceTotalTaxes)
        Element invoiceTotalDiscounts = new Element(INVOICE_TOTAL_DISCOUNTS)
        invoices.addContent(invoiceTotalDiscounts)
        Element invoiceTotalCredits = new Element(INVOICE_TOTAL_CREDITS)
        invoices.addContent(invoiceTotalCredits)
        Element invoiceTotalOwed = new Element(INVOICE_TOTAL_OWED)
        invoices.addContent(invoiceTotalOwed)
        Element invoicePaidThroughDate = new Element(INVOICE_PAID_THROUGH_DATE)
        if (invoice?.getInvoicePaidThruDay()?.toString() != null) {
            invoices.addContent(invoicePaidThroughDate)
        }
        Element invoiceDueDate = new Element(INVOICE_DUE_DATE)
        if (invoice?.getInvoiceDueDate()?.toString() != null) {
            invoices.addContent(invoiceDueDate)
        }
        Element invoiceEffectiveDate = new Element(INVOICE_EFFECTIVE_DATE)
        if (invoice?.getInvoiceEffectiveDate()?.toString() != null) {
            invoices.addContent(invoiceEffectiveDate)
        }
        Element invoiceIbId = new Element(INVOICE_IB_ID)
        Element invoiceObId = new Element(INVOICE_OB_ID)
        if (invoice?.getInvoiceKeyWord1()?.toString() != null && invoice?.getInvoiceKeyWord2()?.toString() != null) {
            invoices.addContent(invoiceIbId)
            invoices.addContent(invoiceObId)
        }
        Element invoiceIsMerged = new Element(INVOICE_IS_MERGED)
        invoices.addContent(invoiceIsMerged)
        Element invoiceGKey = new Element(INVOICE_GKEY)
        invoices.addContent(invoiceGKey)
        Element invoiceCurrency = new Element(INVOICE_CURRENCY)
        invoices.addContent(invoiceCurrency)
        Element invoiceCreated = new Element(INV_CREATED)
        invoices.addContent(invoiceCreated)
        Element invoiceNotes = new Element(INVOICE_NOTES)
        if (invoice?.getInvoiceNotes()?.toString() != null) {
            invoices.addContent(invoiceNotes)
        }
        Element invoiceCustomerNbr = new Element(INVOICE_CUSTOMER_NBR)
        Element invoiceCustomerName = new Element(INVOICE_CUSTOMER_NAME)
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
        if (invoiceItemList != null) {
            for (InvoiceItem invItem : invoiceItemList) {
                Element distribution = new Element(DISTRIBUTION)
                responseRoot.addContent(distribution)
                Element distributionCategoryNbr = new Element(DISTRIBUTION_CATEGORY_NBR)
                distribution.addContent(distributionCategoryNbr)
                Tariff tariff = Tariff.findTariff(invItem?.getItemTariff())
                distributionCategoryNbr.addContent(tariff?.getTariffFlexString01()?.toString())
                Element majorAccNbr = new Element(MAJOR_ACC_NBR)
                distribution.addContent(majorAccNbr)
                majorAccNbr.addContent(invItem?.getItemGlCode())
                Element subAccNbr = new Element(SUB_ACCOUT_NBR)
                distribution.addContent(subAccNbr)
                /** ITS Sub-Acc Nbr is Standard - hence "0" is hardCoded*/
                subAccNbr.addContent(SUB_ACC_NBR)
                Element distributionAmount = new Element(DISTRIBUTION_AMOUNT)
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
    private static final String INVOICE_FLEX_STRING05 = "invoiceFlexString05"
    private static final String COMPLETE = "COMPLETE"
    private static final String BATCH_TYPE = "TYPE"
    private static final String TYPE_TB = "TB"
    private static final String COMPANY_NBR = "1"
    private static final String UPDATE_TIME = "Update-Time"
    private static final String SUB_ACC_NBR = "0"
    private static final String FAILURE_RESPONSE_TAG = "No Data found for the Given Input Date :"
    private static final String INVOICES = "Invoices"
    private static final String INPUT_DATE = "InputDate"
    private static final String INVOICE_STATUS = "invoice-status"
    private static final String DATE_FORMAT = "yyyy-MM-dd"
    private static final String INVOICE_CREATED = "invoiceCreated"
    private static final String INVOICE_ENTITY = "Invoice"
    private static final String MESSAGES = "messages"
    private static final String INVOICE = "invoice"
    private static final String COMPANY = "company"
    private static final String COMPANY_NBR_STATUS = "companyNbr"
    private static final String BATCH_DATE = "batchDate"
    private static final String INVOICE_DRAFT_NUMBER = "invoiceDraftNumber"
    private static final String INVOICE_TYPE = "invoiceType"
    private static final String INVOICE_CONTRACT_NAME = "invoiceContractName"
    private static final String INV_STATUS = "invoiceStatus"
    private static final String INVOICE_COMPLEX_ID = "invoiceComplexId"
    private static final String INVOICE_FINAL_NUMBER = "invoiceFinalNumber"
    private static final String INVOICE_FINAL_DATE = "invoiceFinalDate"
    private static final String VESSEL_CODE = "vesselCode"
    private static final String INVOICE_TOTAL_CHARGE = "invoiceTotalCharge"
    private static final String INVOICE_TOTAL_TAXES = "invoiceTotalTaxes"
    private static final String INVOICE_TOTAL_DISCOUNTS = "invoiceTotalDiscounts"
    private static final String INVOICE_TOTAL_CREDITS = "invoiceTotalCredits"
    private static final String INVOICE_TOTAL_OWED = "invoiceTotalOwed"
    private static final String INVOICE_PAID_THROUGH_DATE = "invoicePaidThroughDate"
    private static final String INVOICE_DUE_DATE = "invoiceDueDate"
    private static final String INVOICE_EFFECTIVE_DATE = "invoiceEffectiveDate"
    private static final String INVOICE_IB_ID = "invoiceIbId"
    private static final String INVOICE_OB_ID = "invoiceObId"
    private static final String INVOICE_IS_MERGED = "invoiceIsMerged"
    private static final String INVOICE_GKEY = "invoiceGKey"
    private static final String INVOICE_CURRENCY = "invoiceCurrency"
    private static final String INV_CREATED = "invoiceCreated"
    private static final String INVOICE_NOTES = "invoiceNotes"
    private static final String INVOICE_CUSTOMER_NBR = "invoiceCustomerNbr"
    private static final String INVOICE_CUSTOMER_NAME = "invoiceCustomerName"
    private static final String DISTRIBUTION = "distribution"
    private static final String DISTRIBUTION_CATEGORY_NBR = "distributionCategoryNbr"
    private static final String MAJOR_ACC_NBR = "majorAccNbr"
    private static final String SUB_ACCOUT_NBR = "subAccNbr"
    private static final String DISTRIBUTION_AMOUNT = "distributionAmount"
}
