/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/

import com.navis.external.framework.beans.EBean
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.*
import com.navis.framework.presentation.view.DefaultSharedUiTableManager
import com.navis.inventory.InvEntity
import com.navis.inventory.business.api.UnitField

/**
 *
 * @Author: mailto:uaarthi@weservetech.com, Aarthi U; Date: 09/08/2022
 *
 *  Requirements: To display the Units selected in Container Availability Query Table.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: customBeanCtrAvailabilityUiTableManager
 *     Code Extension Type: BEAN_PROTOTYPE
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup UI Table manager configured in CUSTOM_TABLE_VIEW_AVAILABILITY
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */

class customBeanCtrAvailabilityUiTableManager extends DefaultSharedUiTableManager implements EBean {

    @Override
    String getDetailedDiagnostics() {
        return "customBeanCtrAvailabilityUiTableManager"
    }

    @Override
    DataQuery createQuery() {


        List<String> unitIds = (List) getAttribute("unitIds");
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
