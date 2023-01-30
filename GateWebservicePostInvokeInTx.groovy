import com.navis.argo.ArgoField
import com.navis.argo.ContextHelper
import com.navis.argo.business.model.Yard
import com.navis.argo.util.ArgoGroovyUtils
import com.navis.argo.util.XmlUtil
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.road.business.model.DocumentMessage
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.spatial.BinField
import com.navis.yard.YardField
import com.navis.yard.business.atoms.YardBlockTypeEnum
import com.navis.yard.business.model.AbstractYardBlock
import com.navis.yard.business.model.YardBinModel
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger
import org.jdom.Document
import org.jdom.Element
import org.jdom.Attribute
import com.navis.argo.webservice.types.v1_0.QueryResultType
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.road.business.atoms.TruckVisitStatusGroupEnum
/*
*
* Requirements : This groovy is used to add messages element to process truck and submit transaction response and this groovy handles only at Ingate.
*
* @Inclusion Location	: Incorporated as GroovyPlugin
* @author <a href="mailto:smohanbabu@weservetech.com">Mohan Babu</a>
*
*  S.No Modified Date   Modified By     Jira Id     SFDC        Change Description
     1     17/08/2022      Mohan
     2     30/01/2023      Mohan         IP-480              Add "gate.flip_mission_statement" message for wheeled block
*/
class GateWebservicePostInvokeInTx {
    private final String PROCESS_TRUCK_ELEMENT = "process-truck"
    private final String PROCESS_TRUCK_RESPONSE_ELEMENT = "process-truck-response"
    private final String SUBMIT_TRANSACTION_ELEMENT = "submit-transaction"
    private final String SUBMIT_TRANSACTION_RESPONSE_ELEMENT = "submit-transaction-response"
    public void postHandlerInvoke(Map parameter) {
        LOGGER.info("Entered GateWebservicePostInvokeInTx")
        Element element = (Element) parameter.get(ArgoGroovyUtils.WS_ROOT_ELEMENT);
        Element processTruckElement = element.getChild(PROCESS_TRUCK_ELEMENT) != null ?
                element.getChild(PROCESS_TRUCK_ELEMENT) : null;
        Element submitTransactionElement = element.getChild(SUBMIT_TRANSACTION_ELEMENT) != null ?
                element.getChild(SUBMIT_TRANSACTION_ELEMENT) : null;

        if (processTruckElement != null) {
            LOGGER.info("Inside Process truck element")
            addMessagesElement(parameter,processTruckElement,PROCESS_TRUCK_RESPONSE_ELEMENT)
        }
        else if(submitTransactionElement != null){
            LOGGER.info("Inside submit transaction element")
            addMessagesElement(parameter,submitTransactionElement,SUBMIT_TRANSACTION_RESPONSE_ELEMENT)
        }
    }

