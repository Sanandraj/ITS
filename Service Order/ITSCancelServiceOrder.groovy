import com.navis.argo.ArgoExtractField
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.ServiceOrderStatusEnum
import com.navis.argo.business.atoms.ServiceOrderUnitStatusEnum
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.business.Roastery
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
import com.navis.framework.presentation.ui.message.ButtonType
import com.navis.framework.presentation.ui.message.ButtonTypes
import com.navis.framework.presentation.ui.message.MessageDialog
import com.navis.framework.presentation.ui.message.MessageType
import com.navis.framework.presentation.ui.message.OptionDialog
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageCollectorFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.framework.web.FrameworkFlexGuiMetafield
import com.navis.inventory.business.units.Unit
import com.navis.orders.OrdersField
import com.navis.orders.business.serviceorders.*
import com.navis.services.business.event.Event
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * This groovy allow to customize the cancel service order form.
 * In order to cancel the partial or fully completed service.
 *
 */

class ITSCancelServiceOrder extends AbstractFormSubmissionCommand {

    private static Logger LOGGER = Logger.getLogger(ITSCancelServiceOrder.class);

    @Override
    public void submit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys,
                       EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        OptionDialog.showMessage("Do you want to cancel the service order?", "Cancel Serice Order", ButtonTypes.YES_NO, MessageType.QUESTION_MESSAGE, new AbstractCarinaOptionCommand() {
            @Override
            protected void safeExecute(ButtonType inOption) {
                if (ButtonType.YES.equals(inOption)) {
                    String cancelNotes = inOutFieldChanges.findFieldChange(FrameworkFlexGuiMetafield.FLEX_FIELD_STRING1).getNewValue();

                    Serializable inSrvoGkey = null;
                    boolean cancelStatus = false;

                    MessageCollector mc = MessageCollectorFactory.createMessageCollector();

                    if (!inGkeys.isEmpty()) {
                        Iterator<Serializable> gkeyIterator = inGkeys.iterator();
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
                    for (Object list : serviceOrderList) {
                        AbstractServiceOrder serviceOrder = (AbstractServiceOrder) list;
                        if ((serviceOrder != null) && (serviceOrder.getSrvoStatus().equals(ServiceOrderStatusEnum.CANCELLED))) {
                            mc.appendMessage(MessageLevel.WARNING, PropertyKeyFactory.valueOf("SERVICE_ORDER_ALREADY_CANCELED"), serviceOrder.getSrvoNbr(), serviceOrder.getSrvoNbr())
                            return
                        }

                        if ((serviceOrder != null) && (!serviceOrder.getSrvoStatus().equals(ServiceOrderStatusEnum.CANCELLED))) {
                            Date currentDate = new Date();
                            serviceOrder.setSrvoStatus(ServiceOrderStatusEnum.CANCELLED);
                            serviceOrder.setSrvoCancelDate(currentDate);
                            serviceOrder.setSrvoCancelNotes(cancelNotes);

                            updateUnitHistory(serviceOrder);

                            def baseUtil = getLibrary("BaseGroovyUtil")
                            baseUtil.refreshEntity(serviceOrder);
                            mc.appendMessage(MessageLevel.INFO, PropertyKeyFactory.valueOf("SERVICE_CANCELED_SUCCESSFULLY"), serviceOrder.getSrvoNbr(), serviceOrder.getSrvoNbr())
                            LOGGER.debug("Values :: Cancelled Service Order Nbr:" + serviceOrder.getSrvoNbr() + " status :" + serviceOrder.getSrvoStatus());
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

            LOGGER.debug("serviceOrder gkey : " + serviceOrder.getSrvoGkey());

            Set<ServiceOrderItem> serviceOrderItems = serviceOrder.getSrvoItems();
            for (ServiceOrderItem serviceOrderItem : serviceOrderItems) {
                LOGGER.debug(" - ServiceOrderItem gkey : " + serviceOrderItem.getSrvoiGkey());

                Set<ItemServiceType> serviceTypes = serviceOrderItem.getItemServiceTypes();
                for (ItemServiceType serviceType : serviceTypes) {
                    Iterator iterator = serviceType.getItemServiceTypeUnits()?.iterator()
                    if (iterator != null) {
                        while (iterator.hasNext()) {
                            ItemServiceTypeUnit itemServiceTypeUnit = (ItemServiceTypeUnit) iterator.next();
                            Event unitEvent = itemServiceTypeUnit.getItmsrvtypunitEvent();
                            String unitEventId = unitEvent.getEventTypeId();
                            Unit unit = itemServiceTypeUnit.getItmsrvtypunitUnit();
                            itemServiceTypeUnit.setItmsrvtypunitStatus(ServiceOrderUnitStatusEnum.CACNCELLED);

                            ServicesManager servicesManager = (ServicesManager) Roastery.getBean("servicesManager");
                            List<Event> events = servicesManager.getEventHistory(unit);

                            for (Event event : events) {
                                LOGGER.debug("     --- event - " + event);
                                if (unitEventId.equals(event.getEventTypeId())) {
                                    if (event.isEventTypeBillable()) {
                                        event.setEventTypeIsBillable(false);
                                    }
                                    event.setEvntNote("Event Cancelled, as service order cancels this unit");

                                    List<ChargeableUnitEvent> cueList = findChargeableUnitEventByServiceOrderNbr(srvOrderNbr);
                                    for (cue in cueList) {
                                        LOGGER.debug("cue: " + cue.getBexuBatchId());
                                        //cue.setStatusCancelled();
                                        cue.setBexuStatus("CANCELLED")
                                    }
                                }
                            }
                            if (!unit.getUnitLineOperator().equals(serviceOrder.getSrvoBillingParty()) && serviceOrder.getSrvoBillingParty() != null && BizRoleEnum.LINEOP.equals(serviceOrder.getSrvoBillingParty().getBzuRole())) {
                                iterator.remove()
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
