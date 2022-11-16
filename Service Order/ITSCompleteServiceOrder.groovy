/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.framework.metafields.MetafieldIdFactory
import org.apache.log4j.Level
import org.apache.log4j.Logger
import com.navis.argo.ArgoBizMetafield
import com.navis.argo.business.atoms.ServiceOrderStatusEnum
import com.navis.argo.business.atoms.ServiceOrderUnitStatusEnum
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.external.framework.ui.AbstractTableViewCommand
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.BizRequest
import com.navis.framework.portal.BizResponse
import com.navis.framework.portal.CrudDelegate
import com.navis.framework.portal.UserContext
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.framework.presentation.ui.event.listener.AbstractCarinaOptionCommand
import com.navis.framework.presentation.ui.message.ButtonType
import com.navis.framework.presentation.ui.message.ButtonTypes
import com.navis.framework.presentation.ui.message.MessageDialog
import com.navis.framework.presentation.ui.message.MessageType
import com.navis.framework.presentation.ui.message.OptionDialog
import com.navis.framework.ulc.server.application.view.ViewHelper
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageCollectorFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.orders.OrdersField
import com.navis.orders.business.serviceorders.ItemServiceType
import com.navis.orders.business.serviceorders.ItemServiceTypeUnit
import com.navis.orders.business.serviceorders.ServiceOrder
import com.navis.orders.business.serviceorders.ServiceOrderItem


/*
 *
 *  @Author <ahref="mailto:mharikumar@weservetech.com"  >  Harikumar M</a>,
 *  Date : 17/Oct/2022
 *  Requirements :This groovy is used to complete the service order which is in New and Inprogress status.
 *  @Inclusion Location : Incorporated as a code extension of the type TABLE_VIEW_COMMAND. Copy -->Paste this code(ITSCompleteServiceOrder.groovy)
 */

class ITSCompleteServiceOrder extends AbstractTableViewCommand {
    private Logger LOGGER = Logger.getLogger(ITSCompleteServiceOrder.class);

    void execute(EntityId entityId, List<Serializable> gkeys, Map<String, Object> params) {
        //LOGGER.setLevel(Level.DEBUG)

        if (entityId == null || gkeys == null) {
            return;
        }


        OptionDialog.showMessage("Do you want to complete the service order?", "Complete Serice Order", ButtonTypes.YES_NO, MessageType.QUESTION_MESSAGE, new AbstractCarinaOptionCommand() {
            @Override
            protected void safeExecute(ButtonType inOption) {
                if (ButtonType.YES.equals(inOption)) {
                    ITSCompleteServiceOrder.this.processServiceOrder(entityId, gkeys)
                }
            }
        });
    }


    private void processServiceOrder(EntityId entityId, List<Serializable> gkeys) {


        Serializable srvoGkey = null;

        MessageCollector mc = MessageCollectorFactory.createMessageCollector();
        if (gkeys != null) {
            Iterator<Serializable> gkeyIterator = gkeys.iterator();
            while (gkeyIterator.hasNext()) {
                srvoGkey = gkeyIterator.next();
                String srvOrderNbr = (String) ViewHelper.getEntityFieldValue(entityId.getEntityName(), srvoGkey, OrdersField.SRVO_NBR)
                Serializable billParty = (String) ViewHelper.getEntityFieldValue(entityId.getEntityName(), srvoGkey, OrdersField.SRVO_BILLING_PARTY)
                logMsg("srvOrderNbr to confirm :" + srvOrderNbr + "billParty ::" + billParty)
                completeServiceOrder(FrameworkPresentationUtils.getUserContext(), srvOrderNbr, mc, entityId, billParty)
            }
            MessageDialog.showMessageDialog(mc, null, PropertyKeyFactory.valueOf("COMPLETE_TITLE"));
        }
    }


    private void completeServiceOrder(UserContext userContext, String srvOrderNbr, MessageCollector mc, EntityId entityId, Serializable billParty) {
        if (srvOrderNbr != null) {
            def baseUtil = getLibrary("BaseGroovyUtil")

            PersistenceTemplate pt = new PersistenceTemplate(userContext);
            pt.invoke(new CarinaPersistenceCallback() {
                protected void doInTransaction() {
                    LOGGER.debug("Entering");
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
                                if (serviceOrderItems != null && !serviceOrderItems.isEmpty()){
                                    for (Object itemSet : serviceOrderItems) {
                                        ServiceOrderItem serviceOrderItem = (ServiceOrderItem) itemSet;
                                        if (serviceOrderItem != null) {
                                            Set<ItemServiceType> itemServiceTypes = serviceOrderItem?.getItemServiceTypes();
                                            if (itemServiceTypes != null && !itemServiceTypes.isEmpty()){
                                                for (Object serviceTypeSet : itemServiceTypes) {
                                                    ItemServiceType itemServiceType = (ItemServiceType) serviceTypeSet;
                                                    Set<ItemServiceTypeUnit> istUnits = itemServiceType?.getItemServiceTypeUnits();
                                                    if (istUnits != null && !istUnits.isEmpty()){
                                                        for (Object istUnitSet : istUnits) {
                                                            ItemServiceTypeUnit istUnit = (ItemServiceTypeUnit) istUnitSet;
                                                            String istuEventId = null;

                                                            Double quantity = 0;
                                                            quantity = (Double) istUnit.getFieldValue(MetafieldIdFactory.valueOf("customFlexFields.itmsrvtypunitCustomDFFQuantity"))
                                                            if (null != istUnit?.getItmsrvtypunitEvent()) {
                                                                LOGGER.debug("istUnit EvnttypeId" + istUnit?.getItmsrvtypunitEvent()?.getEventTypeId());
                                                                istuEventId = istUnit.getItmsrvtypunitEvent().getEventTypeId();

                                                            }
                                                            if (null != itemServiceType?.getItmsrvtypEventType()) {
                                                                LOGGER.debug("serviceTypeSet EvnttypeId" + itemServiceType?.getItmsrvtypEventType()?.getEvnttypeId());
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
                                LOGGER.debug("Exception :" + e.getMessage());
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

    private void logMsg(String inMsg) {
        LOGGER.debug(inMsg);
    }
}
