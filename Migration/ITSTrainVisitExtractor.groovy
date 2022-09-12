package ITS.Migration

import com.navis.external.framework.AbstractExtensionCallback
import groovy.sql.Sql
import org.apache.log4j.Logger
import wslite.json.JSONArray
import wslite.json.JSONObject

class ITSTrainVisitExtractor extends AbstractExtensionCallback {
    def util = getLibrary("ITSExtractorUtil")
    private static final Logger logger = Logger.getLogger(this.class)

    @Override
    void execute() {
        Sql sourceConn = util.establishDbConnection()
        sourceConn.eachRow(TV_SQL) {
            row ->
                row.TrainScheduleId
                LinkedHashMap tvdMap = generateTvdtlsMap(row)
                if(tvdMap != null) {

                    def rcarvList = []
                    sourceConn.eachRow(RCV_SQL.replace('XXX', row.TrainScheduleId.toString())) {
                        rcarVisits ->
                            rcarvList.add(generateRcarv(rcarVisits));
                    }
                    JSONArray rcarArray = new JSONArray(rcarvList)

                    tvdMap.put("rcav_count",rcarvList.size())
                    JSONObject jsonObject = new JSONObject(tvdMap)
                    jsonObject.accumulate("railcar-visit", rcarArray)
                    logger.warn("jsonObject "+jsonObject.toString())
                    util.logRequestToInterfaceMessage(com.navis.argo.business.atoms.LogicalEntityEnum.RV, null, jsonObject.toString(), (String) tvdMap.get("TRAIN_ID"))
                }
        }
    }

    def generateTvdtlsMap(def tvd) {
        def tvdMap = [:]
        tvdMap.put('TRAIN_ID', tvd.TRAIN_ID)
        tvdMap.put('ACTIVE', tvd.ACTIVE)
        tvdMap.put('ACTIVE_SPARCS', tvd.ACTIVE_SPARCS)
        tvdMap.put('RR_ID', tvd.RR_ID)
        tvdMap.put('TRACK', tvd.TRACK)
        tvdMap.put('DIRECTION', tvd.DIRECTION)
        tvdMap.put('ETA', tvd.ETA)
        tvdMap.put('ETD', tvd.ETD)
        tvdMap.put('ATA', tvd.ATA)
        tvdMap.put('ATD', tvd.ATD)
        tvdMap.put('DISCHARGED', tvd.DISCHARGED)
        tvdMap.put('NOTES', tvd.NOTES)
        tvdMap.put('CREATED', tvd.CREATED)
        tvdMap.put('CREATOR', tvd.CREATOR)
        tvdMap.put('CHANGED', tvd.CHANGED)
        tvdMap.put('CHANGER', tvd.CHANGER)
        tvdMap.put('RR_TRAIN_ID', tvd.RR_TRAIN_ID)
        tvdMap.put('LINE_ID', tvd.LINE_ID)
        tvdMap.put('RAIL_SRVC_ID', tvd.RAIL_SRVC_ID)
        tvdMap.put('TrainScheduleId', tvd.TrainScheduleId)
        return tvdMap
    }

    def generateRcarv(def rcarv) {
        def rcarvMap = [:]
        rcarvMap.put('RCAR_NBR', rcarv.RCAR_NBR)
        rcarvMap.put('IN_RR_ID', rcarv.IN_RR_ID)
        rcarvMap.put('IN_SEQ', rcarv.IN_SEQ)
        rcarvMap.put('LOAD_POINT', rcarv.LOAD_POINT)
        rcarvMap.put('DISCHARGE_POINT', rcarv.DISCHARGE_POINT)
        rcarvMap.put('POINT_ID', rcarv.POINT_ID)
        rcarvMap.put('CREATED', rcarv.CREATED)
        rcarvMap.put('CREATOR', rcarv.CREATOR)
        rcarvMap.put('CHANGED', rcarv.CHANGED)
        rcarvMap.put('CHANGER', rcarv.CHANGER)
        rcarvMap.put('GKEY', rcarv.GKEY)
        rcarvMap.put('SEAL_NBR1', rcarv.SEAL_NBR1)
        rcarvMap.put('SEAL_NBR2', rcarv.SEAL_NBR2)
        return rcarvMap
    }

    String TV_SQL = """
			SELECT TRAIN_ID,
				ACTIVE, ACTIVE_SPARCS, RR_ID,
				TRACK, DIRECTION, ETA,
				ETD, ATA, ATD,
				DISCHARGED, NOTES,
				CREATED, CREATOR, CHANGED, CHANGER,
				RR_TRAIN_ID, LINE_ID, RAIL_SRVC_ID,
				TrainScheduleId
			FROM DM_TRAIN_VISITS
		    """;

    String RCV_SQL = """
      			SELECT RCAR_NBR, IN_RR_ID, IN_SEQ,
				LOAD_POINT, DISCHARGE_POINT, POINT_ID,
				CREATED, CREATOR, CHANGED, CHANGER,
				GKEY, SEAL_NBR1, SEAL_NBR2
            FROM DM_RCAR_VISITS
			WHERE gkey = 'XXX'
			""";
}
