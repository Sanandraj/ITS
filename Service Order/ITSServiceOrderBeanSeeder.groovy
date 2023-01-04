

import com.navis.external.framework.beans.ui.AbstractCustomUiBeanSeeder

/**
 * @Author: uaarthi@weservetech.com; Date: 21-12-2022
 *
 *  Requirements: To seed the Service related Beans into N4
 *  customBeanITSShowCreateServiceOrderFormController
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSServiceOrderBeanSeeder
 *     Code Extension Type:  SERVER_UI_TIER_LIFECYCLE
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *
 *
 */
class ITSServiceOrderBeanSeeder extends AbstractCustomUiBeanSeeder{
    @Override
    protected String[] getBeanNames() {
        return ["customBeanITSShowCreateServiceOrderFormController"]
    }

    @Override
    protected String[] getExtensionNames() {
        return ["customBeanITSShowCreateServiceOrderFormController"]
    }
}
