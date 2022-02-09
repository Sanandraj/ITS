import com.navis.argo.business.api.LogicalEntity
import com.navis.argo.business.atoms.EquipClassEnum
import com.navis.external.framework.AbstractExtensionCallback
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.query.common.api.QueryResult
import com.navis.inventory.InvEntity
import com.navis.inventory.InvField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.services.ServicesEntity
import com.navis.services.ServicesField
import com.navis.services.business.event.Event
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger

/**
 * @Author <ahref="mailto:annalakshmig@weservetech.com" > ANNALAKSHMI G</a>
 *  * Date: 09/02/2022
 * Added as Library to
 */
class ITSBaseUtilLibrary extends AbstractExtensionCallback {
    public List<Event> getSpecificEventList(LogicalEntity inLogicalEntity, boolean isFullMode, List EVENT_LIST) {
        DomainQuery dq = QueryUtils.createDomainQuery(ServicesEntity.EVENT)
                .addDqPredicate(PredicateFactory.eq(ServicesField.EVNT_APPLIED_TO_CLASS, inLogicalEntity.getLogicalEntityType().getKey()))
                .addDqPredicate(PredicateFactory.eq(ServicesField.EVNT_APPLIED_TO_PRIMARY_KEY, inLogicalEntity.getPrimaryKey()))
                .addDqOrdering(Ordering.desc(ServicesField.EVNT_GKEY));
        if (!isFullMode) {
            dq.addDqPredicate(PredicateFactory.in(MetafieldIdFactory.valueOf("evntEventType.evnttypeId"), EVENT_LIST))
            /*dq.addDqPredicate(PredicateFactory.eq(EVENT_ID, EVENT_LIST))*/
        }
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
    }

    public String validateMandatoryFields(String ctrNumber) {
        String errorMsg = null;
        if (StringUtils.isEmpty(ctrNumber)) {
            errorMsg = ERROR_MSG
        }

        return errorMsg
    }

    public Map getUnitMap(QueryResult rs, boolean isFullMode) {
        Map<Serializable, String> map = new HashMap<>()
        if (rs.getTotalResultCount() > 0) {
            for (int i = 0; i < rs.getTotalResultCount(); i++) {
                if (isFullMode) {
                    map.put(rs.getValue(i, InvField.UFV_GKEY) as Serializable, rs.getValue(i, UnitField.UFV_UNIT_ID).toString())
                } else {
                    if (!map.containsValue(rs.getValue(i, UnitField.UFV_UNIT_ID))) {
                        map.put(rs.getValue(i, InvField.UFV_GKEY) as Serializable, rs.getValue(i, UnitField.UFV_UNIT_ID).toString())
                    }
                }
            }
        }
        return map;
    }

    public QueryResult fetchUnitList(String[] unitNbrs, EquipClassEnum equipClassEnum) {
        DomainQuery dq = QueryUtils.createDomainQuery(InvEntity.UNIT_FACILITY_VISIT)
                .addDqField(UnitField.UFV_UNIT_ID)
                .addDqField(InvField.UFV_GKEY)
                .addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("ufvUnit.unitEquipment.eqClass"), equipClassEnum))
                .addDqPredicate(PredicateFactory.in(UnitField.UFV_UNIT_ID, unitNbrs))
                .addDqPredicate(PredicateFactory.ne(InvField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S20_INBOUND))
                .addDqPredicate(PredicateFactory.ne(InvField.UFV_VISIT_STATE, UnitVisitStateEnum.ADVISED))
                .addDqOrdering(Ordering.desc(InvField.UFV_TIME_OF_LAST_MOVE))
        return HibernateApi.getInstance().findValuesByDomainQuery(dq)
    }

    private static Logger LOGGER = Logger.getLogger(this.class);
    private static final String ERROR_MSG = "Missing Field"

}
