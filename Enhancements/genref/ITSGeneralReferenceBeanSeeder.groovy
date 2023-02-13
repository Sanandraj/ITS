/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/
import com.navis.external.framework.beans.ui.AbstractCustomUiBeanSeeder

/**
 *
 * @Author: mailto:uaarthi@weservetech.com, Aarthi U; Date: 05/09/2022
 *
 *  Requirements: SERVER_UI_TIER_LIFECYCLE  to seed the bean customBeanITSGenRefTableManager
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSGeneralReferenceBeanSeeder
 *     Code Extension Type: SERVER_UI_TIER_LIFECYCLE
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup Create the code in Code extension at Global level
 *
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */
class ITSGeneralReferenceBeanSeeder extends AbstractCustomUiBeanSeeder {
    @Override
    protected String[] getBeanNames() {
        return ["customBeanITSGenRefTableManager"];
    }

    @Override
    protected String[] getExtensionNames() {
        return ["customBeanITSGenRefTableManager"];
    }
}
