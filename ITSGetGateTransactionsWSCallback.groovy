import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.zk.util.JSONBuilder
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.road.RoadEntity
import com.navis.road.RoadField
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.atoms.TruckVisitStatusEnum
import com.navis.road.business.atoms.TruckerFriendlyTranSubTypeEnum
import com.navis.road.business.model.TruckTransaction
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable
import java.text.DateFormat
import java.text.SimpleDateFormat

/*
 * @Author <a href="mailto:annalakshmig@weservetech.com">ANNALAKSHMI G</a>
 * Date: 08/01/2022
 * Requirements:-  Returns a list of Gate Transactions that meet the given conditions in JSON format
 *  @Inclusion Location	: Incorporated as a code extension of the type TRANSACTED_BUSINESS_FUNCTION --> Paste this code (ITSGetGateTransactionsWSCallback.groovy)
 *
 */

class ITSGetGateTransactionsWSCallback extends AbstractExtensionPersistenceCallback {

    @Override
    void execute(@Nullable Map inMap, @Nullable Map outMap) {
        LOGGER.debug("ITSGetGateTransactionsWSCallback :: start")
        String trkcId = inMap.containsKey("truckingCoScac") ? inMap.get("truckingCoScac") : null
        String tranDate = inMap.containsKey("gateTransDate") ? inMap.get("gateTransDate") : null
        String containerNumber = inMap.containsKey("containerNumbers") ? inMap.get("containerNumbers") : null
        String[] containerNumbers = containerNumber?.toUpperCase()?.split(",")*.trim()
        outMap.put("RESPONSE", prepareGateTransactionMessageToITS(trkcId, tranDate, containerNumbers))
    }


