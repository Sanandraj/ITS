/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/


import com.navis.argo.business.atoms.ServiceOrderStatusEnum
import com.navis.argo.business.atoms.ServiceOrderUnitStatusEnum
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.external.framework.beans.EBean
import com.navis.external.framework.util.ExtensionUtils
import com.navis.framework.FrameworkUiPropertyKeys
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.BizRequest
import com.navis.framework.portal.BizResponse
import com.navis.framework.portal.FieldChanges
import com.navis.framework.portal.UserContext
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.framework.presentation.ui.ICarinaWidget
import com.navis.framework.presentation.ui.command.ISubmitFormCommand
import com.navis.framework.presentation.ui.message.ButtonTypes
import com.navis.framework.presentation.ui.message.OptionDialog
import com.navis.framework.query.common.api.QueryResult
import com.navis.framework.ulc.server.application.view.form.widget.CheckBoxFormWidget
import com.navis.framework.util.ValueObject
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageCollectorFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.InventoryBizMetafield
import com.navis.inventory.presentation.command.DefaultShowUnitDigitSubmitFormCommand
import com.navis.inventory.presentation.controller.ShowCreateServiceOrderFormController
import com.navis.orders.business.serviceorders.ItemServiceType
import com.navis.orders.business.serviceorders.ItemServiceTypeUnit
import com.navis.orders.business.serviceorders.ServiceOrder
import com.navis.orders.business.serviceorders.ServiceOrderItem
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger

/**
 * @Author: mailto:uaarthi@weservetech.com, Aarthi U; Date: 21/12/2022
 *
 *  Requirements: Override Create Service Order Form to handle Auto Completion of Service Orders
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: customBeanITSShowCreateServiceOrderFormController
 *     Code Extension Type: BEAN_PROTOTYPE
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */
class customBeanITSShowCreateServiceOrderFormController extends ShowCreateServiceOrderFormController implements EBean {
    private ValueObject currentValues;
    private Serializable _ufvGkeys;


    public Serializable processPrimaryKeyForSubmitRequest() {
        return this._ufvGkeys;
    }


    //TODO Configure method - To handle Multiple Billing Parties - Pending confirmation, if this requirement is valid

