import com.navis.external.framework.beans.EBean
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.*
import com.navis.framework.presentation.view.DefaultSharedUiTableManager
import com.navis.inventory.InvEntity
import com.navis.inventory.business.api.UnitField
import org.apache.log4j.Logger

/**
 * @Author <a href="mailto:uaarthi@weservetech.com">Aarthi U</a>
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
        logger.warn("get attribute " + getAttribute("unitIds"))


        List<String> unitIds = (List) getAttribute("unitIds");
        logger.warn("unitIds " + unitIds)
        if (unitIds != null && !unitIds.isEmpty()) {


            Junction unitJunction = PredicateFactory.conjunction()
            unitJunction.add(PredicateFactory.in(UnitField.UFV_UNIT_ID, unitIds));

            Junction blJuction = PredicateFactory.conjunction()
            blJuction.add(PredicateFactory.in(UnitField.UFV_GDS_BL_NBR, unitIds));

            Disjunction disjunction = new Disjunction();
            disjunction.add(unitJunction);
            disjunction.add(blJuction);

            DomainQuery dq = QueryUtils.createDomainQuery(InvEntity.UNIT_FACILITY_VISIT);
            dq.addDqPredicate(disjunction)

            return dq
        }
        return NullDataQuery.create();
    }


}
