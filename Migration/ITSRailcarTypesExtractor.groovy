package ITS.Migration

import com.navis.external.framework.AbstractExtensionCallback
import groovy.sql.Sql
import org.apache.log4j.Logger
import wslite.json.JSONObject

class ITSRailcarTypesExtractor extends AbstractExtensionCallback {
    def util = getLibrary("ITSExtractorUtil")
    private static final Logger logger = Logger.getLogger(this.class)

    @Override
    void execute() {
        Sql sourceConn = util.establishDbConnection()
        sourceConn.eachRow(RTYPE_SQL) {
            row ->
                LinkedHashMap rcarTypeMap = generateRctypeMap(row)
                if(rcarTypeMap != null){
                    JSONObject jsonObject = new JSONObject(rcarTypeMap)
                  //  logger.warn("jsonObject "+jsonObject.toString())
                    util.logRequestToInterfaceMessage(com.navis.argo.business.atoms.LogicalEntityEnum.RO, null, jsonObject.toString(), (String) rcarTypeMap.get("id"))
                }
        }

    }

    def generateRctypeMap(rcarType){
        def rcarTypeMap = [:]
        rcarTypeMap.put("id", rcarType.id)
        rcarTypeMap.put("NAME", rcarType.NAME)
        rcarTypeMap.put("DESCRIPTION", rcarType.DESCRIPTION)
        rcarTypeMap.put("STATUS", rcarType.STATUS)
        rcarTypeMap.put("CAR_TYPE", rcarType.CAR_TYPE)
        rcarTypeMap.put("MAX_20S", rcarType.MAX_20S)
        rcarTypeMap.put("MAX_TIER", rcarType.MAX_TIER)
        rcarTypeMap.put("FLOOR_HEIGHT", rcarType.FLOOR_HEIGHT)
        rcarTypeMap.put("HEIGHT_UNIT", rcarType.HEIGHT_UNIT)
        rcarTypeMap.put("FLOOR_LENGTH", rcarType.FLOOR_LENGTH)
        rcarTypeMap.put("LENGTH_UNIT", rcarType.LENGTH_UNIT)
        rcarTypeMap.put("TARE_WEIGHT", rcarType.TARE_WEIGHT)
        rcarTypeMap.put("TARE_UNIT", rcarType.TARE_UNIT)
        rcarTypeMap.put("SAFE_WEIGHT", rcarType.SAFE_WEIGHT)
        rcarTypeMap.put("SAFE_UNIT", rcarType.SAFE_UNIT)
      /*  rcarTypeMap.put("CUBIC_CAPACITY", rcarType.CUBIC_CAPACITY)
        rcarTypeMap.put("CUBIC_UNIT", rcarType.CUBIC_UNIT)
        rcarTypeMap.put("country_id", rcarType.country_id)
        rcarTypeMap.put("TEU_CAPACITY", rcarType.TEU_CAPACITY)
        rcarTypeMap.put("VCG", rcarType.VCG)
        rcarTypeMap.put("VCG_UNIT", rcarType.VCG_UNIT)
        rcarTypeMap.put("NUM_PLATFORMS", rcarType.NUM_PLATFORMS)*/
        rcarTypeMap.put("HIGH_SIDE", rcarType.HIGH_SIDE)
        rcarTypeMap.put("CREATED", rcarType.CREATED)
        rcarTypeMap.put("CREATOR", rcarType.CREATOR)
        rcarTypeMap.put("CHANGED", rcarType.CHANGED)
        rcarTypeMap.put("CHANGER", rcarType.CHANGER)
        return rcarTypeMap
    }

    String RTYPE_SQL = """
		SELECT
        ID, NAME, STATUS,
        DESCRIPTION,
        CAR_TYPE, MAX_20S, MAX_TIER,
        FLOOR_HEIGHT , HEIGHT_UNIT,
        FLOOR_LENGTH, LENGTH_UNIT,
        TARE_WEIGHT, TARE_UNIT,
        SAFE_WEIGHT, SAFE_UNIT,
        HIGH_SIDE, CREATED, CREATOR,
        CHANGED, CHANGER
            FROM DM_RailcarTypes
		  """;
    // CUBIC_CAPACITY, CUBIC_UNIT,
    //        TEU_CAPACITY, VCG,
    //        VCG_UNIT, NUM_PLATFORMS,
}
