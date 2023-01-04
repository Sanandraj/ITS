/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.ArgoExtractField
import com.navis.argo.business.atoms.GuaranteeOverrideTypeEnum
import com.navis.external.framework.beans.EBean
import com.navis.framework.presentation.ui.ICarinaWidget
import com.navis.framework.presentation.ui.event.CarinaFormValueEvent
import com.navis.framework.presentation.ui.event.listener.AbstractCarinaFormValueListener
import com.navis.inventory.presentation.controller.RecordWaiverFormController

/*
     *
     * @Author : Gopinath Kannappan, 29/Dec/2022
     *
     * Requirements : 7-9 Apply Waiver to Multiple Units -- This groovy is used to record the waiver for multiple units.
     *
     * @Inclusion Location	: Incorporated as a code extension of the type BEAN_PROTOTYPE.
     *
     *  Load Code Extension to N4:
            1. Go to Administration --> System -->  Code Extension
            2. Click Add (+)
            3. Enter the values as below:
                Code Extension Name:  customBeanITSRecordWaiverFormController
                Code Extension Type:  BEAN_PROTOTYPE
               Groovy Code: Copy and paste the contents of groovy code.
            4. Click Save button

     *  Set up in Guarantee form as : <controller ref="customBeanITSRecordWaiverFormController" />
     *
     *
 */


class customBeanITSRecordWaiverFormController extends RecordWaiverFormController implements EBean {


    @Override
    protected void configure() {
        super.configure()
        createWaiverTypeListener()
    }


    private void createWaiverTypeListener() {
        final ICarinaWidget waiverType = this.getFormWidget(ArgoExtractField.GNTE_OVERRIDE_VALUE_TYPE)
        final ICarinaWidget amountWidget = this.getFormWidget(ArgoExtractField.GNTE_GUARANTEE_AMOUNT)
        ICarinaWidget expirationDateWidget = this.getFormWidget(ArgoExtractField.GNTE_WAIVER_EXPIRATION_DATE)
        final ICarinaWidget startDay = this.getFormWidget(ArgoExtractField.GNTE_GUARANTEE_START_DAY)
        final ICarinaWidget endDay = this.getFormWidget(ArgoExtractField.GNTE_GUARANTEE_END_DAY)
        if (waiverType != null) {
            waiverType.addFormValueListener(new AbstractCarinaFormValueListener() {
                protected void safeValueChanged(CarinaFormValueEvent inEvent) {
                    amountWidget.setRequired(GuaranteeOverrideTypeEnum.FIXED_PRICE.equals(waiverType.getValue()))
                    startDay.setValue(GuaranteeOverrideTypeEnum.FIXED_PRICE.equals(waiverType.getValue()) ? new Date() : null)
                    endDay.setValue(GuaranteeOverrideTypeEnum.FIXED_PRICE.equals(waiverType.getValue()) ? new Date() : null)
                }
            })
        }

    }


}
