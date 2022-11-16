import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.BizRequest
import com.navis.framework.portal.BizResponse
import com.navis.framework.portal.CrudDelegate
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.framework.presentation.ui.message.MessageDialog
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.internationalization.UserMessage
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageCollectorFactory
import com.navis.inventory.business.api.UnitField
import com.navis.orders.ServiceOrderField
import com.navis.orders.business.serviceorders.ItemServiceTypeUnit
import org.apache.log4j.Level
import org.apache.log4j.Logger

/*
 * Version #: #BuildNumber#
 * Author: Manimaran - WeServe
 * Work B 7-6 Service Orders
 * Date: 26-OCT-22
 * Description: To add the quantity value when adding the Equipment in ItemServiceTypeUnit.
 */

class ITESAddEquipmentToServiceOrderSubmissionCommand extends AbstractFormSubmissionCommand {

    @Override
    void submit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)

        String lineServiceItemKey = inOutFieldChanges.findFieldChange(ServiceOrderField.ITMSRVTYPUNIT_ITEM_SERVICE_TYPE)?.getNewValue();
        String unitEquipment = inOutFieldChanges.findFieldChange(MetafieldIdFactory.valueOf("unitEquipment.eqIdFull"))?.getNewValue();

        if(lineServiceItemKey == null || unitEquipment == null){
            return
        }

        Double quantity = 0.0;
        if(inOutFieldChanges.hasFieldChange(CUSTOM_DFF_QUANTITY)){
            quantity = (Double) inOutFieldChanges.findFieldChange(CUSTOM_DFF_QUANTITY).getNewValue();
        }

        MessageCollector reqMessage = MessageCollectorFactory.createMessageCollector();
        MessageCollector appMessage = MessageCollectorFactory.createMessageCollector();

        reqMessage = createUnitForServiceOrder(unitEquipment, lineServiceItemKey)
        Iterator itr = reqMessage?.getMessages()?.iterator();
        while (itr.hasNext()) {
            UserMessage um = (UserMessage) itr.next();
            appMessage.appendMessage(um.getSeverity(), um.getMessageKey(), null, um.getParms());
        }

        if (appMessage.getMessageCount() != 0) {
            MessageDialog.showMessageDialog(appMessage, null, PropertyKeyFactory.valueOf("SO.CREATE_EQUIPMENT_ERROR_TITLE"));
        }

        if (quantity > 1) {
            PersistenceTemplate pt = new PersistenceTemplate(getUserContext())
            pt.invoke(new CarinaPersistenceCallback() {
                protected void doInTransaction() {
                    DomainQuery dq = QueryUtils.createDomainQuery("ItemServiceTypeUnit")
                            .addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("itmsrvtypunitUnit.unitId"), unitEquipment));
                    ItemServiceTypeUnit itemServiceTypeUnit = (ItemServiceTypeUnit) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
                    if (itemServiceTypeUnit != null) {
                        itemServiceTypeUnit.setFieldValue(CUSTOM_DFF_QUANTITY, quantity)
                    }
                }
            })
        }
    }

    private MessageCollector createUnitForServiceOrder(String equipId, String lineServiceItemKey) {
        BizResponse response = null;
        BizRequest req = new BizRequest(FrameworkPresentationUtils.getUserContext());
        req.setParameter(UnitField.UNIT_PRIMARY_EQ_ID_FULL.toString(), (Serializable) equipId);
        req.setParameter(ServiceOrderField.ITMSRVTYP_GKEY.toString(), (Serializable) lineServiceItemKey)
        response = CrudDelegate.executeBizRequest(req, "ordCreateServiceTypeUnit");
        return response.getMessageCollector();
    }

    private static final MetafieldId CUSTOM_DFF_QUANTITY = MetafieldIdFactory.valueOf("customFlexFields.itmsrvtypunitCustomDFFQuantity");
    private static Logger LOGGER = Logger.getLogger(this.class);
}
