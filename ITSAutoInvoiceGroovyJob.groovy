import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.LogicalEntityEnum
import com.navis.argo.business.integration.IntegrationServiceMessage
import com.navis.argo.business.model.ArgoSequenceProvider
import com.navis.billing.BillingField
import com.navis.billing.business.atoms.InvoiceStatusEnum
import com.navis.billing.business.model.Invoice
import com.navis.billing.business.model.InvoiceItem
import com.navis.billing.business.model.InvoiceParmValue
import com.navis.billing.business.model.InvoiceType
import com.navis.billing.business.model.InvoiceTypeSavedField
import com.navis.billing.business.model.Tariff
import com.navis.carina.integrationservice.business.IntegrationService
import com.navis.external.argo.AbstractGroovyJobCodeExtension
import com.navis.framework.IntegrationServiceField
import com.navis.framework.business.atoms.IntegrationServiceDirectionEnum
import com.navis.framework.business.atoms.IntegrationServiceTypeEnum
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.metafields.MetafieldIdList
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.HibernatingEntity
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.portal.query.PredicateIntf
import com.navis.framework.util.scope.ScopeCoordinates
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jdom.Document
import org.jdom.Element
import org.jdom.output.Format
import org.jdom.output.XMLOutputter

/**
 * @Author <a href="mailto:skishore@weservetech.com">KISHORE KUMAR S</a>
 */

class ITSAutoInvoiceGroovyJob extends AbstractGroovyJobCodeExtension {
    @Override
    void execute(Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSAutoInvoiceGroovyJob Starts :: ")
        MetafieldId metaFieldId_Inv_JMS = MetafieldIdFactory.valueOf("invoiceFlexString02")
        DomainQuery dqInvoice = QueryUtils.createDomainQuery("Invoice")
                .addDqPredicate(PredicateFactory.isNull(metaFieldId_Inv_JMS))
                .addDqPredicate(PredicateFactory.eq(BillingField.INVOICE_STATUS, InvoiceStatusEnum.FINAL))
        List<Invoice> outputList = (List<Invoice>) HibernateApi.getInstance().findEntitiesByDomainQuery(dqInvoice)
        LOGGER.debug("outputList :: " + outputList)

        for (Invoice invoice : outputList as List<Invoice>) {
            LOGGER.debug("invoice ::" + invoice)
            MetafieldId metafieldId = MetafieldIdFactory.valueOf("customFlexFields.invtypeCustomDFFGPDeliverables")
            if ("Y".equals(invoice?.getInvoiceInvoiceType()?.getFieldValue(metafieldId))) {
                List<IntegrationService> integrationServiceList = getInvoiceDetailsSyncIntegrationServices("ITSDEVOUTBOUND", false)
                if (outputList != null) {
                    for (IntegrationService integrationService : integrationServiceList) {
                        LOGGER.debug("integrationService ::" + integrationService)
                        Element requestMessage
                        LOGGER.debug("integrationService ::" + integrationService)
                        requestMessage = fetchInvoiceDetails(invoice)
                        LOGGER.debug("requestMessage ::" + requestMessage)
                        if (requestMessage != null) {
                            LOGGER.debug("requestMessage is not null")
                            logRequestToInterfaceMessage(invoice, LogicalEntityEnum.NA, integrationService, requestMessage)
                            LOGGER.debug("Inside for Setting Flex Values")
                            invoice.setFieldValue(metaFieldId_Inv_JMS, "true")
                            LOGGER.debug("True Value Set")
                        }
                    }
                }
            }
        }
    }

