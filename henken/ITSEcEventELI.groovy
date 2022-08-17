package henken

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import com.navis.argo.web.ArgoGuiMetafield
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChange
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.metafields.MetafieldId
import org.apache.log4j.Level
import org.apache.log4j.Logger


/**
 * @author <a href="mailto:sramasamy@weservetech.com"> Ramasamy Sathappan</a>
 * @since 08-Aug-2022
 * */
class ITSEcEventELI extends AbstractEntityLifecycleInterceptor {

    @Override
    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        notifyHKI(inEntity, inOriginalFieldChanges, inMoreFieldChanges);
    }

    private void notifyHKI(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        try {
            LOGGER.setLevel(Level.DEBUG);
            logMsg("notifyHKI - inEntity:: " + inEntity + ", inOriginalFieldChanges: " + inOriginalFieldChanges + ", inMoreFieldChanges: " + inMoreFieldChanges);
            String ecEventTypeDesc = (String) getNewFieldValue(inOriginalFieldChanges, ArgoGuiMetafield.ECEVENT_TYPE_DESCRIPTION);
            logMsg("ecEventTypeDesc: " + ecEventTypeDesc);

            String cheId, cheStatus, chePool, transactionNumber, message;
            // On LogOff the Che
            if (T_LGOF == ecEventTypeDesc) {
                XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
                config.setServerURL(new URL(SERVER_URL));

                XmlRpcClient client = new XmlRpcClient();
                client.setConfig(config);

                double MsgHighlight = 26;
                cheId = "T001";
                cheStatus = "UNAVAILABLE";
                chePool = "YARDA";
                transactionNumber = "1234567890";

                message = frameMessage(cheId, cheStatus, chePool, LINE_EMPTY,  LINE_EMPTY,  LINE_LOGGED_OUT,  LINE_EMPTY,  LINE_CALL_CLERK,  LINE_EMPTY,  LINE_EMPTY);
                logMsg("message:\n"+message);
                if (message != null) {
                    /* Send MDT Message START  - Based On Data Above */
                    // Used for any parameters to send to XML-RPC Data
                    Vector params = new Vector();
                    params.addElement(message);
                    params.addElement(MsgHighlight);
                    params.addElement(cheId);
                    params.addElement(transactionNumber);
                    params.addElement(T_5000);

                    Object result = client.execute(HKI_API, params);
                    logMsg("Result from Client : " + result);
                    /* Send MDT Message END  - Based On Data Above */
                }
            }

        } catch (Exception e) {
            LOGGER.error("Exception in notifyRTLS : "+e.getMessage());
        }
    }

    private String frameMessage(String inCheId, String inCheStatus, String inChePool, String inLine2, String inLine3, String inLine4, String inLine5, String inLine6, String inLine7, String inLine8) {
        try {
            logMsg("frameMessage BEGIN : "+inCheId);
            StringBuilder sb = new StringBuilder();
            if (!inCheId.isEmpty()) {
                sb.append(inCheId); // 4 chars
                sb.append("     "); // 5 chars
                sb.append(inCheStatus); // 11 chars
                sb.append("     "); // 5 chars
                sb.append(inChePool); // 5 chars
            }
            sb.append(inLine2);
            sb.append(inLine3);
            sb.append(inLine4);
            sb.append(inLine5);
            sb.append(inLine6);
            sb.append(inLine7);
            sb.append(inLine8);
            logMsg("frameMessage END");

            return sb.toString();

        } catch (Exception e) {
            LOGGER.error("Exception in frameMessage : "+e.getMessage());
        }
        return null;
    }

    private Object getNewFieldValue(EFieldChangesView inOriginalFieldChanges, MetafieldId inMetaFieldId) {
        EFieldChange fieldChange = inOriginalFieldChanges.findFieldChange(inMetaFieldId);
        return fieldChange ? fieldChange.getNewValue() : null;
    }

    private void logMsg(Object inMsg) {
        LOGGER.debug(inMsg);
    }

    /*String TextBoxStringData = "1111111111111111111222222222222222222222222222222333333333333333333333333333333" +
            "444444444444444444444444444444555555555555555555555555555555666666666666666666666666666666"+
            "777777777777777777777777777777888888888888888888888888888888";
    */

    /*private static String LINE_1 = "111111111111111111111111111111";
    private static String LINE_2 = "222222222222222222222222222222";
    private static String LINE_3 = "333333333333333333333333333333";
    private static String LINE_4 = "444444444444444444444444444444";
    private static String LINE_5 = "555555555555555555555555555555";
    private static String LINE_6 = "666666666666666666666666666666";
    private static String LINE_7 = "777777777777777777777777777777";
    private static String LINE_8 = "888888888888888888888888888888";*/

    private static String LINE_EMPTY = "                              ";
    private static String LINE_LOGGED_OUT = "         LOGGED OUT           ";
    private static String LINE_CALL_CLERK = "         CALL CLERK           ";
    //private static String LINE_BREAK = "\n";

    private static String T_5000 = "5000";
    private static String HKI_API = "ECN4XMLtoHKIMDT";

    private final int COLUMN_COUNT_LIMIT = 30;
    private final int ROW_COUNT_LIMIT = 8;

    private static final String SERVER_URL = "http://192.168.94.8:9003";
    private static final String T_LGOF = "LGOF";

    private static final Logger LOGGER = Logger.getLogger(ITSEcEventELI.class);

}
