import com.navis.external.framework.beans.EBean
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.presentation.ui.ICarinaWidget
import com.navis.framework.presentation.ui.event.CarinaFormValueEvent
import com.navis.framework.presentation.ui.event.listener.AbstractCarinaFormValueListener
import com.navis.inventory.presentation.controller.UnitUpdateStorageFormController
import org.apache.log4j.Level
import org.apache.log4j.Logger

class customBeanUnitUpdateStorageFormController extends UnitUpdateStorageFormController implements EBean {
    @Override
    boolean initForm() {
        LOGGER.warn("customUnitUpdateStorageFormController initform.")
        return super.initForm()
    }

    @Override
    protected void configure() {
        super.configure()
       // LOGGER.setLevel(Level.DEBUG)
        ICarinaWidget flexWidget = getFormWidget(MetafieldIdFactory.valueOf("ufvFlexString01"));
        if (flexWidget != null) {
            flexWidget.setVisible(false);
            flexWidget.setRequired(false);
        }
        final ICarinaWidget lfdWidget = this.getFormWidget(MetafieldIdFactory.valueOf("ufvLastFreeDay"));
        if (lfdWidget != null) {
            lfdWidget.addFormValueListener(new AbstractCarinaFormValueListener() {
                protected void safeValueChanged(CarinaFormValueEvent inEvent) {

                    String lfdValue = lfdWidget.getUiValue();
                    if (lfdValue != null) {
                        flexWidget.setVisible(true);
                        flexWidget.setRequired(true);
                    } else {
                        flexWidget.setVisible(false);
                        flexWidget.setRequired(false);
                    }
                }
            });
        }
    }
    private static final Logger LOGGER = Logger.getLogger(customBeanUnitUpdateStorageFormController.class);

}
