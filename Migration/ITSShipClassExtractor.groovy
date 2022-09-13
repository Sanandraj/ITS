package ITS.Migration

import com.navis.external.framework.AbstractExtensionCallback
import groovy.sql.Sql
import org.apache.log4j.Logger
import wslite.json.JSONObject

/**
 * Extract Shipclass into Integration Service Messages
 */

class ITSShipClassExtractor extends AbstractExtensionCallback {
    def util = getLibrary("ITSExtractorUtil")
    private static final Logger logger = Logger.getLogger(this.class)

    @Override
    void execute() {
        Sql sourceConn = util.establishDbConnection()
        sourceConn.eachRow(CLASS_SQL) {
            row ->
                LinkedHashMap sClassMap = generateSclassMap(row)
                if(sClassMap != null){
                    JSONObject jsonObject = new JSONObject(sClassMap)
                    util.logRequestToInterfaceMessage(com.navis.argo.business.atoms.LogicalEntityEnum.CV, null, jsonObject.toString(), (String) sClassMap.get("id"))
                }
        }

    }

    def generateSclassMap(sclass){
        def sclassMap = [:]
        sclassMap.put("id", sclass.id)
        sclassMap.put("name", sclass.name)
        sclassMap.put("active", sclass.active)
        sclassMap.put("active_sparcs", sclass.active_sparcs)
        sclassMap.put("self_sustaining", sclass.self_sustaining)
        sclassMap.put("loa", sclass.loa)
        sclassMap.put("loa_units", sclass.loa_units)
        sclassMap.put("beam", sclass.beam)
        sclassMap.put("beam_units", sclass.beam_units)
        sclassMap.put("bays_forward", sclass.bays_forward)
        sclassMap.put("bays_aft", sclass.bays_aft)
        sclassMap.put("bow_overhang", sclass.bow_overhang)
        sclassMap.put("bow_units", sclass.bow_units)
        sclassMap.put("stern_overhang", sclass.stern_overhang)
        sclassMap.put("stern_units", sclass.stern_units)
        sclassMap.put("notes", sclass.notes)
        sclassMap.put("created", sclass.created)
        sclassMap.put("creator", sclass.creator)
        sclassMap.put("changed", sclass.changed)
        sclassMap.put("changer", sclass.changer)
        sclassMap.put("bridge_to_bow", sclass.bridge_to_bow)
        sclassMap.put("bridge_to_bow_units", sclass.bridge_to_bow_units)
        sclassMap.put("grt", sclass.grt)
        sclassMap.put("nrt", sclass.nrt)
        return sclassMap
    }

    String CLASS_SQL = """
		SELECT
		  ID, NAME, ACTIVE,
		  ACTIVE_SPARCS, SELF_SUSTAINING, LOA,
		  LOA_UNITS, BEAM, BEAM_UNITS,
		  BAYS_FORWARD, BAYS_AFT,
		  BOW_OVERHANG, BOW_UNITS,
		  STERN_OVERHANG, STERN_UNITS,
		  NOTES, CREATED,
		  CREATOR, CHANGED,
		  CHANGER, SHIP_TYPE,
		  BRIDGE_TO_BOW, BRIDGE_TO_BOW_UNITS,
		  GRT, NRT
		FROM
		  dm_shipclasses
		  """;

}
