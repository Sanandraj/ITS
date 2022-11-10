import com.navis.external.framework.beans.ui.AbstractCustomUiBeanSeeder

/**
 * @Author <a href="mailto:uaarthi@weservetech.com">Aarthi U</a>
 * To seed the Controller Bean
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
