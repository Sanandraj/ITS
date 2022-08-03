import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.LogicalEntityEnum
import com.navis.argo.business.integration.IntegrationServiceMessage
import com.navis.argo.business.model.ArgoSequenceProvider
import com.navis.argo.business.model.GeneralReference
import com.navis.billing.business.model.Invoice
import com.navis.billing.business.model.InvoiceItem
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
import org.jdom.Namespace
import org.jdom.output.Format
import org.jdom.output.XMLOutputter

/**
@Author <a href="mailto:skishore@weservetech.com">KISHORE KUMAR S</a>
 */

class ITSAutoInvoiceGroovyJob extends AbstractGroovyJobCodeExtension{
    @Override
    void execute(Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSAutoInvoiceGroovyJob Starts :: ")
        MetafieldId metafieldId = MetafieldIdFactory.valueOf("customFlexFields.invoiceCustomDFF_ITSGroovyJobJMS")
        LOGGER.debug("metafieldId :: "+metafieldId)
        MetafieldId metaFieldId_Inv_JMS = MetafieldIdFactory.valueOf("invoiceFlexString02")
        DomainQuery dqInvoice = QueryUtils.createDomainQuery("Invoice")
        .addDqPredicate(PredicateFactory.isNull(metaFieldId_Inv_JMS))
        .addDqPredicate(PredicateFactory.eq(metafieldId,"TRUE"))
        List<Invoice> outputList =(List<Invoice>) HibernateApi.getInstance().findEntitiesByDomainQuery(dqInvoice)
        LOGGER.debug("outputList :: "+outputList)
        GeneralReference gR = GeneralReference.findUniqueEntryById("ITS_INV_JMS_JOB","ITS_AUTO_INV")
        String refValue= gR.getRefValue1()
        List<String> refList = new ArrayList<String>(Arrays.asList(refValue.split(",")));
        LOGGER.debug("refList :: "+refList)

        for (Invoice invoice: outputList as List<Invoice>){
            if (refList!=null && !refList.contains(invoice.getInvoiceInvoiceType().getInvtypeId())){
                LOGGER.debug("invoice ::"+invoice)
                List<IntegrationService> integrationServiceList = getInvoiceDetailsSyncIntegrationServices("ITS_DET_EXTRACTED", false);
                if (outputList != null) {
                    for (IntegrationService integrationService : integrationServiceList) {
                        LOGGER.debug("integrationService ::" +integrationService)
                        Element requestMessage;
                        LOGGER.debug("integrationService ::"+integrationService)
                        requestMessage = fetchInvoiceDetails(invoice);
                        LOGGER.debug("requestMessage ::"+requestMessage)
                        if (requestMessage != null) {
                            LOGGER.debug("requestMessage is not null")
                            logRequestToInterfaceMessage(invoice,LogicalEntityEnum.NA, integrationService, requestMessage);
                            LOGGER.debug("Inside for Setting Flex Values")
                            invoice.setFieldValue(metaFieldId_Inv_JMS,"true")
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


        IntegrationServiceMessage integrationServiceMessage = new IntegrationServiceMessage();
        integrationServiceMessage.setIsmEventPrimaryKey((Long) hibernatingEntity.getPrimaryKey());
        integrationServiceMessage.setIsmEntityClass(inLogicalEntityEnum);
        integrationServiceMessage.setIsmEntityNaturalKey(hibernatingEntity.getHumanReadableKey());
        try {
            if (inIntegrationService) {
                integrationServiceMessage.setIsmIntegrationService(inIntegrationService);
                integrationServiceMessage.setIsmFirstSendTime(ArgoUtils.timeNow());
            }
            if(inMessagePayload.toString() <(3900 as String)){
                integrationServiceMessage.setIsmMessagePayload(inMessagePayload.toString());
            }
            else {
                integrationServiceMessage.setIsmMessagePayloadBig(inMessagePayload.toString());
            }
            integrationServiceMessage.setIsmSeqNbr(new IntegrationServMessageSequenceProvider().getNextSequenceId());
            ScopeCoordinates scopeCoordinates = ContextHelper.getThreadUserContext().getScopeCoordinate();
            integrationServiceMessage.setIsmScopeGkey((String) scopeCoordinates.getScopeLevelCoord(scopeCoordinates.getDepth()));
            integrationServiceMessage.setIsmScopeLevel(scopeCoordinates.getDepth());
            integrationServiceMessage.setIsmUserString3("false")
            HibernateApi.getInstance().save(integrationServiceMessage)
            HibernateApi.getInstance().flush()

        } catch (Exception e) {
            LOGGER.debug("Exception while saving ISM"+e)
        }
        return integrationServiceMessage;
    }

    static class IntegrationServMessageSequenceProvider extends ArgoSequenceProvider {
         Long getNextSequenceId() {
            return super.getNextSeqValue(serviceMsgSequence, ContextHelper.getThreadFacilityKey() != null ? (Long) ContextHelper.getThreadFacilityKey() : 1l);
        }
        private String serviceMsgSequence = "INT_MSG_SEQ";
    }
    private static fetchInvoiceDetails(Invoice invoice){
        Namespace sNS = Namespace.getNamespace("argo", "http://www.navis.com/sn4")
        final Element inOutEResponse = new Element("Invoice",sNS)
        Element responseRoot = new Element("InvoiceDetails", sNS)
        inOutEResponse.addContent(responseRoot)
        Element invoiceDetails = new Element("Invoices", sNS)
        responseRoot.addContent(invoiceDetails)
            responseRoot.setAttribute(STATUS, OK_STATUS_NO)
            responseRoot.setAttribute(STATUS_ID, OK_STATUS)
        MetafieldId metafieldId_Inv_Batch_Id = MetafieldIdFactory.valueOf("invoiceFlexString03")
        LOGGER.debug("metafieldId_Inv_Batch_Id ::"+metafieldId_Inv_Batch_Id)
        DomainQuery dq = QueryUtils.createDomainQuery("Invoice")
                .addDqPredicate(PredicateFactory.isNotNull(metafieldId_Inv_Batch_Id))
        .addDqOrdering(Ordering.desc(metafieldId_Inv_Batch_Id))
        List<Invoice> jobBatchList =(List<Invoice>) HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        LOGGER.debug("jobBatchList :: "+jobBatchList)
        Invoice batchMax= jobBatchList.get(0)
        LOGGER.debug("batchMax :: "+batchMax)
        String flexStringValue = batchMax.getInvoiceFlexString03()
        LOGGER.debug("flexStringValue :: "+flexStringValue)
        Long parseLong = Long.parseLong(flexStringValue)+1
        invoice.setFieldValue(metafieldId_Inv_Batch_Id,parseLong.toString())
        LOGGER.debug("parseLong ::"+parseLong)

        Element company = new Element("Company")
        invoiceDetails.addContent(company)
        Element companyNbr = new Element("companyNbr")
        company.addContent(companyNbr)
        // the company Nbr is standard hence hardcoded
        companyNbr.addContent("1")

        Element batchNbr = new Element("batchNbr")
        company.addContent(batchNbr)
        batchNbr.addContent(parseLong.toString())

        Element batchDate = new Element("batchDate")
        company.addContent(batchDate)
        // the batch date fetch the present groovy job run time
        batchDate.addContent(ArgoUtils.timeNow().toString())

                String invDraftNbr= invoice?.getInvoiceDraftNbr()?.toString()
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
                String inv_Total_Credits  = invoice?.getInvoiceTotalCredits()?.toString()
                String inv_Status = invoice?.getInvoiceStatus()?.toString()
                String inv_Final_Number = invoice?.getInvoiceFinalNbr()?.toString()
                String inv_ComplexId = invoice?.getLogEntityComplex()?.getCpxId()?.toString()
                String inv_Paid_Thru_Date = invoice?.getInvoicePaidThruDay()?.toString()
                String inv_Cust_Id = invoice?.getInvoicePayeeCustomer()?.getCustName()?.toString()
                String inv_Cust_Role = invoice?.getInvoicePayeeCustomer()?.getCustRole()?.toString()
                String inv_Created = invoice?.getInvoiceCreated()?.toString()
                String inv_Contract_Name = invoice?.getInvoiceContract()?.getContractId()?.toString()


                Element invoices = new Element("Invoice")
                invoiceDetails.addContent(invoices)
                invoices?.setAttribute("INVOICE_DRAFT_NBR",invDraftNbr,sNS)
                invoices?.setAttribute("INVOICE_TYPE",inv_Type,sNS)
                if (inv_Contract_Name!=null){
                    invoices?.setAttribute("INVOICE_CONTRACT",inv_Contract_Name,sNS)
                }
                else {
                    invoices?.setAttribute("INVOICE_CONTRACT","")
                }
                invoices?.setAttribute("INVOICE_STATUS",inv_Status,sNS)
                invoices?.setAttribute("INVOICE_COMPLEX_ID",inv_ComplexId,sNS)
                if (invFinalDate!=null && inv_Final_Number != null){
                    invoices?.setAttribute("INVOICE_FINALIZED_DATE",invFinalDate,sNS)
                    invoices?.setAttribute("INVOICE_FINAL_NUMBER",inv_Final_Number,sNS)
                }
                else {
                    invoices?.setAttribute("INVOICE_FINALIZED_DATE","")
                    invoices?.setAttribute("INVOICE_FINAL_NUMBER","")
                }
                invoices?.setAttribute("INVOICE_TOTAL_CHARGES",invTotalCharges,sNS)
                invoices?.setAttribute("INVOICE_TOTAL_TAXES",inv_Total_taxes,sNS)
                invoices?.setAttribute("INVOICE_TOTAL_DISCOUNTS",inv_Total_Discounts,sNS)
                invoices?.setAttribute("INVOICE_TOTAL_CREDITS",inv_Total_Credits,sNS)
                invoices?.setAttribute("INVOICE_TOTAL_OWED",inv_Total_Owed,sNS)
                if (inv_Paid_Thru_Date!=null){
                    invoices?.setAttribute("INVOICE_PAID_THRU_DATE",inv_Paid_Thru_Date,sNS)
                }
                else {
                    invoices?.setAttribute("INVOICE_PAID_THRU_DATE","")
                }
                if (invDueDate!=null){
                    invoices?.setAttribute("INVOICE_DUE_DATE",invDueDate,sNS)
                }
                else {
                    invoices?.setAttribute("INVOICE_DUE_DATE","")
                }
                if (invEffectiveDate!=null){
                    invoices?.setAttribute("INVOICE_EFFECTIVE_DATE",invEffectiveDate,sNS)
                }
                else {
                    invoices?.setAttribute("INVOICE_EFFECTIVE_DATE","")
                }
                invoices?.setAttribute("INVOICE_IS_MERGED",invIs_Merged,sNS)
                invoices?.setAttribute("INVOICE_GKEY",inv_GKey,sNS)
                invoices?.setAttribute("INVOICE_CURRENCY",inv_Currency,sNS)
                if (inv_Cust_Id!=null){
                    invoices?.setAttribute("INVOICE_CUST_ID",inv_Cust_Id,sNS)
                    if (inv_Cust_Role!=null){
                        invoices?.setAttribute("INVOICE_CUST_ROLE",inv_Cust_Role,sNS)
                    }
                    else {
                        invoices?.setAttribute("INVOICE_CUST_ROLE","")
                    }
                }
                else {
                    invoices?.setAttribute("INVOICE_CUST_ID","")
                }
                invoices?.setAttribute("INVOICE_CREATED",inv_Created,sNS)
                if (inv_Notes !=null){
                    invoices?.setAttribute("INVOICE_NOTES",inv_Notes,sNS)
                }
                else {
                    invoices?.setAttribute("INVOICE_NOTES","")
                }
        List<InvoiceItem> invoiceItemList= InvoiceItem.getInvoiceItemByInvoice(invoice)
        LOGGER.debug("invoiceItemList :: "+invoiceItemList)
        if (invoiceItemList != null) {
            for (InvoiceItem invItem : invoiceItemList){
                Element distribution = new Element("distribution")
                invoices.addContent(distribution)
                Element distributionCategoryNbr = new Element("distributionCategoryNbr")
                distribution.addContent(distributionCategoryNbr)
                Tariff tariff = Tariff.findTariff(invItem?.getItemTariff())
                LOGGER.debug("tariff :: "+tariff)
                distributionCategoryNbr.addContent( tariff?.getTariffServiceType()?.getSrvctypeId())
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
        String finalResponse = xmlOutputter.outputString(new Document(inOutEResponse))
        LOGGER.debug("finalResponse ::"+finalResponse)
        return inOutEResponse
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
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_DIRECTION, IntegrationServiceDirectionEnum.OUTBOUND));
        dq.setScopingEnabled(false);

        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
    }
    private static final String STATUS = "status"
    private static final String STATUS_ID = "status-id"
    private static final String OK_STATUS = "OK"
    private static final String OK_STATUS_NO = "1"
    private static final Logger LOGGER = Logger.getLogger(ITSAutoInvoiceGroovyJob.class)
}
