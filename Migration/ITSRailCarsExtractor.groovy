package ITS.Migration

import com.navis.external.framework.AbstractExtensionCallback
import groovy.sql.Sql
import org.apache.log4j.Logger
import wslite.json.JSONObject

/**
 * Extract Railcar into Integration Service Messages
 */

class ITSRailCarsExtractor extends AbstractExtensionCallback {
    def util = getLibrary("ITSExtractorUtil")
    private static final Logger logger = Logger.getLogger(this.class)

    @Override
    void execute() {

        Sql sourceConn = util.establishDbConnection()
        sourceConn.eachRow(RCAR_SQL) {
            row ->
                LinkedHashMap rcarMap = generateRcarMap(row)
                if(rcarMap != null) {
                    JSONObject jsonObject = new JSONObject(rcarMap)
                    util.logRequestToInterfaceMessage(com.navis.argo.business.atoms.LogicalEntityEnum.RCARV, null, jsonObject.toString(), (String) rcarMap.get("NBR"))
                }
        }
    }

    def generateRcarMap(def rcar) {
        def rcarMap = [:]
        rcarMap.put('TYPE_ID', rcar.TYPE_ID)
        rcarMap.put('NBR', rcar.NBR)
        rcarMap.put('OWNER_ID', rcar.OWNER_ID)
        rcarMap.put('SAFE_WEIGHT', rcar.SAFE_WEIGHT)
        rcarMap.put('SAFE_UNIT', rcar.SAFE_UNIT)
        rcarMap.put('STATUS', rcar.STATUS)
        rcarMap.put('LENGTH', rcar.LENGTH)
        rcarMap.put('LENGTH_UNIT', rcar.LENGTH_UNIT)
        rcarMap.put('CREATED', rcar.CREATED)
        rcarMap.put('CHANGED', rcar.CHANGED)
        return rcarMap
    }

    String RCAR_SQL = """
                SELECT TYPE_ID, NBR, OWNER_ID, SAFE_WEIGHT, SAFE_UNIT,
                    STATUS, LENGTH, LENGTH_UNIT,
                    CREATED, CHANGED
                FROM
                    DM_RAILCARS
            """;
}
