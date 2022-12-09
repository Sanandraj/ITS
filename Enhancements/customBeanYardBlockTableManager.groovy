import com.navis.argo.ArgoEntity
import com.navis.argo.ArgoField
import com.navis.external.framework.beans.EBean
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DataQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.presentation.view.DefaultSharedUiTableManager

/**
 * uaarthi@weservetech.com 09/05/2022
 * Custom Table manager UI view to View/update Yard blocks as Deliverable/ Non deliverable, General Reference Key - DELIVERABLE_BLOCK
 *
 * uaarthi@weservetech.com 11/15/2022
 * TT-37 Yard Area Close - Updated groovy to provide Custom view to include Bay as Deliverable/ Non deliverable, General Reference Key - DELIVERABLE_BAY
 *
 */
class customBeanYardBlockTableManager extends DefaultSharedUiTableManager implements EBean {
    @Override
    String getDetailedDiagnostics() {
        return "customBeanYardBlockTableManager"
    }

    @Override
    DataQuery createQuery() {
        return QueryUtils.createDomainQuery(ArgoEntity.GENERAL_REFERENCE)
                .addDqPredicate(PredicateFactory.in(ArgoField.REF_ID1, "DELIVERABLE_BLOCK","DELIVERABLE_BAY"))
    }
}
