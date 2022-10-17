import com.navis.argo.*
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.reference.LineOperator
import com.navis.edi.EdiEntity
import com.navis.edi.EdiField
import com.navis.edi.business.edimodel.EdiConsts
import com.navis.edi.business.entity.EdiFilter
import com.navis.edi.business.entity.EdiFilterEntry
import com.navis.edi.business.entity.EdiSession
import com.navis.edi.business.entity.EdiSessionFilter
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.rail.business.atoms.SpottingStatusEnum
import com.navis.rail.business.entity.Railcar
import com.navis.rail.business.entity.RailcarVisit
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject

/*
 *
 * @author <a href="mailto:sanandaraj@weservetech.com">Anandaraj S</a>, 04/AUG/2022
 *
 * Requirements : This groovy is used for Railconsist EDI - Groovy validation required.
 * Spot and IB train visit validation are checking
 *
 * @Inclusion Location	: Incorporated as a code extension of the type EDI_POST_INTERCEPTOR.
 *
 *  Load Code Extension to N4:
        1. Go to Administration --> System --> Code Extensions
        2. Click Add (+)
        3. Enter the values as below:
            Code Extension Name:  ITSRailConsistEdiPostInterceptor
            Code Extension Type:  EDI_POST_INTERCEPTOR
            Groovy Code: Copy and paste the contents of groovy code.
        4. Click Save button

 Attach code extension to EDI session:
        1. Go to Administration-->EDI-->EDI configuration
        2. Select the EDI session and right click on it
        3. Click on Edit
        4. Select the extension in "Post Code Extension" tab
        5. Click on save
 *
 * 10/14/2022 - Harikumar M  - To validate operator Id based on W4 segment when W3 segment value is not found in N4.
 */

class ITSRailConsistEdiPostInterceptor extends AbstractEdiPostInterceptor {

    private static Logger LOGGER = Logger.getLogger(ITSRailConsistEdiPostInterceptor.class)

    private void logMsg(String inMsg) {
        LOGGER.warn(" ITSRailConsistEdiPostInterceptor :" + inMsg)
    }

