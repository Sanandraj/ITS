package ITS

import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.zk.util.JSONBuilder
import com.navis.road.RoadEntity
import com.navis.road.RoadField
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
        String trkcId = inMap.containsKey(TRUCKING_CO_SCAC) ? inMap.get(TRUCKING_CO_SCAC) : null
        String tranDate = inMap.containsKey(GATE_TRANS_DATE) ? inMap.get(GATE_TRANS_DATE) : null
        String containerNumber = inMap.containsKey(CONTAINER_NUMBERS) ? inMap.get(CONTAINER_NUMBERS) : null
        String[] containerNumbers = containerNumber?.toUpperCase()?.split(",")*.trim()
        outMap.put("RESPONSE", prepareGateTransactionMessageToITS(trkcId, tranDate, containerNumbers))
    }

    String prepareGateTransactionMessageToITS(String trkcId, String tranDate, String[] ctrNbrs) {
        String errorMessage = validateMandatoryFields(trkcId, tranDate)
        JSONBuilder gateTransactionsObj = JSONBuilder.createObject();
        if (errorMessage.length() > 0) {
            gateTransactionsObj.put(ERROR_MESSAGE, errorMessage)
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
                String gateTrnStatusDesc = EMPTY_STR
                String gateTransactionFunction = EMPTY_STR
                JSONBuilder jsonArray = JSONBuilder.createArray()
                TruckTransaction truckTransaction
                for (Serializable trkTranGkey : truckTransactionGkeys) {
                    truckTransaction = TruckTransaction.hydrate((trkTranGkey))
                    if (truckTransaction != null) {
                        switch (truckTransaction.getTranTruckerTranSubType()) {
                            case TruckerFriendlyTranSubTypeEnum.PUC:
                                gateTransactionFunction = BARE_OUT

                                break;

                            case TruckerFriendlyTranSubTypeEnum.DOC:
                                gateTransactionFunction = BARE_IN

                                break;

                            case TruckerFriendlyTranSubTypeEnum.PUE:
                            case TruckerFriendlyTranSubTypeEnum.PUI:
                                gateTransactionFunction = FULL_OUT

                                break;

                            case TruckerFriendlyTranSubTypeEnum.DOE:
                            case TruckerFriendlyTranSubTypeEnum.DOI:
                                gateTransactionFunction = FULL_IN

                                break;

                            case TruckerFriendlyTranSubTypeEnum.PUM:
                                gateTransactionFunction = EMPTY_OUT

                                break;

                            case TruckerFriendlyTranSubTypeEnum.DOM:
                                gateTransactionFunction = EMPTY_IN

                                break;
                        }


                        JSONBuilder jsonObject = JSONBuilder.createObject();
                        jsonObject.put(GATE_TRANS_ID, trkTranGkey)
                        jsonObject.put(TICKET_NUM, truckTransaction.getTranNbr()?.toString() != null ? truckTransaction.getTranNbr().toString() : EMPTY_STR)
                        jsonObject.put(GATE_TRANS_DT_TM, truckTransaction.getTranCreated() != null ? ISO_DATE_FORMAT.format(truckTransaction.getTranCreated()) : EMPTY_STR)
                        jsonObject.put(SHIPPING_LINE_CD, truckTransaction.getTranLineId() != null ? truckTransaction.getTranLineId() : EMPTY_STR)
                        if (truckTransaction.getTranLineId() != null) {
                            ScopedBizUnit scopedBizUnit = ScopedBizUnit.findScopedBizUnit(truckTransaction.getTranLineId(), BizRoleEnum.LINEOP)
                            jsonObject.put(SHIPPING_LINE_SCAC, scopedBizUnit?.getBzuScac() != null ? scopedBizUnit.getBzuScac() : EMPTY_STR)
                        }else{
                            jsonObject.put(SHIPPING_LINE_SCAC, truckTransaction.getTranLine()?.getBzuScac() != null ? truckTransaction.getTranLine().getBzuScac() : EMPTY_STR)

                        }
                        jsonObject.put(GATE_TRANS_FUNCTION_DSC, gateTransactionFunction)
                        jsonObject.put(GATE_TRANS_STATUS_DSC, truckTransaction.getTranStatus() != null ? truckTransaction.getTranStatus().getKey() : EMPTY_STR)
                        jsonObject.put(DRIVER_LICENSE_NUM, truckTransaction.getTranTruckVisit()?.getTvdtlsDriverLicenseNbr() != null ? truckTransaction.getTranTruckVisit().getTvdtlsDriverLicenseNbr() : "")
                        String truckPos = ""
                        if (truckTransaction.getTranTruckVisit() != null) {
                            if (TruckVisitStatusEnum.COMPLETE.equals(truckTransaction.getTranTruckVisit().getTvdtlsStatus())
                                    || TruckVisitStatusEnum.CLOSED.equals(truckTransaction.getTranTruckVisit().getTvdtlsStatus())
                                    || TruckVisitStatusEnum.CANCEL.equals(truckTransaction.getTranTruckVisit().getTvdtlsStatus())) {
                                truckPos = EXIT
                            } else if (TruckVisitStatusEnum.TROUBLE.equals(truckTransaction.getTranTruckVisit().getTvdtlsStatus())) {
                                truckPos = WINDOW
                            } else {

                                if (YARD_ID.equalsIgnoreCase(truckTransaction.getTranTruckVisit().getTvdtlsNextStageId()) || CHECKDELIVERY.equalsIgnoreCase(truckTransaction.getTranTruckVisit().getTvdtlsNextStageId())) {
                                    truckPos = IN_YARD
                                } else if (OUTGATE_ID.equalsIgnoreCase(truckTransaction.getTranTruckVisit().getTvdtlsNextStageId())) {
                                    truckPos = OUT_GATE
                                } /*else {
                                    truckPos = IN_GATE
                                }*/
                            }
                        }
                        jsonObject.put(TRUCK_POSITION_DSC, truckPos)
                        jsonObject.put(TRUCK_POSITION_DT_TM, truckTransaction.getTranTruckVisit()?.getTvdtlsChanged() != null ? ISO_DATE_FORMAT.format(truckTransaction.getTranTruckVisit().getTvdtlsChanged()) : ISO_DATE_FORMAT.format(truckTransaction.getTranTruckVisit().getTvdtlsCreated()))
                        if (truckTransaction.getTranCtrNbr() != null || truckTransaction.getTranCtrNbrAssigned() != null) {
                            jsonObject.put(CONTAINER_NUMBER, truckTransaction.getTranCtrNbr() != null ? truckTransaction.getTranCtrNbr() : truckTransaction.getTranCtrNbrAssigned())
                        }

                        if (truckTransaction.getTranChsNbr() != null) {
                            jsonObject.put(CHASSIS_NUMBER, truckTransaction.getTranChsNbr())

                        }
                        jsonObject.put(TRUCKING_CO_SCAC, trkcId)
                        jsonArray.add(jsonObject)

                    }
                }
                gateTransactionsObj.put(GATE_TRANSACTIONS, jsonArray)
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
    private static final String CHECKDELIVERY = "checkdelivery"

    private static final String TRUCKING_CO_SCAC = "truckingCoScac"
    private static final String GATE_TRANS_DATE = "gateTransDate"
    private static final String CONTAINER_NUMBERS = "containerNumbers"
    private static final String TICKET_NUM = "ticketNum"
    private static final String GATE_TRANS_DT_TM = "gateTransDtTm"
    private static final String SHIPPING_LINE_SCAC = "shippingLineScac"
    private static final String SHIPPING_LINE_CD = "shippingLineCd"

    private static final String GATE_TRANS_FUNCTION_DSC = "gateTransFunctionDsc"
    private static final String GATE_TRANS_STATUS_DSC = "gateTransStatusDsc"
    private static final String CONTAINER_NUMBER = "containerNumber"
    private static final String CHASSIS_NUMBER = "chassisNumber"
    private static final String DRIVER_LICENSE_NUM = "driverLicenseNum"
    private static final String TRUCK_POSITION_DSC = "truckPositionDsc"
    private static final String TRUCK_POSITION_DT_TM = "truckPositionDtTm"
    private static final String GATE_TRANSACTIONS = "gateTransactions"
    private static final String ERROR_MESSAGE = "errorMessage"
    private static final String EMPTY_STR = ""
    private static final String GATE_TRANS_ID = "gateTransId"

    private static DateFormat SRC_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy")
    private static DateFormat DST_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd")
    private static DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static Logger LOGGER = Logger.getLogger(this.class);

}