    @Override
    void setWidgetValue(ICarinaWidget inWidget, Object inValue) {
        super.setWidgetValue(inWidget, inValue)
        CheckBoxFormWidget autoCompleteSO = (CheckBoxFormWidget) getFormWidget(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_AutoCompleteSO"));

        autoCompleteSO.internalSetValue(true)
    }

    @Override
    ISubmitFormCommand getCreateUpdateDataCommand() {
        return new DefaultShowUnitDigitSubmitFormCommand(this, "invCreateServiceOrderForActiveUnits") {

            protected void augmentRequest(BizRequest inBizRequest) {
                List<Serializable> itemGkeys = (List) this.getFormController().getAttribute("source");
                _ufvGkeys = itemGkeys.toArray(new Serializable[itemGkeys.size()]);
                inBizRequest.setParameter("UfvGkeys", _ufvGkeys);
            }

            protected void handleResponse(BizResponse inResponse) {
                if (inResponse.hasRequestFailed()) {
                    this.setErrorMsg(this.extractErrorMessage(inResponse));
                    this.focusOnField(this.extractFailedField(inResponse));
                } else {
                    this.setSuccessMsg(FrameworkPresentationUtils.getTranslation(FrameworkUiPropertyKeys.LABEL__CREATE_SUCCESSFUL));
                }

            }

            @Override
            void doAfterSubmit(BizResponse inOutBizResponse, String inEntityName, Serializable inEntityGkey, FieldChanges inOutFieldChanges) {

                ICarinaWidget srvoNbrWdget = getFormWidget(InventoryBizMetafield.SRVO_NBR);
                String srvoNbr = srvoNbrWdget.getValue()


                QueryResult result = inOutBizResponse.getQueryResult()
                if (StringUtils.isEmpty(srvoNbr) && result != null) {
                    srvoNbr = result.getValue(0, InventoryBizMetafield.SRVO_NBR);

                    if (srvoNbr == null) {
                        srvoNbr = result.getValue(3, InventoryBizMetafield.SRVO_NBR);
                    }
                }

                ICarinaWidget srvoNbrBillParty = getFormWidget(InventoryBizMetafield.SRVO_BILLING_PARTY);

                Serializable billingParty = (Serializable) srvoNbrBillParty.getValue()
                ICarinaWidget autoCompleteSO = getFormWidget(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_AutoCompleteSO"));
                boolean isAutoComplete = autoCompleteSO.getValue()

                MessageCollector mc = MessageCollectorFactory.createMessageCollector();
                if (isAutoComplete) {
                    completeServiceOrder(FrameworkPresentationUtils.getUserContext(), srvoNbr, mc, null, billingParty)
                }
                PersistenceTemplate pt = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext());
                pt.invoke(new CarinaPersistenceCallback() {
                    protected void doInTransaction() {
                        ServiceOrder srvo = ServiceOrder.findServiceOrderByNbr(srvoNbr)
                        srvo.setFieldValue(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_AutoCompleteSO"), isAutoComplete)


                    }
                })


                getFormUiProcessor().hideWindow(this.getFormController());
                OptionDialog.showMessage("Service Order created successfully - ${srvoNbr}", "Service Order", ButtonTypes.OK, "informationMessage", null);


                super.doAfterSubmit(inOutBizResponse, inEntityName, inEntityGkey, inOutFieldChanges)
            }
        };
    }

    public ValueObject getCurrentValues() {
        return this.currentValues;
    }

    public void setCurrentValues(ValueObject inCurrentValues) {
        this.currentValues = inCurrentValues;
    }

    private void completeServiceOrder(UserContext userContext, String srvOrderNbr, MessageCollector mc, EntityId entityId, Serializable billParty) {
        if (srvOrderNbr != null) {

            PersistenceTemplate pt = new PersistenceTemplate(userContext);
            pt.invoke(new CarinaPersistenceCallback() {
                protected void doInTransaction() {
                    Object baseUtil = ExtensionUtils.getLibrary(FrameworkPresentationUtils.getUserContext(), "BaseGroovyUtil");

                    ServiceOrder inSrvOrder = ServiceOrder.findServiceOrderByNbr(srvOrderNbr);
                    if (inSrvOrder != null) {
                        if ((ServiceOrderStatusEnum.COMPLETED.equals(inSrvOrder.getSrvoStatus()) || ServiceOrderStatusEnum.CANCELLED.equals(inSrvOrder?.getSrvoStatus()))) {
                            mc.appendMessage(MessageLevel.WARNING, PropertyKeyFactory.valueOf("UNABLE_TO_PERFORM_COMPLETE"), null, inSrvOrder?.getSrvoNbr(), capitalize(inSrvOrder.getSrvoStatus().getName()))
                            return
                        }

                        if (billParty != null) {
                            billParty = (ScopedBizUnit) HibernateApi.getInstance().load(ScopedBizUnit.class, billParty);
                        }

                        if (!ServiceOrderStatusEnum.COMPLETED.equals(inSrvOrder.getSrvoStatus()) || !ServiceOrderStatusEnum.CANCELLED.equals(inSrvOrder.getSrvoStatus())) {
                            try {
                                Set<ServiceOrderItem> serviceOrderItems = inSrvOrder?.getSrvoItems();
                                if (serviceOrderItems != null && !serviceOrderItems.isEmpty()) {
                                    for (Object itemSet : serviceOrderItems) {
                                        ServiceOrderItem serviceOrderItem = (ServiceOrderItem) itemSet;
                                        if (serviceOrderItem != null) {
                                            Set<ItemServiceType> itemServiceTypes = serviceOrderItem?.getItemServiceTypes();
                                            if (itemServiceTypes != null && !itemServiceTypes.isEmpty()) {
                                                for (Object serviceTypeSet : itemServiceTypes) {
                                                    ItemServiceType itemServiceType = (ItemServiceType) serviceTypeSet;
                                                    Set<ItemServiceTypeUnit> istUnits = itemServiceType?.getItemServiceTypeUnits();
                                                    if (istUnits != null && !istUnits.isEmpty()) {
                                                        for (Object istUnitSet : istUnits) {
                                                            ItemServiceTypeUnit istUnit = (ItemServiceTypeUnit) istUnitSet;
                                                            String istuEventId = null;

                                                            Double quantity = 0;
                                                            quantity = (Double) istUnit.getFieldValue(MetafieldIdFactory.valueOf("customFlexFields.itmsrvtypunitCustomDFFQuantity"))
                                                            if (null != istUnit?.getItmsrvtypunitEvent()) {
                                                                istuEventId = istUnit.getItmsrvtypunitEvent().getEventTypeId();

                                                            }
                                                            if (null != itemServiceType?.getItmsrvtypEventType()) {
                                                                if (!itemServiceType.getItmsrvtypEventType().getEvnttypeId().equalsIgnoreCase(istuEventId)) {
                                                                    baseUtil.recordEventOnCompleteServiceOrder(itemServiceType.getItmsrvtypEventType().getEvnttypeId(), istUnit.getItmsrvtypunitUnit(), inSrvOrder, billParty, quantity);
                                                                    istUnit.setItmsrvtypunitStatus(ServiceOrderUnitStatusEnum.COMPLETED);
                                                                }
                                                            }
                                                        }
                                                    }


                                                }
                                            }


                                        }
                                    }
                                }

                                inSrvOrder.setSrvoStatus(ServiceOrderStatusEnum.COMPLETED);
                                baseUtil.refreshEntity(inSrvOrder);
                                mc.appendMessage(MessageLevel.INFO, PropertyKeyFactory.valueOf("SERVICE_ORDER_COMPLETED"), null, inSrvOrder.getSrvoNbr())

                            } catch (Exception e) {
                                logger.debug("Exception :" + e.getMessage());
                                mc.appendMessage(MessageLevel.WARNING, PropertyKeyFactory.valueOf("UNABLE_TO_PERFORM_COMPLETE"), null, inSrvOrder.getSrvoNbr(), "Trouble, Please contact system admin for complete stack trace")
                            }
                        }
                    }
                }
            });
        }
    }

    private String capitalize(String s) {
        if (s == null && s.length() == 0) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
    private static final Logger logger = Logger.getLogger(customBeanITSShowCreateServiceOrderFormController.class)


}
