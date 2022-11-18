/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/

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
 * @Author: mailto:kmanimaran@weservetech.com, Manimaran; Date: 26/10/2022
 *
 * Requirements: To add the quantity, while adding the Equipment in ItemServiceTypeUnit.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITESAddEquipmentToServiceOrderSubmissionCommand
 *     Code Extension Type: FORM_SUBMISSION_INTERCEPTION
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Set up in the database backed variform --> ORD062_OVERRIDE --> adding formSubmissionCodeExtension name="ITESAddEquipmentToServiceOrderSubmissionCommand.
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 *
 */

class ITESAddEquipmentToServiceOrderSubmissionCommand extends AbstractFormSubmissionCommand {

    @Override
    void submit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        String lineServiceItemKey = inOutFieldChanges.findFieldChange(ServiceOrderField.ITMSRVTYPUNIT_ITEM_SERVICE_TYPE).getNewValue();
        String unitEquipment = inOutFieldChanges.findFieldChange(MetafieldIdFactory.valueOf("unitEquipment.eqIdFull")).getNewValue();
        String itemServiceTypeGkey = inOutFieldChanges.findFieldChange(MetafieldIdFactory.valueOf("itmsrvtypunitItemServiceType")).getNewValue();

        if(lineServiceItemKey == null || unitEquipment == null){
            return
        }

        Double quantity = 0.0;
        if (inOutFieldChanges.hasFieldChange(CUSTOM_DFF_QUANTITY)) {
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
                            .addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("itmsrvtypunitUnit.unitId"), unitEquipment))
                            .addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf(" itmsrvtypunitItemServiceType.itmsrvtypGkey"), itemServiceTypeGkey))
                    ItemServiceTypeUnit itemServiceTypeUnit = (ItemServiceTypeUnit) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
                    if (itemServiceTypeUnit != null) {
                        itemServiceTypeUnit.setFieldValue(CUSTOM_DFF_QUANTITY, quantity)
                    }
                }
            })
        }
    }

    private MessageCollector createUnitForServiceOrder(String equipId, String lineServiceItemKey) {
        BizResponse response = null
        BizRequest req = new BizRequest(FrameworkPresentationUtils.getUserContext());
        req.setParameter(UnitField.UNIT_PRIMARY_EQ_ID_FULL.toString(), (Serializable) equipId);
        req.setParameter(ServiceOrderField.ITMSRVTYP_GKEY.toString(), (Serializable) lineServiceItemKey)
        response = CrudDelegate.executeBizRequest(req, "ordCreateServiceTypeUnit");
        return response.getMessageCollector()
    }

    private static final MetafieldId CUSTOM_DFF_QUANTITY = MetafieldIdFactory.valueOf("customFlexFields.itmsrvtypunitCustomDFFQuantity");
    private static Logger LOGGER = Logger.getLogger(ITESAddEquipmentToServiceOrderSubmissionCommand.class);
}

