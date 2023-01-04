/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.external.framework.beans.EBean
import com.navis.framework.presentation.command.VariformUiCommand
import com.navis.framework.presentation.lovs.factory.ParentDependentLovKey
import com.navis.framework.presentation.ui.ICarinaLovWidget
import com.navis.inventory.InventoryBizMetafield
import com.navis.inventory.presentation.controller.RecordGuaranteeFormController
import org.apache.log4j.Level
import org.apache.log4j.Logger

/*
     *
     * @Author : Gopinath Kannappan, 29/Dec/2022
     *
     * Requirements : 7-9 Apply Waiver to Multiple Units -- This groovy is used to record the guarantee for multiple units.
     *
     * @Inclusion Location	: Incorporated as a code extension of the type BEAN_PROTOTYPE.
     *
     *  Load Code Extension to N4:
            1. Go to Administration --> System -->  Code Extension
            2. Click Add (+)
            3. Enter the values as below:
                Code Extension Name:  customBeanITSRecordGuaranteeFormController
                Code Extension Type:  BEAN_PROTOTYPE
               Groovy Code: Copy and paste the contents of groovy code.
            4. Click Save button

     *  Set up in Guarantee form as : <controller ref="customBeanITSRecordGuaranteeFormController" />
     *
     *
 */


class customBeanITSRecordGuaranteeFormController extends RecordGuaranteeFormController implements EBean {


    private static Logger LOGGER = Logger.getLogger(customBeanITSRecordGuaranteeFormController.class)

    public CustomBeanITSRecordGuaranteeFormController() {}

    boolean initForm() {
        return super.initForm()
    }

    @Override
    protected void configure() {
        LOGGER.setLevel(Level.DEBUG)
        super.configure()
        setLovKeyForExtractEventType()
    }


    protected void setLovKeyForExtractEventType() {
        LOGGER.debug("setLovKeyForExtractEventType")
        Serializable gkey = null
        Serializable[] primaryKeys = null
        ICarinaLovWidget eventTypeWidget = (ICarinaLovWidget) this.getFormWidget(InventoryBizMetafield.EXTRACT_EVENT_TYPE)
        LOGGER.debug("eventTypeWidget :" + eventTypeWidget)
        if (null != eventTypeWidget && eventTypeWidget instanceof ICarinaLovWidget) {
            if (null == this.getEntityGkey()) {
                Object parent = this.getAttribute("parent")
                if (parent instanceof VariformUiCommand) {
                    List<Serializable> gkeys = (List) ((VariformUiCommand) parent).getAttribute("source")
                    gkey = (Serializable) gkeys.get(0)
                }
            } else {
                primaryKeys = this.getPrimaryKeys()

            }

            ParentDependentLovKey parentDependentLovKey = new ParentDependentLovKey("argoExtractLov.ufvUnitEvent", primaryKeys)
            eventTypeWidget.setLovKey(parentDependentLovKey)

        }

    }
}
