/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/


import com.navis.argo.ArgoExtractField
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.ServiceOrderStatusEnum
import com.navis.argo.business.atoms.ServiceOrderUnitStatusEnum
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.framework.presentation.ui.event.listener.AbstractCarinaOptionCommand
import com.navis.framework.presentation.ui.message.*
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageCollectorFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.framework.web.FrameworkFlexGuiMetafield
import com.navis.inventory.business.units.Unit
import com.navis.orders.OrdersField
import com.navis.orders.business.serviceorders.*
import com.navis.services.business.event.Event
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 *
 * @Author: mailto:mharikumar@weservetech.com, Harikumar M; Date: 17/10/2022
 *
 *  Requirements: This groovy allow to customize the cancel service order form, In order to cancel the partial or fully completed service.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSCancelServiceOrder
 *     Code Extension Type: FORM_SUBMISSION_INTERCEPTION
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup in the database backed variform --> OVERRIDE_ORD66 --> adding formSubmissionCodeExtension name="ITSCancelServiceOrder.
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 *
 */

class ITSCancelServiceOrder extends AbstractFormSubmissionCommand {

    private static Logger LOGGER = Logger.getLogger(ITSCancelServiceOrder.class);

    @Override
    public void submit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys,
                       EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        //LOGGER.setLevel(Level.DEBUG)
        OptionDialog.showMessage("Do you want to cancel the service order?", "Cancel Serice Order", ButtonTypes.YES_NO, MessageType.QUESTION_MESSAGE, new AbstractCarinaOptionCommand() {
            @Override
            protected void safeExecute(ButtonType inOption) {
                if (ButtonType.YES.equals(inOption)) {
                    String cancelNotes = inOutFieldChanges.findFieldChange(FrameworkFlexGuiMetafield.FLEX_FIELD_STRING1)?.getNewValue();

                    Serializable inSrvoGkey = null;
                    boolean cancelStatus = false;

                    MessageCollector mc = MessageCollectorFactory.createMessageCollector();

                    if (!inGkeys.isEmpty()) {
                        Iterator<Serializable> gkeyIterator = inGkeys?.iterator();
                        while (gkeyIterator.hasNext()) {
                            inSrvoGkey = gkeyIterator.next();
                            cancelServiceOrder(FrameworkPresentationUtils.getUserContext(), inSrvoGkey, cancelNotes, mc)
                        }
                        MessageDialog.showMessageDialog(mc, null, PropertyKeyFactory.valueOf("CANCEL_TITLE"));
                    }
                }
            }
        });

    }


    private void cancelServiceOrder(UserContext userContext, Serializable inSrvoGkey, String cancelNotes, MessageCollector mc) {

        PersistenceTemplate pt = new PersistenceTemplate(userContext);
        pt.invoke(new CarinaPersistenceCallback() {
            protected void doInTransaction() {
                LOGGER.setLevel(Level.DEBUG)

                if (inSrvoGkey != null) {
                    DomainQuery dq = QueryUtils.createDomainQuery("AbstractServiceOrder").addDqPredicate(PredicateFactory.eq(OrdersField.SRVO_GKEY, inSrvoGkey));


                    List<AbstractServiceOrder> serviceOrderList = this.getHibernateApi().findEntitiesByDomainQuery(dq);
                    if (serviceOrderList != null && !serviceOrderList.isEmpty()) {
                        for (Object list : serviceOrderList) {
                            AbstractServiceOrder serviceOrder = (AbstractServiceOrder) list;
                            if (serviceOrder != null){
                                if (serviceOrder.getSrvoStatus().equals(ServiceOrderStatusEnum.CANCELLED)) {
                                    mc.appendMessage(MessageLevel.WARNING, PropertyKeyFactory.valueOf("SERVICE_ORDER_ALREADY_CANCELED"), serviceOrder?.getSrvoNbr(), serviceOrder?.getSrvoNbr())
                                    return
                                }

                                boolean finalized = serviceOrder.getFieldValue(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_InvoiceFinalised"))
                                String invoiceNbr = serviceOrder.getFieldValue(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_Invoiced"))
                                if(finalized || (StringUtils.isNotEmpty(invoiceNbr)) ){
                                    mc.appendMessage(MessageLevel.WARNING, PropertyKeyFactory.valueOf("INVOICE_EXISTS_FOR_THE_SERVICE_ORDER"), serviceOrder?.getSrvoNbr(), serviceOrder?.getSrvoNbr())
                                    return
                                }

                                if (!serviceOrder.getSrvoStatus().equals(ServiceOrderStatusEnum.CANCELLED)) {
                                    Date currentDate = ArgoUtils.timeNow();
                                    serviceOrder.setSrvoStatus(ServiceOrderStatusEnum.CANCELLED);
                                    serviceOrder.setSrvoCancelDate(currentDate);
                                    serviceOrder.setSrvoCancelNotes(cancelNotes);

                                    updateUnitHistory(serviceOrder);

                                    Object baseUtil = getLibrary("BaseGroovyUtil")
                                    baseUtil.refreshEntity(serviceOrder);
                                    mc.appendMessage(MessageLevel.INFO, PropertyKeyFactory.valueOf("SERVICE_CANCELED_SUCCESSFULLY"), serviceOrder?.getSrvoNbr(), serviceOrder?.getSrvoNbr())
                                    LOGGER.debug("Values :: Cancelled Service Order Nbr:" + serviceOrder?.getSrvoNbr() + " status :" + serviceOrder?.getSrvoStatus());
                                }
                            }

                        }
                    }

                }
            }
        });
    }

    private void updateUnitHistory(AbstractServiceOrder asOrder) {
        LOGGER.debug("updateUnitHistory - START")
        try {
            String srvOrderNbr = asOrder.getSrvoNbr();
            ServiceOrder serviceOrder = ServiceOrder.findServiceOrderByNbr(srvOrderNbr);
            if(serviceOrder != null){
                Set<ServiceOrderItem> serviceOrderItems = serviceOrder.getSrvoItems();
                if (serviceOrderItems != null && !serviceOrderItems.isEmpty()) {
                    for (ServiceOrderItem serviceOrderItem : serviceOrderItems) {
                        Set<ItemServiceType> serviceTypes = serviceOrderItem?.getItemServiceTypes();
                        if (serviceTypes != null && !serviceTypes.isEmpty()) {
                            for (ItemServiceType serviceType : serviceTypes) {
                                Iterator iterator = serviceType?.getItemServiceTypeUnits()?.iterator()
                                if (iterator != null) {
                                    while (iterator.hasNext()) {
                                        ItemServiceTypeUnit itemServiceTypeUnit = (ItemServiceTypeUnit) iterator.next();
                                        Event unitEvent = itemServiceTypeUnit?.getItmsrvtypunitEvent();
                                        String unitEventId = unitEvent?.getEventTypeId();
                                        Unit unit = itemServiceTypeUnit?.getItmsrvtypunitUnit();
                                        itemServiceTypeUnit.setItmsrvtypunitStatus(ServiceOrderUnitStatusEnum.CACNCELLED);

                                        ServicesManager servicesManager = (ServicesManager) Roastery.getBean("servicesManager");
                                        List<Event> events = servicesManager.getEventHistory(unit);

                                        if (events != null) {
                                            for (Event event : events) {
                                                if (unitEventId.equals(event?.getEventTypeId())) {
                                                    if (event?.isEventTypeBillable()) {
                                                        event.setEventTypeIsBillable(false);
                                                    }
                                                    event.setEvntNote("Event Cancelled, as service order cancels this unit");

                                                    List<ChargeableUnitEvent> cueList = findChargeableUnitEventByServiceOrderNbr(srvOrderNbr);
                                                    if(cueList != null && !cueList.isEmpty()){
                                                        for (cue in cueList) {
                                                            cue.setBexuStatus("CANCELLED")
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (!unit.getUnitLineOperator().equals(serviceOrder?.getSrvoBillingParty()) && serviceOrder?.getSrvoBillingParty() != null && BizRoleEnum.LINEOP.equals(serviceOrder.getSrvoBillingParty()?.getBzuRole())) {
                                            iterator.remove()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Exception : " + e.getMessage());
        }

        LOGGER.debug("updateUnitHistory - END")
    }


    private List<ChargeableUnitEvent> findChargeableUnitEventByServiceOrderNbr(String inSoId) {
        DomainQuery query = QueryUtils.createDomainQuery("ChargeableUnitEvent");
        query.addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_SERVICE_ORDER, inSoId));
        return HibernateApi.getInstance().findEntitiesByDomainQuery(query);
    }

}