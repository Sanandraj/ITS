import com.navis.external.framework.beans.EBean
import com.navis.framework.presentation.command.VariformUiCommand
import com.navis.framework.presentation.ui.CarinaButton
import com.navis.framework.presentation.ui.FormController
import com.navis.framework.presentation.ui.ICarinaWidget
import com.navis.framework.presentation.ui.event.CarinaUIEvent
import com.navis.framework.presentation.ui.event.listener.AbstractCarinaEventListener
import com.navis.inventory.business.api.UnitField
import com.navis.road.RoadField
import org.apache.log4j.Logger


/**
 * Author - 09/08 uaarthi@weservetech.com
 * Form controller configured in ITS_CONTAINER_AVAILABILITY
 */

class customBeanITSContainerAvailabilityFormController extends FormController implements EBean {
    private static final Logger LOGGER = Logger.getLogger(this.class)
    private List selectedKeys;
    private def unitIds = new ArrayList<String>();


    String getDetailedDiagnostics() {
        return "customBeanITSContainerAvailabilityFormController";
    }

    @Override
    protected void configure() {
        super.configure()
        selectedKeys = (List) this.getAttribute("keys");
        LOGGER.warn("selectedKeys "+selectedKeys)


    }

    protected void addSaveButtonListener(CarinaButton inSaveButton) {
        inSaveButton.addListener(new AbstractCarinaEventListener() {
            protected void safeActionPerformed(CarinaUIEvent inCarinaUIEvent) {
                Logger LOGGER = Logger.getLogger(this.class)
                final ICarinaWidget ufvUnitWidget = getFormWidget(UnitField.UFV_UNIT_ID);
                LOGGER.warn("ufvUnitWidget "+ufvUnitWidget)
                unitIds = new ArrayList<String>();
                if (ufvUnitWidget != null) {
                    String unit = ufvUnitWidget.getValue()
                    LOGGER.warn("unit "+unit)
                    if(unit){
                        unitIds.addAll(unit.split("[ ,]+"))
                        LOGGER.warn("unitIds "+unitIds)
                    }
                }
                displaySelectedUnits()
                getFormUiProcessor().hideWindow(customBeanITSContainerAvailabilityFormController.this);
            }
        });
    }




    private void displaySelectedUnits() {
        VariformUiCommand command = new VariformUiCommand("CUSTOM_TABLE_VIEW_AVAILABILITY");
        command.setAttribute("entityname", "UnitFacilityVisit");
        command.setAttribute("keys", getPrimaryKeys());
        command.setAttribute("unitIds",unitIds)
        command.setSource(selectedKeys);

        Map args = new HashMap();
        args.put("entityname", "UnitFacilityVisit");
        args.put("keys", getPrimaryKeys());
        args.put("unitIds",unitIds)
        command.execute(args);
    }


}