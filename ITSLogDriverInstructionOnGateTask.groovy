package ITSIntegration

import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.util.MetafieldUserMessageImp
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.internationalization.UserMessage
import com.navis.framework.util.message.MessageLevel
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.commons.lang.StringUtils

/**
 * Author: <a href="mailto:smohanbabu@weservetech.com">Mohan Babu</a>
 *
 * Description: This groovy script will add driver instruction messages
 * Values in General Reference has to be set in below format for the code to work
 * ID1 - DRIVER_INSTRUCTION_MSG
 * ID2 - GateStageId_TranType, ex: INGATE_RM
 * ID3 - STATUS_TranStatus, ex: STATUS_OK
 * Value 1 - Message Id(From resource bundle)
 * Value 2 - Field(The name of the field if any conditions has to be checked)
 * Value 3 - Value of the field to be checked
 * Value 4 - Parameters to be passed to the message(Field name which has to be passed)
 */
class ITSLogDriverInstructionOnGateTask extends AbstractGateTaskInterceptor {
    private static Logger LOGGER = Logger.getLogger(ITSLogDriverInstructionOnGateTask.class);
    private final String GR_TYPE = "GOS"
    private final String GR_ID1 = "DRIVER_INSTRUCTION_MSG"
    private String GR_ID2_FORMAT = "%s_%s"
    private String GR_ID3_FORMAT = "STATUS_%s"
    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        TruckTransaction tran = inWfCtx.getTran();
        String grId2 = String.format(GR_ID2_FORMAT,tran.getTranStageId().toUpperCase(),tran.getTranSubType().getKey())
        List  genRef = GeneralReference.findAllEntriesById(GR_TYPE,GR_ID1,grId2)

        if(genRef != null && !genRef.isEmpty()){
            String id3 = String.format(GR_ID3_FORMAT,tran.getTranStatus().getKey())
            LOGGER.debug("ID3 - " + id3)
            for(GeneralReference ref: genRef){
                if(StringUtils.isNotBlank(ref.getRefId3()) && ref.getRefId3().startsWith(id3)){
                    LOGGER.debug("Adding message for  - " + ref.getRefValue1())
                    Object[] messageParam = null
                    /* Check if there are any parameters to be passed to message */
                    if(StringUtils.isNotBlank(ref.getRefValue4())){
                        LOGGER.debug("Found parameters  - " + ref.getRefValue4())
                        String[] params = ref.getRefValue4().split(",")
                         messageParam = new Object[params.size()]
                        for(int i =0;i < params.size(); i++){
                            messageParam[i] = tran.getFieldValue(MetafieldIdFactory.valueOf(params[i]))
                        }
                    }
                    /* Check if there are any fields to check for conditions */
                    if(StringUtils.isNotBlank(ref.getRefValue2())){
                        LOGGER.debug("Condition to be checked - " + ref.getRefValue2() + "and value is - " + ref.getRefValue3())
                        Object value = tran.getFieldValue(MetafieldIdFactory.valueOf(ref.getRefValue2()))
                        if(value != null && value.toString() == ref.getRefValue3()){
                            PropertyKey key = PropertyKeyFactory.valueOf(ref.getRefValue1())
                            RoadBizUtil.appendMessage(MessageLevel.INFO,key,messageParam)
                        }
                    }
                    else{
                        PropertyKey key = PropertyKeyFactory.valueOf(ref.getRefValue1())
                        RoadBizUtil.appendMessage(MessageLevel.INFO,key,messageParam)
                    }
                }
            }
        }

        LOGGER.debug("ITSLogDriverInstructionOnGateTask execution completed")
    }
}

