import com.navis.argo.business.atoms.EdiMessageClassEnum
import com.navis.edi.EdiEntity
import com.navis.edi.EdiField
import com.navis.edi.business.EdiFacade
import com.navis.edi.business.entity.EdiTransaction
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.*
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.business.units.Unit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger
/*
 *
 * @Author <a href="mailto:uaarthi@weservetech.com">Madhavan M</a>,
 *
 * Requirements : Vermas EDI Post Recent transaction optimized to fetch and repost a batch.
 *@Inclusion Location   : Incorporated as a code extension of the type GENERAL_NOTICE_CODE_EXTENSION.
 *  Load Code Extension to N4:
         1. Go to Administration --> System -->  Code Extension
         2. Click Add (+)
         3. Enter the values as below:
             Code Extension Name:  ITSPostLatestVermasTransactionOnUnitActivateGenNotice
             Code Extension Type:  GENERAL_NOTICE_CODE_EXTENSION
            Groovy Code: Copy and paste the contents of groovy code.
         4. Click Save button
 *
 *@Set up General Notice for event type "UNIT_RECEIVE" and "UNIT_DERAMP" on Unit Entity then execute this code extension (ITSPostLatestVermasTransactionOnUnitActivateGenNotice).
 *  S.No    Modified Date     Modified By     Jira      Description
 *  01.     2023-02-01        madhavan m      IP-370    fetch the transaction Query changes
 */

class ITSPostLatestVermasTransactionOnUnitActivateGenNotice extends AbstractGeneralNoticeCodeExtension {

    private static final Logger LOGGER = Logger.getLogger(this.class);

    @Override
    void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("Inside ITSPostLatestVermasTransactionOnUnitActivateGenNotice : ")

        if (inGroovyEvent == null) {
            return;
        }

        Event event = inGroovyEvent.getEvent();
        Unit inUnit = (Unit) inGroovyEvent.getEntity()

        if (event == null || inUnit == null) {
            return;
        }
        EdiTransaction ediTransaction = findTransaction(inUnit.getUnitId())
        LOGGER.debug("Inside ITSPostLatestVermasTransactionOnUnitActivateGenNotice - ediTransaction: " + ediTransaction)
        Serializable[] gkeys = new Serializable[1]
        LOGGER.debug("Inside ITSPostLatestVermasTransactionOnUnitActivateGenNotice - gkeys: " + gkeys)
        gkeys[0] = ediTransaction != null ? ediTransaction.getEditranGkey() : null
        LOGGER.debug("Inside ITSPostLatestVermasTransactionOnUnitActivateGenNotice - gkeys - 2: " + gkeys)
        if (gkeys.size() > 0) {
            BizRequest request = new BizRequest(UserContext.getThreadUserContext());
            request.setParameter(EdiField.EDITRAN_GKEY as String, gkeys);
            LOGGER.debug("Inside ITSPostLatestVermasTransactionOnUnitActivateGenNotice - request: " + request)
            BizResponse response = new BizResponse()
            EdiFacade ediFacade = (EdiFacade) Roastery.getBean(EdiFacade.BEAN_ID)
            ediFacade.postEdiTransaction(request, response)
            LOGGER.debug("Inside ITSPostLatestVermasTransactionOnUnitActivateGenNotice - response: " + response)
        }


        LOGGER.debug("Inside ITSPostLatestVermasTransactionOnUnitActivateGenNotice code compelted  : ")

    }
    private static EdiTransaction findTransaction(String unitNbr) {
        if (unitNbr != null) {
            DomainQuery dq = QueryUtils.createDomainQuery(EdiEntity.EDI_TRANSACTION);
            dq.addDqPredicate(PredicateFactory.eq(EdiField.EDITRAN_PRIMARY_KEYWORD_VALUE, unitNbr))
            dq.addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("editranBatch.edibatchSession.edisessMessageClass"), EdiMessageClassEnum.VERMAS.getKey()))
            dq.addDqOrdering(Ordering.desc(EdiField.EDITRAN_BATCH))
            dq.setDqMaxResults(1)
            EdiTransaction transaction = (EdiTransaction) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
            return transaction
        }
        return null;
    }
}