    private
    static IntegrationServiceMessage logRequestToInterfaceMessage(HibernatingEntity hibernatingEntity, LogicalEntityEnum inLogicalEntityEnum,
                                                                  IntegrationService inIntegrationService, Element inMessagePayload) {


        IntegrationServiceMessage integrationServiceMessage = new IntegrationServiceMessage()
        integrationServiceMessage.setIsmEventPrimaryKey((Long) hibernatingEntity.getPrimaryKey())
        integrationServiceMessage.setIsmEntityClass(inLogicalEntityEnum)
        integrationServiceMessage.setIsmEntityNaturalKey(hibernatingEntity.getHumanReadableKey())
        try {
            if (inIntegrationService) {
                integrationServiceMessage.setIsmIntegrationService(inIntegrationService)
                integrationServiceMessage.setIsmFirstSendTime(ArgoUtils.timeNow())
            }
            if (inMessagePayload.toString() < (3900 as String)) {
                integrationServiceMessage.setIsmMessagePayload(inMessagePayload.toString())
            } else {
                integrationServiceMessage.setIsmMessagePayloadBig(inMessagePayload.toString())
            }
            integrationServiceMessage.setIsmSeqNbr(new IntegrationServMessageSequenceProvider().getNextSequenceId())
            ScopeCoordinates scopeCoordinates = ContextHelper.getThreadUserContext().getScopeCoordinate()
            integrationServiceMessage.setIsmScopeGkey((String) scopeCoordinates.getScopeLevelCoord(scopeCoordinates.getDepth()))
            integrationServiceMessage.setIsmScopeLevel(scopeCoordinates.getDepth())
            integrationServiceMessage.setIsmUserString3("false")
            HibernateApi.getInstance().save(integrationServiceMessage)
            HibernateApi.getInstance().flush()

        } catch (Exception e) {
            LOGGER.debug("Exception while saving ISM" + e)
        }
        return integrationServiceMessage
    }
    static class IntegrationServMessageSequenceProvider extends ArgoSequenceProvider {
        Long getNextSequenceId() {
            return super.getNextSeqValue(serviceMsgSequence, ContextHelper.getThreadFacilityKey() != null ? (Long) ContextHelper.getThreadFacilityKey() : 1l)
        }
        private String serviceMsgSequence = "INT_MSG_SEQ"
    }

