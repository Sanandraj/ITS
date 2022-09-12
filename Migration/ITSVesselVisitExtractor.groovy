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
import wslite.json.JSONArray
import wslite.json.JSONObject

class ITSVesselVisitExtractor extends AbstractExtensionCallback {

    @Override
    void execute() {
        Sql sourceConn = establishDbConnection()
        sourceConn.eachRow(sql) {
            row ->
                def lineList = []
                sourceConn.eachRow(lineSql.replace('XXX', row.SHIP_ID.toString())
                        .replace('YYY', row.CALL_YEAR.toString())
                        .replace('ZZZ', row.OUT_CALL_NBR.toString())) {
                    vvLines ->
                        lineList.add(generateLines(vvLines));
                }
                JSONArray jsonlineArray = new JSONArray(lineList)
                LinkedHashMap visitMap = generateVisitMap(row)
                visitMap.put("line_count",lineList.size())
                String json = generateJson(jsonlineArray, visitMap)
                logRequestToInterfaceMessage(LogicalEntityEnum.VV, null, json, (String) visitMap.get("vessel_visit_id") )

        }
    }

    def generateLines(def vvLine) {
       // JSONBuilder lineMap = JSONBuilder.createObject()

        def lineMap = [:]
        lineMap.put('cargo_cutoff', vvLine.cargo_cutoff)
        lineMap.put('changed', vvLine.changed)
        lineMap.put('changer', vvLine.changer)
        lineMap.put('created', vvLine.created)
        lineMap.put('creator', vvLine.creator)
        lineMap.put('empty_pickup', vvLine.empty_pickup)
        lineMap.put('hazardous_cutoff', vvLine.hazardous_cutoff)
        lineMap.put('line_id', vvLine.line_id)
        lineMap.put('line_in_voy_nbr', vvLine.line_in_voy_nbr)
        lineMap.put('line_in_voy_nbr', vvLine.line_in_voy_nbr)
        lineMap.put('line_out_voy_nbr', vvLine.line_out_voy_nbr)
        lineMap.put('reefer_cutoff', vvLine.reefer_cutoff)
        return lineMap
    }

    def generateVisitMap(def visit) {
        def visitMap = [:]
        //visitMap.put('extract_date', extractDate)
        //visitMap.put('batch_id', batchId)
        visitMap.put('ship_id', visit.ship_id)
        visitMap.put('vessel_visit_id', visit.vessel_visit_id)
        visitMap.put('line_id', visit.line_id)
        visitMap.put('service_id', visit.service_id)
        visitMap.put('in_voy_nbr', visit.in_voy_nbr)
        //    visitMap.put('in_call_nbr', visit.in_call_nbr)
        visitMap.put('out_voy_nbr', visit.out_voy_nbr)
        //    visitMap.put('out_call_nbr', visit.out_call_nbr)
        visitMap.put('active', visit.active)
        //   visitMap.put('active_sparcs', visit.active_sparcs)
        visitMap.put('eta', visit.eta)
        visitMap.put('ata', visit.ata)
        visitMap.put('etd', visit.etd)
        visitMap.put('arrived', visit.arrived)
        visitMap.put('discharged', visit.discharged)
        visitMap.put('work_start', visit.work_start)
        visitMap.put('work_complete', visit.work_complete)
        visitMap.put('berth', visit.berth)
        visitMap.put('ship_side_to', visit.ship_side_to)
        visitMap.put('notes', visit.notes)
        visitMap.put('created', visit.created)
        visitMap.put('creator', visit.creator)
        visitMap.put('changed', visit.changed)
        visitMap.put('changer', visit.changer)
        visitMap.put('empty_pickup', visit.empty_pickup)
        visitMap.put('cargo_cutoff', visit.cargo_cutoff)
        visitMap.put('reefer_cutoff', visit.reefer_cutoff)
        visitMap.put('completed', visit.completed)
        visitMap.put('published_eta', visit.published_eta)
        visitMap.put('published_etd', visit.published_etd)
        visitMap.put('discharge_completed', visit.discharge_completed)
        visitMap.put('hazardous_cutoff', visit.hazardous_cutoff)
        visitMap.put('begin_receive', visit.begin_receive)
        return visitMap
    }

    String generateJson( JSONArray lines, def visitMap) {
        JSONObject builder = new JSONObject(visitMap)
        builder.accumulate("visit_lines", lines)
        return builder.toString()

    }

    Sql establishDbConnection() {
        String dbName = "jdbc:jtds:sqlserver://10.204.7.102:1433/itsdb"
        String userName = "sparcsn4"
        String password = "sparcsn4"

        return Sql.newInstance(dbName, userName, password);
    }


    private
    static IntegrationServiceMessage logRequestToInterfaceMessage(LogicalEntityEnum inLogicalEntityEnum,
                                                                  IntegrationService inIntegrationService, String inMessagePayload, String primaryId) {

        log.debug("hibernatingEntity"+inMessagePayload.length())
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
            log.debug("Exception while saving ISM"+e)
        }

        return integrationServiceMessage;
    }

    public static class IntegrationServMessageSequenceProvider extends ArgoSequenceProvider {
        public Long getNextSequenceId() {
            return super.getNextSeqValue(serviceMsgSequence, ContextHelper.getThreadFacilityKey() != null ? (Long) ContextHelper.getThreadFacilityKey() : 1l);
        }
        private String serviceMsgSequence = "INT_MSG_SEQ";
    }

    String sql = """SELECT  sv.vessel_visit_id, sv.line_id,
        sv.service_id, sv.SHIP_ID, sv.IN_VOY_NBR, sv.IN_CALL_NBR,sv.OUT_VOY_NBR, sv.OUT_CALL_NBR,sv.ACTIVE, sv.ACTIVE_SPARCS,
        sv.ETA, sv.ATA, sv.ATD, sv.ETD, sv.ARRIVED, sv.DISCHARGED,
        sv.WORK_START, sv.WORK_COMPLETE, sv.BERTH, sv.SHIP_SIDE_TO,
        sv.NOTES, sv.CREATED, sv.CREATOR, sv.CHANGED,
        sv.CHANGER, sv.EMPTY_PICKUP, sv.CARGO_CUTOFF, sv.REEFER_CUTOFF,
        sv.COMPLETED, sv.PUBLISHED_ETA, sv.PUBLISHED_ETD, sv.DISCHARGE_COMPLETED,
        sv.HAZARDOUS_CUTOFF, sv.CALL_YEAR, sv.BEGIN_RECEIVE
        FROM dm_ship_visits sv""";

    String lineSql = """SELECT CARGO_CUTOFF,CHANGED,CHANGER,CREATED,CREATOR,EMPTY_PICKUP,
				 HAZARDOUS_CUTOFF,LINE_ID,LINE_IN_VOY_NBR,LINE_OUT_VOY_NBR,REEFER_CUTOFF
			FROM
				DM_VESSEL_VISIT_LINES
			WHERE
			    VSL_CD='XXX'
			 	and CALL_YEAR ='YYY'
				and CALL_SEQ = 'ZZZ'
			""";
    private static final Logger log = Logger.getLogger(this.class)
}
