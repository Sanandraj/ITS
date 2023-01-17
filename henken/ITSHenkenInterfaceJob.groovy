package henken

import com.navis.argo.ArgoIntegrationField
import com.navis.argo.business.integration.IntegrationServiceMessage
import com.navis.argo.business.model.GeneralReference
import com.navis.carina.integrationservice.business.IntegrationService
import com.navis.external.argo.AbstractGroovyJobCodeExtension
import com.navis.framework.IntegrationServiceField
import com.navis.framework.business.Roastery
import com.navis.framework.business.atoms.IntegrationServiceDirectionEnum
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.Junction
import com.navis.framework.portal.query.PredicateFactory
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.xmlrpc.client.XmlRpcClient
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl

/**
 * @author <a href="mailto:sramasamy@weservetech.com"> Ramasamy Sathappan</a>
 * @since 23-Dec-2022
 * Sending message to HKI from the ISM created for ITV operations
 * */
class ITSHenkenInterfaceJob extends AbstractGroovyJobCodeExtension {

    @Override
    void execute(Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSHenkenInterfaceJob - BEGIN")

        loadPushLimit();
        List<IntegrationServiceMessage> ismList = getIsmListToBeSend();
        logMsg("ismList: " + ismList);
        if (ismList)
            logMsg("ismList size: " + ismList.size());

        for (IntegrationServiceMessage ism : ismList) {
            logMsg("ismForMessage :: " + ism.getIsmSeqNbr());

            if (ism != null) {
                // Used for any parameters to send to XML-RPC Data
                Vector params = new Vector();
                params.addElement(ism.getIsmMessagePayloadBig());
                params.addElement(MsgHighlight);
                params.addElement(ism.getIsmUserString1()); //cheId
                params.addElement(String.valueOf(ism.getIsmSeqNbr())); //transactionNumber - ecevent gkey
                params.addElement(T_5000);

                XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
                config.setServerURL(new URL(ism.getIsmIntegrationService().getIntservUrl()));

                XmlRpcClient client = new XmlRpcClient();
                client.setConfig(config);

                logMsg("params: "+params);

                Object result;
                try {
                    result = client.execute(HKI_API, params);
                    ism.setIsmUserString5(T_SENT);

                } catch (Exception e) {
                    result = e.getMessage();
                    LOGGER.error("Exception while pushing the request : "+result);
                    ism.setIsmUserString5(T_FAILED);
                }
                logMsg("result: "+ result);

                String resultMessage = result? (String) result : T_EMPTY;
                logMsg("resultMessage: "+resultMessage);
                ism.setIsmUserString4(resultMessage);
                ism.setIsmLastSendTime(new Date())
                HibernateApi.getInstance().save(ism);

                //HibernateApi.getInstance().flush();
                logMsg("Result from Client : " + result);
            }

        }
    }

    private List<IntegrationServiceMessage> getIsmListToBeSend() {
        DomainQuery dq = QueryUtils.createDomainQuery(T_INTEGRATION_SERVICE_MESSAGE)
                .addDqPredicate(nonProcessedDisJunction)
                /*.addDqPredicate(PredicateFactory.eq(ISM_SERV_NAME, T_HKI))*/
                .addDqPredicate(PredicateFactory.like(ISM_SERV_NAME, T_HKI + T_PERCENTAGE))
                .addDqOrdering(Ordering.asc(ArgoIntegrationField.ISM_SEQ_NBR))
                .setDqMaxResults(ISM_MSG_PUSH_LIMIT);

        return Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);
    }


    private IntegrationService getIntegrationServiceByName(String inName, IntegrationServiceDirectionEnum inDirection) {
        DomainQuery dq = QueryUtils.createDomainQuery(T_INTEGRATION_SERVICE)
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_NAME, inName))
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_DIRECTION, inDirection))
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_ACTIVE, Boolean.TRUE));
        return (IntegrationService) Roastery.getHibernateApi().getUniqueEntityByDomainQuery(dq);
    }

    private void logMsg(Object inMsg) {
        LOGGER.debug(inMsg);
    }

    //@TODO: Below General Reference read is in private method for testing purpose. It should a static block instead private method
    //static {
    private void loadPushLimit() {
        GeneralReference reference = GeneralReference.findUniqueEntryById(T_HKI, T_MESSAGE_EXCHANGE, T_ALLOWED_UTR);
        ISM_MSG_PUSH_LIMIT = reference? Integer.parseInt(reference.getRefValue2()) : ISM_MSG_PUSH_LIMIT;
    }

    private static final Junction nonProcessedDisJunction = PredicateFactory.disjunction()
            .add(PredicateFactory.eq(ArgoIntegrationField.ISM_USER_STRING5, T_FAILURE))
            .add(PredicateFactory.isNull(ArgoIntegrationField.ISM_USER_STRING5));

    private static final MetafieldId ISM_SERV_NAME = MetafieldIdFactory.getCompoundMetafieldId(ArgoIntegrationField.ISM_INTEGRATION_SERVICE, IntegrationServiceField.INTSERV_NAME);

    private static int ISM_MSG_PUSH_LIMIT = 20;
    private static final String T_5000 = "5000";
    private static final String T_FAILURE = "FAILURE";
    private static final String T_HKI = "HKI";
    private static final double MsgHighlight = 26;
    private static final String T_EMPTY = "";
    //private static String SERVER_URL = "http://192.168.94.8:9003";
    //private static String SERVER_URL;
    private static final String HKI_API = "ECN4XMLtoHKIMDT";
    private static final String T_SENT = "Sent";
    private static final String T_FAILED = "Failed";
    private static final String T_INTEGRATION_SERVICE = "IntegrationService";
    private static final String T_PERCENTAGE = "%";

    private static final String T_MESSAGE_EXCHANGE = "MESSAGE_EXCHANGE";
    private static final String T_ALLOWED_UTR = "ALLOWED_UTR";
    private static final String T_INTEGRATION_SERVICE_MESSAGE = "IntegrationServiceMessage";

    private static final Logger LOGGER = Logger.getLogger(ITSHenkenInterfaceJob.class);
}