    private static fetchInvoiceDetails(Invoice invoice) {
        final Element responseRoot = new Element("Invoice")
        responseRoot.setAttribute("Update_Time", ArgoUtils.timeNow().toString())
        // type is always "TB" hence hard-coded
        responseRoot.setAttribute("Type", "TB")


        MetafieldId metafieldId_Inv_Batch_Id = MetafieldIdFactory.valueOf("invoiceFlexString03")
        LOGGER.debug("metafieldId_Inv_Batch_Id ::" + metafieldId_Inv_Batch_Id)
        DomainQuery dq = QueryUtils.createDomainQuery("Invoice")
                .addDqPredicate(PredicateFactory.isNotNull(metafieldId_Inv_Batch_Id))
                .addDqOrdering(Ordering.desc(metafieldId_Inv_Batch_Id))
        List<Invoice> jobBatchList = (List<Invoice>) HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        LOGGER.debug("jobBatchList :: " + jobBatchList)
        Invoice batchMax = jobBatchList.get(0)
        LOGGER.debug("batchMax :: " + batchMax)
        String flexStringValue = batchMax.getInvoiceFlexString03()
        LOGGER.debug("flexStringValue :: " + flexStringValue)
        Long parseLong = Long.parseLong(flexStringValue) + 1
        invoice.setFieldValue(metafieldId_Inv_Batch_Id, parseLong.toString())
        LOGGER.debug("parseLong ::" + parseLong)

        Element company = new Element("Company")
        responseRoot.addContent(company)
        Element companyNbr = new Element("companyNbr")
        company.addContent(companyNbr)
        // the company Nbr is standard hence hardcoded
        companyNbr.addContent("1")

        Element batchNbr = new Element("batchNbr")
        company.addContent(batchNbr)
        batchNbr.addContent(parseLong.toString())

        Element batchDate = new Element("batchDate")
        company.addContent(batchDate)
        /** the batch date fetch the present groovy job run time */
        batchDate.addContent(ArgoUtils.timeNow().toString())

        String invDraftNbr = invoice?.getInvoiceDraftNbr()?.toString()
        String invFinalDate = invoice?.getInvoiceFinalizedDate()?.toString()
        String invTotalCharges = invoice.getInvoiceTotalCharges().toString()
        String invDueDate = invoice?.getInvoiceDueDate()?.toString()
        String invEffectiveDate = invoice?.getInvoiceEffectiveDate()?.toString()
        String invIs_Merged = invoice?.getIsInvoiceMerged()?.toString()
        String inv_GKey = invoice?.getInvoiceGkey()?.toString()
        String inv_Currency = invoice?.getInvoiceCurrency()?.getCurrencyId()?.toString()
        String inv_Notes = invoice?.getInvoiceNotes()?.toString()
        String inv_Type = invoice?.getInvoiceInvoiceType()?.getInvtypeId()?.toString()
        String inv_Total_taxes = invoice?.getInvoiceTotalTaxes()?.toString()
        String inv_Total_Owed = invoice?.getInvoiceTotalOwed()?.toString()
        String inv_Total_Discounts = invoice?.getInvoiceTotalDiscounts()?.toString()
        String inv_Total_Credits = invoice?.getInvoiceTotalCredits()?.toString()
        String inv_Status = invoice?.getInvoiceStatus()?.getKey()?.toString()
        String inv_Final_Number = invoice?.getInvoiceFinalNbr()?.toString()
        String inv_ComplexId = invoice?.getLogEntityComplex()?.getCpxId()?.toString()
        String inv_Paid_Thru_Date = invoice?.getInvoicePaidThruDay()?.toString()
        String inv_Cust_Id = invoice?.getInvoicePayeeCustomer()?.getCustDebitCode()?.toString()
        String inv_Cust_Role = invoice?.getInvoicePayeeCustomer()?.getCustName()?.toString()
        String inv_Created = invoice?.getInvoiceCreated()?.toString()
        String inv_KeyWord1 = invoice?.getInvoiceKeyWord1()?.toString()
        String inv_KeyWord2 = invoice?.getInvoiceKeyWord2()?.toString()
        String inv_Contract_Name = invoice?.getInvoiceContract()?.getContractId()?.toString()

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
        if (inv_Contract_Name != null) {
            invoices.addContent(invoiceContractName)
        }
        Element invoiceStatus = new Element("invoiceStatus")
        invoices.addContent(invoiceStatus)
        Element invoiceComplexId = new Element("invoiceComplexId")
        invoices.addContent(invoiceComplexId)
        Element invoiceFinalNumber = new Element("invoiceFinalNumber")
        Element invoiceDate=new Element("invoiceFinalDate")
        if (invFinalDate != null && inv_Final_Number != null){
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
        if (inv_Paid_Thru_Date != null){
            invoices.addContent(invoicePaidThroughDate)
        }
        Element invoiceDueDate = new Element("invoiceDueDate")
        if (invDueDate!=null){
            invoices.addContent(invoiceDueDate)
        }
        Element invoiceEffectiveDate = new Element("invoiceEffectiveDate")
        if (invEffectiveDate!=null){
            invoices.addContent(invoiceEffectiveDate)
        }
        Element invoiceIbId = new Element("invoiceIbId")
        Element invoiceObId = new Element("invoiceObId")
        if (inv_KeyWord1 != null && inv_KeyWord2 != null) {
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
        if (inv_Notes != null) {
            invoices.addContent(invoiceNotes)
        }
        Element invoiceCustomerNbr = new Element("invoiceCustomerNbr")
        Element invoiceCustomerName = new Element("invoiceCustomerName")
        if (inv_Cust_Id != null) {
            invoices.addContent(invoiceCustomerNbr)
            if (inv_Cust_Role != null) {
                invoices.addContent(invoiceCustomerName)
            }
        }

        invoiceDraftNumber?.addContent(invDraftNbr)
        invoiceType?.addContent(inv_Type)

        if (inv_Contract_Name != null) {
            invoiceContractName?.addContent(inv_Contract_Name)
        }
        invoiceStatus?.addContent(inv_Status)
        invoiceComplexId?.addContent(inv_ComplexId)

        if (invFinalDate != null && inv_Final_Number != null) {
            invoiceDate?.addContent(invFinalDate)
            invoiceFinalNumber?.addContent(inv_Final_Number)
        }
        if (parmList != null && parmList.size() > 0) {
            invoiceVesselCode?.addContent(parmList.get(1).replaceAll("[^A-Z]", ""))
        }
        invoiceTotalCharge?.addContent(invTotalCharges)
        invoiceTotalTaxes?.addContent(inv_Total_taxes)
        invoiceTotalDiscounts?.addContent(inv_Total_Discounts)
        invoiceTotalCredits?.addContent(inv_Total_Credits)
        invoiceTotalOwed?.addContent(inv_Total_Owed)
        if (inv_Paid_Thru_Date != null) {
            invoicePaidThroughDate?.addContent(inv_Paid_Thru_Date)
        }
        if (invDueDate != null) {
            invoiceDueDate?.addContent(invDueDate)
        }
        if (invEffectiveDate != null) {
            invoiceEffectiveDate?.addContent(invEffectiveDate)
        }
        if (inv_KeyWord1 != null && inv_KeyWord2 != null) {
            invoiceIbId?.addContent(inv_KeyWord1)
            invoiceObId?.addContent(inv_KeyWord2)
        }
        invoiceIsMerged?.addContent(invIs_Merged)
        invoiceGKey?.addContent(inv_GKey)
        invoiceCurrency?.addContent(inv_Currency)
        if (inv_Cust_Id != null) {
            invoiceCustomerNbr?.addContent(inv_Cust_Id)
            if (inv_Cust_Role != null) {
                invoiceCustomerName?.addContent(inv_Cust_Role)
            }
        }
        invoiceCreated?.addContent(inv_Created)
        if (inv_Notes != null) {
            invoiceNotes?.addContent(inv_Notes)
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
        XMLOutputter xmlOutputter = new XMLOutputter(Format.getPrettyFormat())
        String finalResponse = xmlOutputter.outputString(new Document(responseRoot))
        LOGGER.debug("finalResponse ::" + finalResponse)
        return responseRoot
    }

    private static List<IntegrationService> getInvoiceDetailsSyncIntegrationServices(String integrationServiceName, boolean isGroup) {
        MetafieldIdList metafieldIdList = new MetafieldIdList(3)
        metafieldIdList.add(IntegrationServiceField.INTSERV_URL)
        metafieldIdList.add(IntegrationServiceField.INTSERV_USER_ID)
        metafieldIdList.add(IntegrationServiceField.INTSERV_PASSWORD)
        PredicateIntf namePredicate
        if (isGroup) {
            namePredicate = PredicateFactory.eq(IntegrationServiceField.INTSERV_GROUP, integrationServiceName)
        } else {
            namePredicate = PredicateFactory.eq(IntegrationServiceField.INTSERV_NAME, integrationServiceName)
        }

        DomainQuery dq = QueryUtils.createDomainQuery("IntegrationService").addDqFields(metafieldIdList)
                .addDqPredicate(namePredicate)
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_TYPE, IntegrationServiceTypeEnum.WEB_SERVICE))
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_ACTIVE, Boolean.TRUE))
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_DIRECTION, IntegrationServiceDirectionEnum.OUTBOUND))
        dq.setScopingEnabled(false)

        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
    }
    private static final String STATUS = "status"
    private static final String STATUS_ID = "status-id"
    private static final String OK_STATUS = "OK"
    private static final String OK_STATUS_NO = "1"
    private static final Logger LOGGER = Logger.getLogger(ITSAutoInvoiceGroovyJob.class)
}