    @Override
    void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        //LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSRailConsistEdiPostInterceptor - beforeEdiPost - Execution started.")
        if (RailConsistTransactionsDocument.class.isAssignableFrom(inXmlTransactionDocument.getClass())) {
            RailConsistTransactionsDocument railConsistTransactionsDocument = (RailConsistTransactionsDocument) inXmlTransactionDocument
            RailConsistTransactionsDocument.RailConsistTransactions railConsistTransactions = railConsistTransactionsDocument.getRailConsistTransactions()
            List<RailConsistTransactionDocument.RailConsistTransaction> railConsistTransactionsList = railConsistTransactions.getRailConsistTransactionList()
            if (railConsistTransactionsList != null && railConsistTransactionsList.size() == 1) {
                RailConsistTransactionDocument.RailConsistTransaction railConsistTransaction = railConsistTransactionsList.get(0)


                List<RailConsistTransactionDocument.RailConsistTransaction.EdiRailCarContainer> ediRailCarContainers = railConsistTransaction.getEdiRailCarContainerList()
                if (ediRailCarContainers != null) {
                    for (RailConsistTransactionDocument.RailConsistTransaction.EdiRailCarContainer ediRailCarContainer : ediRailCarContainers) {
                        EdiContainer ediContainer = ediRailCarContainer.getEdiContainer()
                        if (ediContainer != null) {
                            EdiOperator ediOperator = ediContainer.getContainerOperator()
                            if (ediOperator != null) {
                                String operator = ediOperator.getOperator()
                                if (operator != null && !operator.isEmpty()) {
                                    LineOperator lineOperator = findLineOperatorByScac(operator)
                                    if (lineOperator == null) {
                                        EdiFlexFields flexFields = ediRailCarContainer.getEdiFlexFields();
                                        if (flexFields != null) {
                                            String val = flexFields.getUfvFlexString01()
                                            Serializable sessionGKey = (Serializable) inParams.get(EdiConsts.SESSION_GKEY);
                                            EdiSession ediSession = (EdiSession) HibernateApi.getInstance().load(EdiSession.class, sessionGKey);

                                            DomainQuery dq = QueryUtils.createDomainQuery(EdiEntity.EDI_SESSION_FILTER)
                                                    .addDqPredicate(PredicateFactory.eq(EdiField.EDISESSFLTR_SESSION, ediSession.getEdisessGkey()))
                                            //.addDqField(EdiField.EDISESSFLTR_FILTER)
                                            List<EdiSessionFilter> ediSessionFilterList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
                                            if (ediSessionFilterList != null) {
                                                for (Object ediSessionFilter : ediSessionFilterList) {
                                                    EdiSessionFilter filter = (EdiSessionFilter) ediSessionFilter
                                                    if (filter != null) {
                                                        EdiFilter ediFilter = filter.getEdisessfltrFilter()
                                                        Set ediFilterEntrys = ediFilter.getEdifltrFltrEn()
                                                        for (Object ediFilterEntryObj : ediFilterEntrys) {
                                                            EdiFilterEntry filterEntry = (EdiFilterEntry) ediFilterEntryObj
                                                            if (filterEntry != null) {
                                                                String fromVal = filterEntry.getEdifltrenFromValue()
                                                                if (fromVal != null && val.equalsIgnoreCase(fromVal)) {
                                                                    ediContainer.getContainerOperator().setOperator(filterEntry.getEdifltrenToValue())
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                RailConsistTransactionDocument.RailConsistTransaction.EdiRailCarVisit ediRailCarVisit = railConsistTransaction.getEdiRailCarVisit()
                if (ediRailCarVisit != null) {
                    EdiRailCar ediRailCar = ediRailCarVisit.getRailCar()
                    if (ediRailCar != null) {
                        String railCarId = ediRailCar.getRailCarId()
                        Railcar railCar = railCarId != null ? Railcar.findRailcar(railCarId) : null
                        if (railCar != null) {
                            RailcarVisit railcarVisit = RailcarVisit.findActiveRailCarVisit(railCar)
                            if (railcarVisit != null) {
                                String rcarVisitIbTrainId = railcarVisit.getCarrierIbVoyNbrOrTrainId()
                                if (railcarVisit.railcarVisitSpotted == true) {
                                    LOGGER.debug("ITSRailConsistEdiPostInterceptor - Is Spotted : " + railcarVisit.railcarVisitSpotted)
                                    inParams.put("SKIP_POSTER", Boolean.TRUE)
                                    new GroovyApi().registerWarning("Railcar :" + railCarId + " is spotted. Skipping EDI post.")
                                } else if (!SpottingStatusEnum.NOTSPOTTED.equals(railcarVisit.getRcarvSpottingStatus())) {
                                    LOGGER.debug("ITSRailConsistEdiPostInterceptor - Spotting Status : " + railcarVisit.getRcarvSpottingStatus())
                                    inParams.put("SKIP_POSTER", Boolean.TRUE)
                                    new GroovyApi().registerWarning("Spotting Status updated for railcar :" + railCarId + ". Skipping EDI post.")
                                } else if (railcarVisit.getRcarvTrack() != null) {
                                    LOGGER.debug("ITSRailConsistEdiPostInterceptor - Railcar Track : " + railcarVisit.getRcarvTrack())
                                    inParams.put("SKIP_POSTER", Boolean.TRUE)
                                    new GroovyApi().registerWarning("Track assigned for railcar :" + railCarId + ". Skipping EDI post.")
                                } else if (rcarVisitIbTrainId != null && !BNSF_TRAIN_VISIT.equalsIgnoreCase(rcarVisitIbTrainId) && !UP_TRAIN_VISIT.equalsIgnoreCase(rcarVisitIbTrainId)) {
                                    LOGGER.debug("ITSRailConsistEdiPostInterceptor - Inbound Train : " + rcarVisitIbTrainId)
                                    inParams.put("SKIP_POSTER", Boolean.TRUE)
                                    new GroovyApi().registerWarning("IB Train Visit assigned for railcar :" + railCarId + ". Skipping EDI post.")
                                }
                            }
                        }
                    }
                }
            }
        }
        LOGGER.debug("ITSRailConsistEdiPostInterceptor - beforeEdiPost - Execution completed.")
    }

    private static LineOperator findLineOperatorByScac(String inLineId) {
        DomainQuery domainQuery = QueryUtils.createDomainQuery(ArgoRefEntity.LINE_OPERATOR)
                .addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_SCAC, inLineId))
        .addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_ROLE, BizRoleEnum.LINEOP));
        return (LineOperator) HibernateApi.getInstance().getUniqueEntityByDomainQuery(domainQuery);
    }

    private final static String BNSF_TRAIN_VISIT = "IB_BNSF_TRAIN"
    private final static String UP_TRAIN_VISIT = "IB_UP_TRAIN"
}
