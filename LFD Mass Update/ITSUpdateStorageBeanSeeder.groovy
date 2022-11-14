
import com.navis.external.framework.beans.ui.AbstractCustomUiBeanSeeder

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