    String prepareGateTransactionMessageToITS(String trkcId, String tranDate, String[] ctrNbrs) {
        String errorMessage = validateMandatoryFields(trkcId, tranDate)
        JSONBuilder gateTransactionsObj = JSONBuilder.createObject();
        if (errorMessage.length() > 0) {
            gateTransactionsObj.put("errorMessage", errorMessage)
        } else {
            DomainQuery dq = QueryUtils.createDomainQuery(RoadEntity.TRUCK_TRANSACTION)
                    .addDqPredicate(PredicateFactory.eq(RoadField.TRAN_TRKC_ID, trkcId))
                    .addDqPredicate(PredicateFactory.between(RoadField.TRAN_CREATED, getStartDate(tranDate), getEndDate(tranDate)))
            if (ctrNbrs != null && ctrNbrs.size() > 0) {
                dq.addDqPredicate(PredicateFactory.in(RoadField.TRAN_CTR_NBR, ctrNbrs))
            }
            LOGGER.debug("ITSGetGateTransactionsWSCallback :: dq" + dq)
            Serializable[] truckTransactionGkeys = HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq)
            if (truckTransactionGkeys != null && truckTransactionGkeys.size() > 0) {
                String gateTrnStatusDesc = ""
                String gateTransactionFunction = ""
                JSONBuilder jsonArray = JSONBuilder.createArray()
                TruckTransaction truckTransaction
                for (Serializable trkTranGkey : truckTransactionGkeys) {
                    truckTransaction = TruckTransaction.hydrate((trkTranGkey))
                    if (truckTransaction != null) {
                        switch (truckTransaction.getTranTruckerTranSubType()) {
                            case TruckerFriendlyTranSubTypeEnum.PUC:
                                gateTransactionFunction = BARE_OUT
                                gateTrnStatusDesc = checkDeliveryGateStatus(truckTransaction)
                                break;

                            case TruckerFriendlyTranSubTypeEnum.DOC:
                                gateTransactionFunction = BARE_IN
                                gateTrnStatusDesc = checkReceivalGateStatus(truckTransaction)
                                break;

                            case TruckerFriendlyTranSubTypeEnum.PUE:
                            case TruckerFriendlyTranSubTypeEnum.PUI:
                                gateTransactionFunction = FULL_OUT
                                gateTrnStatusDesc = checkDeliveryGateStatus(truckTransaction)
                                break;

                            case TruckerFriendlyTranSubTypeEnum.DOE:
                            case TruckerFriendlyTranSubTypeEnum.DOI:
                                gateTransactionFunction = FULL_IN
                                gateTrnStatusDesc = checkReceivalGateStatus(truckTransaction)
                                break;

                            case TruckerFriendlyTranSubTypeEnum.PUM:
                                gateTransactionFunction = EMPTY_OUT
                                gateTrnStatusDesc = checkDeliveryGateStatus(truckTransaction)
                                break;

                            case TruckerFriendlyTranSubTypeEnum.DOM:
                                gateTransactionFunction = EMPTY_IN
                                gateTrnStatusDesc = checkReceivalGateStatus(truckTransaction)
                                break;

                        }


                        JSONBuilder jsonObject = JSONBuilder.createObject();
                        jsonObject.put("ticketNum", truckTransaction.getTranNbr()?.toString() != null ? truckTransaction.getTranNbr().toString() : "")
                        jsonObject.put("gateTransDtTm", truckTransaction.getTranCreated() != null ? ISO_DATE_FORMAT.format(truckTransaction.getTranCreated()) : "")
                        jsonObject.put("shippingLineScac", truckTransaction.getTranLineId() != null ? truckTransaction.getTranLineId() : "")
                        jsonObject.put("gateTransFunctionDsc", gateTransactionFunction)

                        if (truckTransaction.getTranStatus() != null) {
                            if (TranStatusEnum.TROUBLE.equals(truckTransaction.getTranStatus())) {
                                gateTrnStatusDesc = TROUBLED
                            } else if (TranStatusEnum.CANCEL.equals(truckTransaction.getTranStatus())) {
                                gateTrnStatusDesc = CANCELLED
                            }
                            jsonObject.put("gateTransStatusDsc", gateTrnStatusDesc)
                        }

                        jsonObject.put("driverLicenseNum", truckTransaction.getTranTruckVisit()?.getTvdtlsDriverLicenseNbr() != null ? truckTransaction.getTranTruckVisit().getTvdtlsDriverLicenseNbr() : "")
                        String truckPos = ""
                        if (truckTransaction.getTranTruckVisit() != null) {
                            if (TruckVisitStatusEnum.COMPLETE.equals(truckTransaction.getTranTruckVisit().getTvdtlsStatus())
                                    || TruckVisitStatusEnum.CLOSED.equals(truckTransaction.getTranTruckVisit().getTvdtlsStatus())
                                    || TruckVisitStatusEnum.CANCEL.equals(truckTransaction.getTranTruckVisit().getTvdtlsStatus())) {
                                truckPos = EXIT
                            } else if (TruckVisitStatusEnum.TROUBLE.equals(truckTransaction.getTranTruckVisit().getTvdtlsStatus())) {
                                truckPos = WINDOW
                            } else {
                                if (YARD_ID.equalsIgnoreCase(truckTransaction.getTranTruckVisit().getTvdtlsNextStageId())) {
                                    truckPos = IN_YARD
                                } else if (OUTGATE_ID.equalsIgnoreCase(truckTransaction.getTranTruckVisit().getTvdtlsNextStageId())) {
                                    truckPos = OUT_GATE
                                } else {
                                    truckPos = IN_GATE
                                }
                            }
                        }
                        jsonObject.put("truckPositionDsc", truckPos)
                        jsonObject.put("truckPositionDtTm", truckTransaction.getTranTruckVisit()?.getTvdtlsChanged() != null ? ISO_DATE_FORMAT.format(truckTransaction.getTranTruckVisit().getTvdtlsChanged()) : ISO_DATE_FORMAT.format(truckTransaction.getTranTruckVisit().getTvdtlsCreated()))
                        if (truckTransaction.getTranCtrNbr() != null || truckTransaction.getTranCtrNbrAssigned() != null) {
                            jsonObject.put("containerNumber", truckTransaction.getTranCtrNbr() != null ? truckTransaction.getTranCtrNbr() : truckTransaction.getTranCtrNbrAssigned())
                        }

                        if (truckTransaction.getTranChsNbr() != null) {
                            jsonObject.put("chassisNumber", truckTransaction.getTranChsNbr())

                        }
                        jsonObject.put("truckingCoScac", trkcId)
                        jsonArray.add(jsonObject)

                    }
                }
                gateTransactionsObj.put("gateTransactions", jsonArray)
                LOGGER.debug("Response string" + gateTransactionsObj.toJSONString())

            }
        }
        return gateTransactionsObj.toJSONString()
    }

    private Date getStartDate(String datestr) {
        Date startDate = DST_DATE_FORMAT.parse(DST_DATE_FORMAT.format(SRC_DATE_FORMAT.parse(datestr)))
        Calendar c1 = Calendar.getInstance()
        c1.setTime(startDate)
        c1.set(Calendar.HOUR_OF_DAY, 0)
        c1.set(Calendar.MINUTE, 0)
        c1.set(Calendar.SECOND, 0)
        c1.set(Calendar.MILLISECOND, 0)
        return c1.getTime();
    }

    private Date getEndDate(String datestr) {
        Date endDate = DST_DATE_FORMAT.parse(DST_DATE_FORMAT.format(SRC_DATE_FORMAT.parse(datestr)))
        Calendar c1 = Calendar.getInstance()
        c1.setTime(endDate)
        c1.set(Calendar.HOUR_OF_DAY, 23)
        c1.set(Calendar.MINUTE, 59)
        c1.set(Calendar.SECOND, 59)
        c1.set(Calendar.MILLISECOND, 999)
        return c1.getTime()
    }

    private String validateMandatoryFields(String trkcId, String tranDate) {
        StringBuilder stringBuilder = new StringBuilder()
        if (StringUtils.isEmpty(trkcId)) {
            stringBuilder.append("Missing required parameter : truckingCoScac.").append(" :: ")
        }
        if (StringUtils.isEmpty(tranDate)) {
            stringBuilder.append("Missing required parameter : gateTransDate.")
        }
        return stringBuilder.toString()
    }

    private String checkReceivalGateStatus(TruckTransaction transaction) {
        String gateStatus = ""
        if(transaction.getTranUfv() != null && transaction.getTranUfv().isTransitState(UfvTransitStateEnum.S40_YARD)) {
            gateStatus = RECEIVED
        }
       else if (TranStatusEnum.OK.equals(transaction.getTranStatus())) {
            if (INGATE_ID.equalsIgnoreCase(transaction.getTranStageId())) {
                gateStatus = EIRED
            }
        }

        return gateStatus
    }

    private String checkDeliveryGateStatus(TruckTransaction transaction) {
        String gateStatus = ""
        if(transaction.getTranUfv() != null && transaction.getTranUfv().isTransitState(UfvTransitStateEnum.S70_DEPARTED)) {
            gateStatus = COMPLETED
        }
        else  if(transaction.getTranUfv() != null && transaction.getTranUfv().isTransitStateBeyond(UfvTransitStateEnum.S40_YARD)) {
            gateStatus = RELEASED
        }
        if (TranStatusEnum.OK.equals(transaction.getTranStatus())) {
            if (INGATE_ID.equalsIgnoreCase(transaction.getTranStageId())) {
                gateStatus = ISSUED
            } /*else if (YARD_ID.equalsIgnoreCase(transaction.getTranStageId())) {
                gateStatus = RELEASED
            } else if (OUTGATE_ID.equalsIgnoreCase(transaction.getTranStageId())) {
                gateStatus = COMPLETED
            }*/
        }

        return gateStatus
    }

    private static final String BARE_IN = "BARE-IN"
    private static final String BARE_OUT = "BARE-OUT"
    private static final String EMPTY_IN = "EMPTY-IN"
    private static final String EMPTY_OUT = "EMPTY-OUT"
    private static final String FULL_IN = "FULL-IN"
    private static final String FULL_OUT = "FULL-OUT"
    private static final String IN_GATE = "IN-GATE"
    private static final String IN_YARD = "IN-YARD"
    private static final String OUT_GATE = "OUT-GATE"
    private static final String WINDOW = "WINDOW"
    private static final String EXIT = "EXIT"
    private static final String EIRED = "EIRED"
    private static final String RECEIVED = "RECEIVED"
    private static final String ISSUED = "ISSUED"
    private static final String RELEASED = "RELEASED"
    private static final String COMPLETED = "COMPLETED"
    private static final String TROUBLED = "TROUBLED"
    private static final String CANCELLED = "CANCELLED"
    private static final String INGATE_ID = "ingate"
    private static final String YARD_ID = "yard"
    private static final String OUTGATE_ID = "outgate"
    private static DateFormat SRC_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy")
    private static DateFormat DST_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd")
    private static DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("YYYY-MM-DD'T'HH:mm:ss");
    private static Logger LOGGER = Logger.getLogger(this.class);

}
