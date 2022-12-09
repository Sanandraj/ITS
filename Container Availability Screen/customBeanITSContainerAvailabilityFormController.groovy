/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/
import com.navis.external.framework.beans.EBean
import com.navis.framework.presentation.command.DestinationEnum
import com.navis.framework.presentation.command.VariformUiCommand
import com.navis.framework.presentation.ui.CarinaButton
import com.navis.framework.presentation.ui.FormController
import com.navis.framework.presentation.ui.ICarinaWidget
import com.navis.framework.presentation.ui.event.CarinaUIEvent
import com.navis.framework.presentation.ui.event.listener.AbstractCarinaEventListener
import com.navis.inventory.business.api.UnitField
import org.apache.log4j.Logger

/**
 *
 * @Author: mailto:uaarthi@weservetech.com, Aarthi U; Date: 09/08/2022
 *
 *  Requirements: To process the Units selected and displays it in Container availability table.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: customBeanITSContainerAvailabilityFormController
 *     Code Extension Type: BEAN_PROTOTYPE
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup Form controller configured in CUSTOM_ITS_CONTAINER_AVAILABILITY
 *
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */


class customBeanITSContainerAvailabilityFormController extends FormController implements EBean {
    private static final Logger LOGGER = Logger.getLogger(this.class)
    private List selectedKeys;
    private List<String> unitIds = new ArrayList<String>();


    String getDetailedDiagnostics() {
        return "customBeanITSContainerAvailabilityFormController";
    }

    @Override
    protected void configure() {
        super.configure()
        selectedKeys = (List) this.getAttribute("keys");
    }


    protected void addSaveButtonListener(CarinaButton inSaveButton) {
        inSaveButton.addListener(new AbstractCarinaEventListener() {
            protected void safeActionPerformed(CarinaUIEvent inCarinaUIEvent) {
                Logger LOGGER = Logger.getLogger(this.class)
                final ICarinaWidget ufvUnitWidget = getFormWidget(UnitField.UFV_FLEX_STRING02)
                unitIds = new ArrayList<String>();
                if (ufvUnitWidget != null) {
                    String unit = ufvUnitWidget.getValue()
                    LOGGER.warn("unit " + unit)
                    if (unit) {
                        unitIds.addAll(unit.trim().split("[ ,]+"))
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
        command.setAttribute("unitIds", unitIds)
        command.setSource(selectedKeys);
        command.setDestination(DestinationEnum.DIALOG);

        Map args = new HashMap();
        args.put("entityname", "UnitFacilityVisit");
        args.put("keys", getPrimaryKeys());
        args.put("unitIds", unitIds)
        command.execute(args);
    }
}
