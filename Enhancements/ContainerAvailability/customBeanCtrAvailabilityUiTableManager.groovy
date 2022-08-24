import com.navis.external.framework.beans.EBean
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DataQuery
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.NullDataQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.presentation.view.DefaultSharedUiTableManager
import com.navis.inventory.InvEntity
import com.navis.inventory.InvField
import com.navis.inventory.business.api.UnitField
import org.apache.log4j.Logger

/**
 * Container Availability Query Table
 */
class customBeanCtrAvailabilityUiTableManager extends DefaultSharedUiTableManager implements EBean {
    private static final Logger logger = Logger.getLogger(this.class)

    @Override
    String getDetailedDiagnostics() {
        return "customBeanCtrAvailabilityUiTableManager"
    }

    @Override
    DataQuery createQuery() {
        Object returnValue = null;
        logger.warn("get attribute "+getAttribute("unitIds"))

        List<String> unitIds = (List)this.getRequestContext().getAttribute("unitIds");
        logger.warn("unitIds "+unitIds)
        if(unitIds != null && !unitIds.isEmpty()){
            DomainQuery dq = QueryUtils.createDomainQuery(InvEntity.UNIT_FACILITY_VISIT)
                    .addDqPredicate(PredicateFactory.in(UnitField.UFV_UNIT_ID, unitIds))
            return dq
        }

        return NullDataQuery.create();
    }


}
