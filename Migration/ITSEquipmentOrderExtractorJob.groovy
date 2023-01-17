package Migration

import com.navis.argo.ArgoIntegrationEntity
import com.navis.argo.ArgoIntegrationField
import com.navis.argo.business.atoms.LogicalEntityEnum
import com.navis.external.argo.AbstractGroovyJobCodeExtension
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import groovy.sql.Sql
import org.apache.log4j.Logger
import wslite.json.JSONArray
import wslite.json.JSONObject

class ITSEquipmentOrderExtractorJob extends AbstractGroovyJobCodeExtension {
    private static final Logger logger = Logger.getLogger(this.class)

    @Override
    void execute(Map<String, Object> inParams) {

        try {
            PersistenceTemplate pt = new PersistenceTemplate(getUserContext())
            pt.invoke(new CarinaPersistenceCallback() {
                protected void doInTransaction() {
                    extractOrder()

                }
            })
        } catch (Exception e) {
            logger.warn("Exception occurred while executing ITSContainerUseExtractorJob")
        }

    }

    boolean isExtracted(String nbr) {
        DomainQuery domainQuery = QueryUtils.createDomainQuery(ArgoIntegrationEntity.INTEGRATION_SERVICE_MESSAGE)
        domainQuery.addDqPredicate(PredicateFactory.eq(ArgoIntegrationField.ISM_ENTITY_CLASS, LogicalEntityEnum.BKG));
        domainQuery.addDqPredicate(PredicateFactory.eq(ArgoIntegrationField.ISM_USER_STRING1, nbr))

        return HibernateApi.getInstance().existsByDomainQuery(domainQuery)
    }

    def extractOrder() {
        String orderSql = "SELECT DISTINCT * FROM LT_EquipmentOrder_VW"
        def util = getLibrary("ITSExtractorUtil")

        Sql sourceConn = util.establishDbConnection()
        sourceConn.eachRow(orderSql) {
            row ->
                def order = row
                if (!isExtracted(order.nbr)) {

                    LinkedHashMap orderMap = generateMainMap(order)
                    if (orderMap != null) {
                        JSONObject mainObj = new JSONObject(orderMap)
                        List eqoiList = [];
                        List hazList = [];
                        List holdMaps = [];

                        sourceConn.eachRow(EqoiSql.replace('XXX', row.nbr.toString()).replace('YYY', row.VESSEL_VISIT_ID.toString())) {
                            row1 ->
                                eqoiList.add(generateEqoiMap(row1));
                        }

                        JSONArray eqoiArray = new JSONArray(eqoiList)
                        mainObj.accumulate("eqoi-list", eqoiArray)
                        mainObj.put("eqoi_count", eqoiList.size())

                        sourceConn.eachRow("SELECT * FROM LT_EquipmentOrderHaz_VW WHERE NBR='$row.nbr' AND VESSEL_VISIT_ID = '$row.VESSEL_VISIT_ID'") {
                            row2 ->
                                hazList.add(generateHazardMap(row2));
                        }

                        JSONArray hazArray = new JSONArray(hazList)
                        mainObj.accumulate("hazard-list", hazArray)
                        mainObj.put("haz_count", hazList.size())

                        //TODO Holds?

                        util.logRequestToInterfaceMessage(com.navis.argo.business.atoms.LogicalEntityEnum.BKG, null, mainObj.toString(), (String) orderMap.get("nbr"))

                    }
                }
        }
    }

    def generateMainMap(def order) {
        def orderMap = [:]
        orderMap.put('vessel_visit_id', order.vessel_visit_id)
        orderMap.put('changed', order.changed)
        orderMap.put('changer', order.changer)
        orderMap.put('created', order.created)
        orderMap.put('creator', order.creator)
        orderMap.put('cutoff_override', order.cutoff_override)
        orderMap.put('destination', order.destination)
        orderMap.put('discharge_point_id1', order.discharge_point_id1)
        orderMap.put('discharge_point_id2', order.discharge_point_id2)
        orderMap.put('end_date', order.end_date)
        orderMap.put('flex1', order.flex1)
        orderMap.put('gkey', order.gkey)
        orderMap.put('line_id', order.line_id)
        orderMap.put('load_point_id', order.load_point_id)
        orderMap.put('nbr', order.nbr)
        orderMap.put('notes', order.notes)
        orderMap.put('shipper', order.shipper)
        orderMap.put('ship_id', order.ship_id)
        orderMap.put('special_stow', order.special_stow)
        orderMap.put('start_date', order.start_date)
        orderMap.put('status', order.status)
        orderMap.put('sub_type', order.sub_type)
        orderMap.put('trucker_id', order.trucker_id)
        orderMap.put('voy_nbr', order.voy_nbr)
        return orderMap
    }

