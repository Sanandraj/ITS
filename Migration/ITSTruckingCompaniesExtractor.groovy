import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.integration.IntegrationServiceMessage
import com.navis.argo.business.model.ArgoSequenceProvider
import com.navis.carina.integrationservice.business.IntegrationService
import com.navis.external.framework.AbstractExtensionCallback
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.util.scope.ScopeCoordinates
import groovy.sql.Sql
import org.apache.log4j.Logger
import wslite.json.JSONObject

/**
 * Extract Trucking Companies into Integration Service Messages
 */
class ITSTruckingCompaniesExtractor extends AbstractExtensionCallback {
    def util = getLibrary("ITSExtractorUtil")
    private static final Logger logger = Logger.getLogger(this.class)

    @Override
    void execute() {

        Sql sourceConn = util.establishDbConnection()
        sourceConn.eachRow(TRKCSQL) {
            row ->
                LinkedHashMap trkcMap = generateTrkcMap(row)
                JSONObject jsonObject = new JSONObject(trkcMap)
                logger.warn("jsonObject "+jsonObject.toString())
                util.logRequestToInterfaceMessage(com.navis.argo.business.atoms.LogicalEntityEnum.TV, null, jsonObject.toString(), (String) trkcMap.get("ID"))

        }

    }


    def generateTrkcMap(def trkc) {
        def trkcMap = [:]
        trkcMap.put('ID', trkc.ID)
        trkcMap.put('NAME', trkc.NAME)
        trkcMap.put('SCAC', trkc.SCAC)
        trkcMap.put('CONTACT', trkc.CONTACT)
        trkcMap.put('ADR1', trkc.ADR1)
        trkcMap.put('ADR2', trkc.ADR2)
        trkcMap.put('CITY', trkc.CITY)
        trkcMap.put('STATE', trkc.STATE)
        trkcMap.put('COUNTRY', trkc.COUNTRY)
        trkcMap.put('ZIPCODE', trkc.ZIPCODE)
        trkcMap.put('FAX', trkc.FAX)
        trkcMap.put('TELNBR', trkc.TELNBR)
        trkcMap.put('EMAIL', trkc.EMAIL)
        trkcMap.put('NOTES', trkc.NOTES)
        trkcMap.put('CREATED', trkc.CREATED)
        trkcMap.put('CREATOR', trkc.CREATOR)
        trkcMap.put('CHANGED', trkc.CHANGED)
        trkcMap.put('CHANGER', trkc.CHANGER)
        return trkcMap
    }


    String TRKCSQL = """
			SELECT ID,NAME,SCAC,
                 CONTACT,ADR1,ADR2,
                 CITY,STATE,
                 COUNTRY,ZIPCODE,FAX,
                 TELNBR,EMAIL,NOTES,
                 created,creator,
                 changed,changer
			FROM
				DM_TRUCKING_COMPANIES
            """;
}
