import com.navis.external.framework.beans.EBean
import com.navis.framework.presentation.ui.ICarinaWidget
import com.navis.framework.presentation.ui.event.CarinaFormValueEvent
import com.navis.framework.presentation.ui.event.listener.AbstractCarinaFormValueListener
import com.navis.inventory.InventoryField
import com.navis.inventory.presentation.controller.UnitUpdateStorageFormController
import org.apache.log4j.Level
import org.apache.log4j.Logger

/*
*
*  @Author <ahref="mailto:mharikumar@weservetech.com"  >  Harikumar M</a>,
*  Date: 20/Oct/2022
*  Requirement: To make the LFD Reason Notes(flexField) visible and required only when LFD field not empty
*  @Inclusion Location	: Incorporated as a code extension of the type BEAN_PROTOTYPE.Copy --> Paste this code (customBeanUnitUpdateStorageFormController.groovy)
*
*/

class customBeanUnitUpdateStorageFormController extends UnitUpdateStorageFormController implements EBean {
    @Override
    boolean initForm() {
        LOGGER.warn("customUnitUpdateStorageFormController initform.")
        return super.initForm()
    }

    @Override
    protected void configure() {
        super.configure()
        //LOGGER.setLevel(Level.DEBUG)
        ICarinaWidget flexWidget = getFormWidget(InventoryField.UFV_FLEX_STRING01);
        if (flexWidget != null) {
            flexWidget.setRequired(false);
        }
        final ICarinaWidget lineLfdWidget = this.getFormWidget(InventoryField.UFV_LINE_LAST_FREE_DAY);
        if (lineLfdWidget != null) {
            lineLfdWidget.addFormValueListener(new AbstractCarinaFormValueListener() {
                protected void safeValueChanged(CarinaFormValueEvent inEvent) {

                    String value = lineLfdWidget?.getUiValue();
                    if (value != null) {
                        flexWidget.setRequired(true);
                    } else {
                        flexWidget.setRequired(false);
                    }
                }
            });
        }

    }

    private static final Logger LOGGER = Logger.getLogger(customBeanUnitUpdateStorageFormController.class);

}
