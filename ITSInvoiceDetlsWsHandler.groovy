import com.navis.billing.business.model.Invoice
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
import org.jdom.Namespace
import java.text.ParsePosition
import java.text.SimpleDateFormat


/*
 * @Author: <a href="mailto:skishore@weservetech.com"> KISHORE KUMAR S</a>
 * Date: 04/03/2022
 * Requirements:-  Request should have the input date and read all the Invoices beyond that date
 * @Inclusion Location	: Incorporated as a code extension of the type WS_ARGO_CUSTOM_HANDLER --> Paste this code (ITSInvoiceDetlsWsHandler.groovy)

<custom class= "ITSInvoiceDetlsWsHandler" type="extension">
<Invoices>
<InputDate>?</InputDate>
</Invoices>
</custom>

*/

class ITSInvoiceDetlsWsHandler extends  AbstractArgoCustomWSHandler {
    @Override
    void execute(final UserContext userContext,
                 final MessageCollector messageCollector,
                 final Element inECustom, final Element inOutEResponse, final Long inWsLogGKey) {
        LOGGER.setLevel(Level.DEBUG)
        Element rootElement = inECustom.getChild("Invoices")
        Element inputDate = rootElement.getChild("InputDate")
        inputDate.getNamespace()
        Namespace sNS = Namespace.getNamespace("argo", "http://www.navis.com/sn4")
        Element responseRoot = new Element("InvoiceDetails", sNS)
        inOutEResponse.addContent(responseRoot)
        Element invoiceDetails = new Element("InvoiceDetails", sNS)
        responseRoot.addContent(invoiceDetails)

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd")
        ParsePosition pp = new ParsePosition(0)
        Date invoiceCreatedDate = dateFormat.parse(inputDate.getValue(), pp)

        if (invoiceCreatedDate != null) {
            responseRoot.setAttribute(STATUS, OK_STATUS_NO)
            responseRoot.setAttribute(STATUS_ID, OK_STATUS)
            MetafieldId metaFieldIdInvCreatedDate = MetafieldIdFactory.valueOf("invoiceCreated")
            DomainQuery domainQuery = QueryUtils.createDomainQuery("Invoice")
                    .addDqPredicate(PredicateFactory.gt(metaFieldIdInvCreatedDate, invoiceCreatedDate))
            List<Invoice> OutputList = HibernateApi.getInstance().findEntitiesByDomainQuery(domainQuery)
            if (OutputList != null && !OutputList.isEmpty()) {
                for (Invoice invoice : (OutputList as List<Invoice>)) {
                    String invDraftNbr = invoice.getInvoiceDraftNbr()
                    String invFinalDate = invoice.getInvoiceFinalizedDate()
                    String invTotalCharges = invoice.getInvoiceTotalCharges().toString()
                    Element invoices = new Element("Invoices", sNS)
                    invoiceDetails.addContent(invoices)
                    invoices.setAttribute("INVOICE_DRAFT_NBR", invDraftNbr, sNS)
                    invoices.setAttribute("INVOICE_FINALIZED_DATE", invFinalDate, sNS)
                    invoices.setAttribute("INVOICE_TOTAL_CHARGES", invTotalCharges, sNS)

                    Set invItems = invoice.getInvoiceInvoiceItems()
                    for (String items : invItems as List<String>) {
                        Element InvoiceItems = new Element("INVOICE-ITEMS", sNS)
                        invoices.addContent(InvoiceItems)
                        String numberOnly = items.replaceAll("[^0-9]", "")
                        InvoiceItems.addContent(numberOnly)
                    }
                }
            } else {
                Element messages = new Element("messages")
                invoiceDetails.addContent(messages)
                Element message = new Element("message")
                messages.addContent(message)
                invoiceDetails.setText("No Data found after the Given Input Date : ${inputDate.getValue()}")
                responseRoot.setAttribute(STATUS, NOT_OK_STATUS_NO)
                responseRoot.setAttribute(STATUS_ID, NOT_OK_STATUS)
            }
        }
    }
    private static Logger LOGGER = Logger.getLogger(this.class)
    private static final String STATUS = "status"
    private static final String STATUS_ID = "status-id"
    private static final String OK_STATUS = "OK"
    private static final String OK_STATUS_NO = "1"
    private static final String NOT_OK_STATUS = "NOK"
    private static final String NOT_OK_STATUS_NO = "3"
}
