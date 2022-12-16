
import com.navis.edi.EdiEntity
import com.navis.edi.EdiField
import com.navis.edi.business.EdiFacade
import com.navis.edi.business.entity.EdiTransaction
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.*
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.business.units.Unit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger

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


//        TrainVisitDetails inTrainVisitDtls = (TrainVisitDetails) inGroovyEvent.getEntity();

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
//            EdiDelegate.postTransaction(UserContext.getThreadUserContext(), gKey)
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
            dq.addDqOrdering(Ordering.desc(EdiField.EDITRAN_BATCH))
            List<EdiTransaction> tranList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

            if (tranList.size() > 0) {
                return tranList.get(0)
            }
        }
        return null;
    }
}
