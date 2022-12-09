/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.business.api.ArgoUtils
import com.navis.external.framework.beans.EBean
import com.navis.framework.ulc.server.application.controller.listener.AbstractCarinaFormValueListener
import com.navis.framework.ulc.server.application.view.form.widget.ComboBoxFormWidget
import com.navis.framework.ulc.server.application.view.form.widget.DateFormWidget
import com.navis.framework.ulc.server.application.view.form.widget.event.FormValueEvent
import com.navis.road.RoadApptsField
import com.navis.road.presentation.controller.GateAppointmentFormController

/**
 * @Author: Kishore Kumar S <a href= skishore@weservetech.com / >, 04/11/2022
 *
 *  Requirements: ITS - 3-1 Container Exemption List - Setting Exemption List value in Gate Appt as true while generating appointment through exemption list form and
 *                allows generation only with present date value.
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: customBeanITSApptGenerateFormController
 *     Code Extension Type: BEAN_PROTOTYPE
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */

class customBeanITSApptGenerateFormController extends GateAppointmentFormController implements EBean {
    @Override
    void configure() {
       super.configure()

        ComboBoxFormWidget comboBoxFormWidget = getFormWidget(RoadApptsField.GAPPT_TRAN_TYPE) as ComboBoxFormWidget
        comboBoxFormWidget.addFormValueListener(new AbstractCarinaFormValueListener() {
            @Override
            protected void safeValueChanged(FormValueEvent formValueEvent) {
                DateFormWidget dateFormWidget = (DateFormWidget) getFormWidget(RoadApptsField.GAPPT_REQUESTED_DATE)
                dateFormWidget.internalSetValue(ArgoUtils.timeNow())
                dateFormWidget.setEnabled(false)
            }
        })
    }
}