    def generateEqoiMap(def eqoi) {
        def eqoiMap = [:]
        eqoiMap.put('acc_type_id', eqoi.acc_type_id)
//        eqoiMap.put('active_reefer', eqoi.active_reefer)
        eqoiMap.put('changed', eqoi.changed)
        // eqoiMap.put('changeid', eqoi.changeid)
        eqoiMap.put('iso_code', eqoi.iso_code)
        eqoiMap.put('changer', eqoi.changer)
//        eqoiMap.put('chs_qty', eqoi.chs_qty)
//        eqoiMap.put('chs_tally', eqoi.chs_tally)
        eqoiMap.put('client_sztp', eqoi.client_sztp)
        eqoiMap.put('co2_required', eqoi.co2_required)
        eqoiMap.put('commodity', eqoi.commodity)
        eqoiMap.put('commodity_code', eqoi.commodity_code)
        eqoiMap.put('created', eqoi.created)
        eqoiMap.put('creator', eqoi.creator)
//        eqoiMap.put('eqftr_id', eqoi.eqftr_id)
        eqoiMap.put('eqht_id', eqoi.eqht_id)
//        eqoiMap.put('eqo_gkey', eqoi.eqo_gkey)
        eqoiMap.put('eqsz_id', eqoi.eqsz_id)
        eqoiMap.put('eqtp_id', eqoi.eqtp_id)
//        eqoiMap.put('grade_id', eqoi.grade_id)
        eqoiMap.put('gross_units', eqoi.gross_units)
        eqoiMap.put('gross_weight', eqoi.gross_weight)
        eqoiMap.put('humidity', eqoi.humidity)
//        eqoiMap.put('material', eqoi.material)
        eqoiMap.put('o2_required', eqoi.o2_required)
        eqoiMap.put('qty', eqoi.qty)
        eqoiMap.put('tally', eqoi.tally)
        eqoiMap.put('tally_limit', eqoi.tally_limit)
//        eqoiMap.put('temp_recorder', eqoi.temp_recorder)
        eqoiMap.put('temp_required', eqoi.temp_required)
        eqoiMap.put('temp_units', eqoi.temp_units)
//        eqoiMap.put('vented', eqoi.vented)
        eqoiMap.put('vent_required', eqoi.vent_required)
        eqoiMap.put('vent_units', eqoi.vent_units)
//        eqoiMap.put('verifier', eqoi.verifier)
//        eqoiMap.put('verifier_changed', eqoi.verifier_changed)
        return eqoiMap
    }

    def generateHazardMap(def haz) {
        def hazMap = [:]
//        hazMap.put('approved', haz.approved)
//        hazMap.put('approver', haz.approver)
        hazMap.put('changed', haz.changed)
        hazMap.put('changer', haz.changer)
        hazMap.put('contact_phone', haz.contact_phone)
        hazMap.put('created', haz.created)
        hazMap.put('creator', haz.creator)
        hazMap.put('description', haz.description)
        hazMap.put('emergency_contact', haz.emergency_contact)
        hazMap.put('ems_nbr', haz.ems_nbr)
//        hazMap.put('fkey', haz.fkey)
//        hazMap.put('fk_type', haz.fk_type)
        hazMap.put('flash_point', haz.flash_point)
        hazMap.put('flash_point_units', haz.flash_point_units)
        hazMap.put('imdg_id', haz.imdg_id)
        hazMap.put('imdg_page', haz.imdg_page)
        hazMap.put('inhalation_zone', haz.inhalation_zone)
        hazMap.put('limited_qty_flag', haz.limited_qty_flag)
        hazMap.put('marine_pollutant', haz.marine_pollutant)
        hazMap.put('mfag_nbr', haz.mfag_nbr)
        hazMap.put('package_type', haz.package_type)
        hazMap.put('packing_group', haz.packing_group)
        hazMap.put('proper_name', haz.proper_name)
        hazMap.put('qty', haz.qty)
        hazMap.put('seq', haz.seq)
        hazMap.put('technical_name', haz.technical_name)
        hazMap.put('undg_nbr', haz.undg_nbr)
        hazMap.put('weight', haz.weight)
//        hazMap.put('weight_units', haz.weight_units)

        return hazMap
    }

    String EqoiSql = """
        SELECT *
              FROM
                LT_EquipmentOrderItem_VW eoi
              WHERE
	        eoi.NBR = 'XXX' and eoi.VESSEL_VISIT_ID = 'YYY' 
	 """;
}
