/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.external.framework.beans.ui.AbstractCustomUiBeanSeeder

/*
     *
     * @Author : Gopinath Kannappan, 29/Dec/2022
     *
     * Requirements : 7-9 Apply Waiver to Multiple Units -- This groovy is used to load these controller - customBeanITSRecordGuaranteeFormController and customBeanITSRecordWaiverFormController.
     *
     * @Inclusion Location	: Incorporated as a code extension of the type SERVER_UI_TIER_LIFE_CYCLE.
     *
     *  Load Code Extension to N4:
            1. Go to Administration --> System -->  Code Extension
            2. Click Add (+)
            3. Enter the values as below:
                Code Extension Name:  ITSGuaranteeAndWaiverBeanSeeder
                Code Extension Type:  SERVER_UI_TIER_LIFE_CYCLE
               Groovy Code: Copy and paste the contents of groovy code.
            4. Click Save button

     *  Set up  />
     *
     *
 */


class ITSGuaranteeAndWaiverBeanSeeder extends AbstractCustomUiBeanSeeder {



    protected String[] getBeanNames() {
        return ["customBeanITSRecordGuaranteeFormController", "customBeanITSRecordWaiverFormController"];
    }

    @Override
    protected String[] getExtensionNames() {
        return ["customBeanITSRecordGuaranteeFormController", "customBeanITSRecordWaiverFormController"];
    }


}

