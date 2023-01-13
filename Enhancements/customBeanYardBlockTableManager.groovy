/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/

import com.navis.argo.ArgoEntity
import com.navis.argo.ArgoField
import com.navis.external.framework.beans.EBean
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DataQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.presentation.view.DefaultSharedUiTableManager

/**
 * @Author: mailto:uaarthi@weservetech.com, Aarthi U; Date: 11/15/2022
 *
 *  Requirements: Custom Table manager UI view to View/update Yard blocks as Deliverable/ Non deliverable, General Reference Key - DELIVERABLE_BLOCK
 *                TT-37 Yard Area Close - Updated groovy to provide Custom view to include Bay as Deliverable/ Non deliverable, General Reference Key - DELIVERABLE_BAY
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: customBeanYardBlockTableManager
 *     Code Extension Type:  BEAN_PROTOTYPE
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup Create the code Extension customBeanYardBlockTableManager in Global level
 *
 *
 *  S.No    Modified Date   Modified By     Jira      Description
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
                .addDqPredicate(PredicateFactory.in(ArgoField.REF_ID1, "DELIVERABLE_BLOCK", "DELIVERABLE_BAY"))
    }
}
