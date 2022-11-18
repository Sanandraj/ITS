/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/

import com.navis.external.framework.beans.ui.AbstractCustomUiBeanSeeder

/**
 *
 * @Author: mailto:mailto:mharikumar@weservetech.com, Harikumar M; Date: 20/10/2022
 *
 *  Requirements: This groovy is to seed the new bean
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSUpdateStorageBeanSeeder
 *     Code Extension Type: SERVER_UI_TIER_LIFECYCLE
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 * */

class ITSUpdateStorageBeanSeeder extends AbstractCustomUiBeanSeeder {
    @Override
    protected String[] getBeanNames() {
        return ["customBeanUnitUpdateStorageFormController", "customBeanLFDMassUpdateFormController"]
    }

    @Override
    protected String[] getExtensionNames() {
        return ["customBeanUnitUpdateStorageFormController", "customBeanLFDMassUpdateFormController"];
    }
}

