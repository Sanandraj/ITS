//package ITS


import com.navis.argo.ContextHelper
import com.navis.argo.EdiRailCar
import com.navis.argo.RailConsistTransactionDocument
import com.navis.argo.RailConsistTransactionsDocument
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.model.CarrierVisit
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.rail.business.atoms.SpottingStatusEnum
import com.navis.rail.business.entity.Railcar
import com.navis.rail.business.entity.RailcarVisit
import com.navis.rail.business.entity.TrainVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject

/*
 *
 * @Author <a href="mailto:sanandaraj@weservetech.com">Anandaraj S</a>, 04/AUG/2022
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
 *
 */

class ITSRailConsistEdiPostInterceptor extends AbstractEdiPostInterceptor {

    private static Logger LOGGER = Logger.getLogger(ITSRailConsistEdiPostInterceptor.class)

    private void logMsg(String inMsg) {
        LOGGER.warn(" ITSRailConsistEdiPostInterceptor :" + inMsg)
    }

    @Override
    void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        LOGGER.setLevel(Level.DEBUG);
        LOGGER.debug("ITSRailConsistEdiPostInterceptor - beforeEdiPost - Execution started.")


        if (RailConsistTransactionsDocument.class.isAssignableFrom(inXmlTransactionDocument.getClass())) {
            RailConsistTransactionsDocument railConsistTransactionsDocument = (RailConsistTransactionsDocument) inXmlTransactionDocument
            RailConsistTransactionsDocument.RailConsistTransactions railConsistTransactions = railConsistTransactionsDocument.getRailConsistTransactions()
            List<RailConsistTransactionDocument.RailConsistTransaction> railConsistTransactionsList = railConsistTransactions.getRailConsistTransactionList()
            if (railConsistTransactionsList != null && railConsistTransactionsList.size() == 1) {
                RailConsistTransactionDocument.RailConsistTransaction railConsistTransaction = railConsistTransactionsList.get(0)

                RailConsistTransactionDocument.RailConsistTransaction.EdiRailCarVisit ediRailCarVisit = railConsistTransaction.getEdiRailCarVisit()
                if (ediRailCarVisit != null) {
                    EdiRailCar ediRailCar = ediRailCarVisit.getRailCar()
                    if (ediRailCar != null) {
                        String railCarId = ediRailCar.getRailCarId()
                        if (railCarId != null) {
                            Railcar railCar = Railcar.findRailcar(railCarId)
                            if (railCar != null) {
                                RailcarVisit railcarVisit = RailcarVisit.findActiveRailCarVisit(railCar)
                                if (railcarVisit != null) {
                                    if (!SpottingStatusEnum.NOTSPOTTED.equals(railcarVisit.getRcarvSpottingStatus())) {
                                        inParams.put("SKIP_POSTER", Boolean.TRUE);
                                        new GroovyApi().registerWarning("Railcar " + railCarId + " Contains  the Spot Status ,So not allowed to proceed.")

                                    }


                                    String railibVisitId = railcarVisit.getRcarvTrainVisitInbound().getCvdCv() != null ? railcarVisit.getRcarvTrainVisitInbound().getCvdCv().getCvId() : null
                                    if (railibVisitId != null) {
                                        CarrierVisit cv = CarrierVisit.findTrainVisit(ContextHelper.getThreadComplex(), ContextHelper.getThreadFacility(), railibVisitId)
                                        if (cv == null) {
                                            inParams.put("SKIP_POSTER", Boolean.TRUE);
                                            new GroovyApi().registerWarning("Given TrainVisitDetails is not Valid.")

                                        } else {
                                            TrainVisitDetails tvd = TrainVisitDetails.resolveTvdFromCv(cv)
                                            if (tvd == null) {
                                                inParams.put("SKIP_POSTER", Boolean.TRUE);
                                                new GroovyApi().registerWarning("Given TrainVisitDetails  is not Valid.")
                                            }
                                            if (tvd != null) {
                                                LOGGER.debug("ITSRailConsistEdiPostInterceptor - tvd : " + tvd)
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
