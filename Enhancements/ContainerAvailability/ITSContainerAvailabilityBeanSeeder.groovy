import com.navis.external.framework.beans.ui.AbstractCustomUiBeanSeeder

/**
 * To seed the Controller Bean
 */
class ITSContainerAvailabilityBeanSeeder extends  AbstractCustomUiBeanSeeder{
    @Override
    protected String[] getBeanNames() {
        return ["customBeanITSContainerAvailabilityFormController","customBeanCtrAvailabilityUiTableManager"]
    }

    @Override
    protected String[] getExtensionNames() {
        return ["customBeanITSContainerAvailabilityFormController","customBeanCtrAvailabilityUiTableManager"]
    }
}
