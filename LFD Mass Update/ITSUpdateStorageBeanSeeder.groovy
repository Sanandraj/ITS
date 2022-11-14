
import com.navis.external.framework.beans.ui.AbstractCustomUiBeanSeeder

/**
 *@Author <ahref="mailto:mharikumar@weservetech.com"  >  Harikumar M</a>,
 *  Date: 20/Oct/2022
 * Requirement:This groovy is to seed the new bean
 *
 **/

class ITSUpdateStorageBeanSeeder extends AbstractCustomUiBeanSeeder {
    @Override
    protected String[] getBeanNames() {
        return ["customBeanUnitUpdateStorageFormController" ,"customBeanLFDMassUpdateFormController"]
    }

    @Override
    protected String[] getExtensionNames() {
        return ["customBeanUnitUpdateStorageFormController" , "customBeanLFDMassUpdateFormController"];
    }
}
