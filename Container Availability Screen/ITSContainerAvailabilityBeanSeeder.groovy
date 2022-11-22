/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/
import com.navis.external.framework.beans.ui.AbstractCustomUiBeanSeeder

/**
 * @Author <a href="mailto:uaarthi@weservetech.com">Aarthi U</a>, 09/08/2022
 *
 *  Requirements: To seed the Controller Bean, uiManager and value convertor
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSContainerAvailabilityBeanSeeder
 *     Code Extension Type: SERVER_UI_TIER_LIFECYCLE
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */

class ITSContainerAvailabilityBeanSeeder extends  AbstractCustomUiBeanSeeder{
    @Override
    protected String[] getBeanNames() {
        return ["customBeanITSContainerAvailabilityFormController","customBeanCtrAvailabilityUiTableManager","customBeanCtrAvailablityValueConverter"]
    }

    @Override
    protected String[] getExtensionNames() {
        return ["customBeanITSContainerAvailabilityFormController","customBeanCtrAvailabilityUiTableManager","customBeanCtrAvailablityValueConverter"]
    }
}
