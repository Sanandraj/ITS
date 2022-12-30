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
import com.navis.framework.ulc.server.application.view.form.widget.LookupFormWidget
import com.navis.framework.util.ValueObject
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageCollectorFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.InventoryBizMetafield
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.presentation.command.DefaultShowUnitDigitSubmitFormCommand
import com.navis.inventory.presentation.controller.ShowCreateServiceOrderFormController
import com.navis.orders.OrdersField
import com.navis.orders.business.serviceorders.ItemServiceType
import com.navis.orders.business.serviceorders.ItemServiceTypeUnit
import com.navis.orders.business.serviceorders.ServiceOrder
import com.navis.orders.business.serviceorders.ServiceOrderItem
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger

/**
 * @Author: uaarthi@weservetech.com; Date: 21-12-2022
 *
 *  Requirements:
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name:
 *     Code Extension Type:  
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *
 *
 */
class customBeanITSShowCreateServiceOrderFormController extends ShowCreateServiceOrderFormController implements EBean {
    private static final Logger logger = Logger.getLogger(this.class)
    private ValueObject currentValues;
    private Serializable _ufvGkeys;


    public Serializable processPrimaryKeyForSubmitRequest() {
        return this._ufvGkeys;
    }


    @Override
    protected void configure() {
        logger.warn("get current values configure " + getCurrentValues())
        PersistenceTemplate pt = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext())
        Serializable line = null
        String lineId = null
        boolean isSameOp = Boolean.FALSE
        pt.invoke(new CarinaPersistenceCallback() {
            protected void doInTransaction() {
                for (Serializable gkey : getPrimaryKeys()) {
                    UnitFacilityVisit ufv = UnitFacilityVisit.hydrate(gkey)
                    if (line == null || lineId == null) {
                        line = ufv.getUfvUnit().getUnitLineOperator().getPrimaryKey()
                        lineId = ufv.getUfvUnit().getUnitLineOperator().getBzuId()
                        isSameOp = true
                    } else if (!ufv.getUfvUnit().getUnitLineOperator().getPrimaryKey().equals(line)) {
                        isSameOp = false
                    }
                }
            }
        })
        logger.warn("line " + line)
        logger.warn("lineId " + lineId)
        logger.warn("issameOp " + isSameOp)
        logger.warn("Configure " + _ufvGkeys)

        if (isSameOp) {
            // this.currentValues.setFieldValue(InventoryCompoundField.UNIT_LINE_OP_GKEY, line)
            LookupFormWidget billingPartyWidget = (LookupFormWidget) getFormWidget(OrdersField.SRVO_BILLING_PARTY)
            if (billingPartyWidget != null && lineId != null) {

            }
        }
      /*  CheckBoxFormWidget autoCompleteSO = (CheckBoxFormWidget) getFormWidget(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_AutoCompleteSO"));
        autoCompleteSO.setValue(true)
        autoCompleteSO.internalSetValue(true)*/
        super.configure()
    }

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
                logger.warn(" getCreateUpdateDataCommand _ufvGkeys " + _ufvGkeys)
                inBizRequest.setParameter("UfvGkeys", _ufvGkeys);
            }

            protected void handleResponse(BizResponse inResponse) {
                if (inResponse.hasRequestFailed()) {
                    this.setErrorMsg(this.extractErrorMessage(inResponse));
                    this.focusOnField(this.extractFailedField(inResponse));
                } else {
                    logger.warn(" doAfterSubmit inResponse " + inResponse)

                    this.setSuccessMsg(FrameworkPresentationUtils.getTranslation(FrameworkUiPropertyKeys.LABEL__CREATE_SUCCESSFUL));
                    ICarinaWidget srvoNbrWdget = getFormWidget(InventoryBizMetafield.SRVO_NBR);
                    String srvoNbr = srvoNbrWdget.getValue()
                    //       assignField(metafield, (Object) null);

                    /*    assignField(metafield, (Object)null);
                        metafield = InventoryBizMetafield.SRVO_BILLING_PARTY;
                        assignField(metafield, (Object)null);
                        metafield = InventoryBizMetafield.SRVO_LINE;
                        assignField(metafield, (Object)null);
                        metafield = InventoryBizMetafield.UNIT_EVNT_TYPE_ARRAY;
                        assignField(metafield, (Object)null);
                        this.focusOnField(InventoryBizMetafield.SRVO_NBR);
                        CarinaButton executeButton = this.getFormController().getButton(FrameworkUserActions.EXECUTE);
                        if (executeButton != null) {
                            executeButton.setEnabled(true);
                        }*/


                }

            }

            @Override
            void doAfterSubmit(BizResponse inOutBizResponse, String inEntityName, Serializable inEntityGkey, FieldChanges inOutFieldChanges) {
                logger.warn(" doAfterSubmit _ufvGkeys " + _ufvGkeys)
                logger.warn(" doAfterSubmit inEntityGkey " + inEntityGkey)
                logger.warn(" doAfterSubmit inOutFieldChanges " + inOutFieldChanges)
                logger.warn(" doAfterSubmit inOutBizResponse " + inOutBizResponse)


                ICarinaWidget srvoNbrWdget = getFormWidget(InventoryBizMetafield.SRVO_NBR);
                String srvoNbr = srvoNbrWdget.getValue()
                logger.warn(" doAfterSubmit srvoNbr " + srvoNbr)


                QueryResult result = inOutBizResponse.getQueryResult()
                if (StringUtils.isEmpty(srvoNbr) && result != null) {
                    srvoNbr = result.getValue(0, InventoryBizMetafield.SRVO_NBR);
                    logger.warn(" doAfterSubmit srvoNbr 2 " + srvoNbr)

                    if (srvoNbr == null) {
                        srvoNbr = result.getValue(3, InventoryBizMetafield.SRVO_NBR);
                    }
                }

                ICarinaWidget srvoNbrBillParty = getFormWidget(InventoryBizMetafield.SRVO_BILLING_PARTY);

                Serializable billingParty = (Serializable) srvoNbrBillParty.getValue()
                ICarinaWidget autoCompleteSO = getFormWidget(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_AutoCompleteSO"));
                boolean isAutoComplete = autoCompleteSO.getValue()
                logger.warn(" doAfterSubmit isAutoComplete " + isAutoComplete)

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

                    logger.debug("Entering");
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
                                                                logger.debug("istUnit EvnttypeId" + istUnit?.getItmsrvtypunitEvent()?.getEventTypeId());
                                                                istuEventId = istUnit.getItmsrvtypunitEvent().getEventTypeId();

                                                            }
                                                            if (null != itemServiceType?.getItmsrvtypEventType()) {
                                                                logger.debug("serviceTypeSet EvnttypeId" + itemServiceType?.getItmsrvtypEventType()?.getEvnttypeId());
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

}