    private void addMessagesElement(Map parameter,Element element,String responseElement){
        Object[] obj = parameter.get("ARGO_WS_RESULT_HOLDER")
        QueryResultType[] queryResultType = (QueryResultType[]) obj[0]
        QueryResultType type = (QueryResultType) queryResultType[0]
        String result = type.getResult()

        Element truckElement = element.getChild("truck") ? element.getChild("truck") : null
        Attribute licenseNbrAttribute = truckElement.getAttribute("license-nbr") ? truckElement.getAttribute("license-nbr") : null
        String licenseNbr = licenseNbrAttribute.getValue()
        if (licenseNbr != null) {
            LOGGER.info("Inside Truck License")
            TruckVisitDetails truckVisitDetails = TruckVisitDetails.findMostRecentTruckVisitByField(MetafieldIdFactory.valueOf("tvdtlsTruck.truckLicenseNbr"), licenseNbr, TruckVisitStatusGroupEnum.ALL)
            if (truckVisitDetails != null) {
                LOGGER.info("Inside Truck Visit Details")
                Document document = (Document) XmlUtil.parse(result)
                Element rootElement = document != null ? (Element) document.getRootElement() : null
                Element tranResponseElement = rootElement != null ? rootElement.getChild(responseElement) : null
                Element truckTransactionsElement = tranResponseElement != null ? tranResponseElement.getChild("truck-transactions") : null
                List truckTransactionList = truckTransactionsElement != null ? truckTransactionsElement.getChildren("truck-transaction") : null
                if(truckTransactionList != null && !truckTransactionList.isEmpty()){
                    Set truckTrans = truckVisitDetails.getTvdtlsTruckTrans()

                    for(Element truckTransElement : truckTransactionList){
                        Element messagesElement = new Element("messages")
                        String tranNbr = truckTransElement.getAttributeValue("tran-nbr")
                        TruckTransaction truckTransaction = getTruckTransactionByNbr(truckTrans,Long.valueOf(tranNbr))
                        if (truckTransaction != null && truckTransaction.getTranStatus() == TranStatusEnum.OK){
                            LOGGER.info("Inside Truck Transaction - " + truckTransaction.getTranNbr().toString())
                            List<DocumentMessage> documentMessageList = DocumentMessage.findByTransaction(truckTransaction)
                            if (documentMessageList != null && !documentMessageList.isEmpty()) {
                                for (DocumentMessage documentMessage : documentMessageList) {
                                    Element messageElement = new Element("message")
                                    if (documentMessage != null && documentMessage.getDocmsgSeverity() == "INFO") {
                                        String docmsgMsgId = documentMessage.getDocmsgMsgId()
                                        String docmsgMsgText = documentMessage.getDocmsgMsgText()
                                        String docmsgSeverity = documentMessage.getDocmsgSeverity()

                                        messageElement.setAttribute("message-id", docmsgMsgId)
                                        messageElement.setAttribute("message-text", docmsgMsgText)
                                        messageElement.setAttribute("message-severity", docmsgSeverity)
                                        messagesElement.addContent(messageElement)
                                        LOGGER.info("Added message "+docmsgMsgText)
                                    }
                                }
                            }

                            /* Add flip message for receive transactions if yard block is wheeled */
                            if(truckTransaction.getTranSubType() == TranSubTypeEnum.RE || truckTransaction.getTranSubType() == TranSubTypeEnum.RM){
                                LOGGER.info("Entered flip message block")
                                String blockId = truckTransElement.getAttributeValue("block-id")
                                if(StringUtils.isNotBlank(blockId)){
                                    LOGGER.info("Block id to check if wheeled - " + blockId)
                                    DomainQuery dq = QueryUtils.createDomainQuery("Yard").addDqPredicate(PredicateFactory.eq(ArgoField.YRD_ID,"PIERG"))
                                    List<Yard> yard = (List<Yard>)HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
                                    if(yard != null && !yard.isEmpty()){
                                        long key = yard.get(0).getYrdBinModel().getAbnGkey()
                                        AbstractYardBlock yardBlock = AbstractYardBlock.findYardBlockByCode(key, blockId)
                                        if(yardBlock != null && yardBlock.getAyblkBlockType() == YardBlockTypeEnum.CHASSIS){
                                            LOGGER.info("Add message for flip")
                                            Element messageElement = new Element("message")
                                            messageElement.setAttribute("message-id", "gate.flip_mission_statement")
                                            messageElement.setAttribute("message-text", "gate.flip_mission_statement")
                                            messageElement.setAttribute("message-severity", "INFO")
                                            messagesElement.addContent(messageElement)
                                            LOGGER.info("Added message "+"gate.flip_mission_statement")
                                        }
                                    }

                                }
                            }

                        }

                        truckTransElement.addContent(messagesElement)

                        LOGGER.info("Added message to Truck transaction element")
                    }
                }

                type.setResult(XmlUtil.toString(rootElement, false));
                parameter.put("ARGO_WS_RESULT_HOLDER", obj[0])

            }
        }
    }

    private TruckTransaction getTruckTransactionByNbr(Set truckTrans, Long tranNbr){
        if(truckTrans != null && !truckTrans.isEmpty()){
            for(TruckTransaction truckTransaction : truckTrans){
                if(truckTransaction.getTranNbr() == tranNbr){
                    return truckTransaction
                }
            }
        }
        return null
    }
    private final static Logger LOGGER = Logger.getLogger(GateWebservicePostInvokeInTx.class)
}
