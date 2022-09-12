import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.LogicalEntityEnum
import com.navis.argo.business.integration.IntegrationServiceMessage
import com.navis.argo.business.model.ArgoSequenceProvider
import com.navis.carina.integrationservice.business.IntegrationService
import com.navis.external.framework.AbstractExtensionCallback
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.util.scope.ScopeCoordinates
import groovy.sql.Sql
import org.apache.log4j.Logger

class ITSExtractorUtil extends AbstractExtensionCallback{
    private static final Logger logger = Logger.getLogger(this.class)


    Sql establishDbConnection() {
        String dbName = "jdbc:jtds:sqlserver://10.204.7.102:1433/itsdb"
        String userName = "sparcsn4"
        String password = "sparcsn4"

        return Sql.newInstance(dbName, userName, password);
    }

    IntegrationServiceMessage logRequestToInterfaceMessage(LogicalEntityEnum inLogicalEntityEnum,
                                                                  IntegrationService inIntegrationService, String inMessagePayload, String primaryId) {

        logger.debug("hibernatingEntity"+inMessagePayload.length())
        IntegrationServiceMessage integrationServiceMessage = new IntegrationServiceMessage();
        // integrationServiceMessage.setIsmEventPrimaryKey((Long) hibernatingEntity.getPrimaryKey());
        integrationServiceMessage.setIsmEntityClass(inLogicalEntityEnum);
        // integrationServiceMessage.setIsmEntityNaturalKey(hibernatingEntity.getHumanReadableKey());
        try {
            if (inIntegrationService) {
                integrationServiceMessage.setIsmIntegrationService(inIntegrationService);
                integrationServiceMessage.setIsmFirstSendTime(ArgoUtils.timeNow());
                //integrationServiceMessage.setIsmLastSendTime(ArgoUtils.timeNow());
            }
            if(inMessagePayload.length() <3900){
                integrationServiceMessage.setIsmMessagePayload(inMessagePayload);
            }
            else {
                integrationServiceMessage.setIsmMessagePayloadBig(inMessagePayload);
            }
            integrationServiceMessage.setIsmSeqNbr(new IntegrationServMessageSequenceProvider().getNextSequenceId());
            ScopeCoordinates scopeCoordinates = ContextHelper.getThreadUserContext().getScopeCoordinate();
            integrationServiceMessage.setIsmScopeGkey((String) scopeCoordinates.getScopeLevelCoord(scopeCoordinates.getDepth()));
            integrationServiceMessage.setIsmScopeLevel(scopeCoordinates.getDepth());
            integrationServiceMessage.setIsmUserString3("false")
            integrationServiceMessage.setIsmUserString1(primaryId)
            HibernateApi.getInstance().save(integrationServiceMessage);
            HibernateApi.getInstance().flush();
        } catch (Exception e) {
            logger.debug("Exception while saving ISM"+e)
        }

        return integrationServiceMessage;
    }

     class IntegrationServMessageSequenceProvider extends ArgoSequenceProvider {
        public Long getNextSequenceId() {
            return super.getNextSeqValue(serviceMsgSequence, ContextHelper.getThreadFacilityKey() != null ? (Long) ContextHelper.getThreadFacilityKey() : 1l);
        }
        private String serviceMsgSequence = "INT_MSG_SEQ";
    }

}
