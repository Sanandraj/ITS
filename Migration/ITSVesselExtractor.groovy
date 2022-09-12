package ITS.Migration

import com.navis.external.framework.AbstractExtensionCallback
import groovy.sql.Sql
import org.apache.log4j.Logger
import wslite.json.JSONObject

/**
 * Extract Vessel into Integration Service Messages
 */

class ITSVesselExtractor extends AbstractExtensionCallback {
    def util = getLibrary("ITSExtractorUtil")
    private static final Logger logger = Logger.getLogger(this.class)

    @Override
    void execute() {
        Sql sourceConn = util.establishDbConnection()
        sourceConn.eachRow(VSL_SQL) {
            row ->
                LinkedHashMap vslMap = generateVslMap(row)
                if(vslMap != null){
                    JSONObject jsonObject = new JSONObject(vslMap)
                    logger.warn("jsonObject "+jsonObject.toString())
                    util.logRequestToInterfaceMessage(com.navis.argo.business.atoms.LogicalEntityEnum.VES, null, jsonObject.toString(), (String) vslMap.get("id"))
                }
        }

    }

    def generateVslMap(vsl){
        def vslMap = [:]
        vslMap.put("id", vsl.id)
        vslMap.put("lloyds_id", vsl.lloyds_id)
        vslMap.put("name", vsl.name)
        vslMap.put("sclass_id", vsl.sclass_id)
        vslMap.put("line_id", vsl.line_id)
        vslMap.put("captain", vsl.captain)
        vslMap.put("radio_call_sign", vsl.radio_call_sign)
        vslMap.put("country_id", vsl.country_id)
        vslMap.put("active", vsl.active)
        vslMap.put("active_sparcs", vsl.active_sparcs)
        vslMap.put("notes", vsl.notes)
       // vslMap.put("created", vsl.created)
        vslMap.put("creator", vsl.creator)
       // vslMap.put("changed", vsl.changed)
        vslMap.put("changer", vsl.changer)
      //  vslMap.put("stowage_scheme", vsl.stowage_scheme)
        // vslMap.put("common_carrier", vsl.common_carrier)
        vslMap.put("documentation_nbr", vsl.documentation_nbr)
        return vslMap
    }

    String VSL_SQL = """
		SELECT
		  s.ID, isnull(s.LLOYDS_ID, s.id) LLOYDS_ID,
		  s.NAME, s.SCLASS_ID,s.LINE_ID,
		  s.CAPTAIN,s.RADIO_CALL_SIGN,s.COUNTRY_ID,
		  s.ACTIVE, s.ACTIVE_SPARCS, s.NOTES,
		   s.CREATOR,s.CHANGER, 
		  s.DOCUMENTATION_NBR
		FROM
		  dm_ships s
		  """;

    //  s.STOWAGE_SCHEME, s.COMMON_CARRIER,

}
