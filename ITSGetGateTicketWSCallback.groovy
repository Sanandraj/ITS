import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.reference.Chassis
import com.navis.argo.business.reference.Container
import com.navis.argo.business.reference.LineOperator
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.zk.util.JSONBuilder
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.road.RoadEntity
import com.navis.road.RoadField
import com.navis.road.business.atoms.TruckerFriendlyTranSubTypeEnum
import com.navis.road.business.model.Document
import com.navis.road.business.model.DocumentMessage
import com.navis.road.business.model.TruckTransaction
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.stream.Collectors

/*
 * @Author <a href="mailto:annalakshmig@weservetech.com">ANNALAKSHMI G</a>
 * Date: 29/12/2021
 * Requirements:- Receives Ticket Number and Gate trasaction date and returns Gate ticket details
 *  @Inclusion Location	: Incorporated as a code extension of the type TRANSACTED_BUSINESS_FUNCTION --> Paste this code (ITSGetGateTicketWSCallback.groovy)
 */

class ITSGetGateTicketWSCallback extends AbstractExtensionPersistenceCallback {

    @Override
    void execute(@Nullable Map inMap, @Nullable Map outMap) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSGetGateTicketWSCallback :: start" + inMap)
        String ticketNumber = inMap.containsKey("ticketNum") ? inMap.get("ticketNum") : null
        String tranDate = inMap.containsKey("gateTransDate") ? inMap.get("gateTransDate") : null
        outMap.put("RESPONSE", prepareGateTicketMessageToITS(ticketNumber, tranDate))
    }


    String prepareGateTicketMessageToITS(String ticketNbr, String tranDate) {
        JSONBuilder jsonObject = JSONBuilder.createObject();
        String errorMessage = validateMadtoryFields(ticketNbr, tranDate)
        if (errorMessage.length() > 0) {
            jsonObject.put("errorMessage", errorMessage)
        } else {
            DomainQuery dq = QueryUtils.createDomainQuery(RoadEntity.TRUCK_TRANSACTION)
                    .addDqPredicate(PredicateFactory.eq(RoadField.TRAN_NBR, ticketNbr))
                    .addDqPredicate(PredicateFactory.between(RoadField.TRAN_CREATED, getStartDate(tranDate), getEndDate(tranDate)))
            LOGGER.debug("ITSGetGateTicketWSCallback :: dq" + dq)

            TruckTransaction truckTransaction = (TruckTransaction) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq)
            String ctrSizeType;
            String chassisSizeType;
            String gateTransactionFunction = null
            Document document
            if (truckTransaction != null) {
                if (truckTransaction.getTranDocuments() != null && truckTransaction.getTranDocuments().size() > 0) {
                    List<Document> documentList = truckTransaction.getTranDocuments().stream().collect(Collectors.toList());
                    Collections.sort(documentList, new Comparator<Document>() {
                        @Override
                        int compare(Document doc1, Document doc2) {
                            return doc1.getDocCreated().compareTo(doc2.getDocCreated())
                        }
                    });
                    document = documentList.get(0)
                }

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

                    case TruckerFriendlyTranSubTypeEnum.DOB:
                        gateTransactionFunction = "RB"
                        break;
                    case TruckerFriendlyTranSubTypeEnum.PUB:
                        gateTransactionFunction = "DB"
                        break;
                }
                jsonObject.put("ticketNum", truckTransaction.getTranNbr()?.toString() != null ? truckTransaction.getTranNbr().toString() : "")
                jsonObject.put("gateTransDtTm", truckTransaction.getTranCreated() != null ? ISO_DATE_FORMAT.format(truckTransaction.getTranCreated()) : "")
                jsonObject.put("printDtTm", document?.getDocCreated() != null ? ISO_DATE_FORMAT.format(document.getDocCreated()) : "")
                jsonObject.put("terminalCd", ContextHelper.getThreadFacilityId() != null ? ContextHelper.getThreadFacilityId() : "")
                jsonObject.put("terminalFullName", TERMINAL_NAME)
                jsonObject.put("shippingLineScac", truckTransaction.getTranLineId() != null ? truckTransaction.getTranLineId() : "")
                String shippingLineName = ""
                if (truckTransaction.getTranLine() != null) {
                    shippingLineName = truckTransaction.getTranLine().getBzuName()
                } else if (truckTransaction.getTranLineId()) {
                    LineOperator lineOperator = LineOperator.findLineOperatorById(truckTransaction.getTranLineId())
                    if (lineOperator != null) {
                        shippingLineName = lineOperator.getBzuName()
                    }
                }
                jsonObject.put("shippingLineName", shippingLineName)

                jsonObject.put("laneNum", truckTransaction.getTranTruckVisit()?.getTvdtlsExitLane()?.getLaneId() != null ? truckTransaction.getTranTruckVisit().getTvdtlsExitLane().getLaneId() : truckTransaction.getTranTruckVisit()?.getTvdtlsEntryLane()?.getLaneId())
                jsonObject.put("gateTransFunctionDsc", gateTransactionFunction)
                jsonObject.put("ticketTypeDsc", document?.getDocDocType()?.getDoctypeId() != null ? document.getDocDocType().getDoctypeId() : "")
                jsonObject.put("driverLicenseNum", truckTransaction.getTranTruckVisit()?.getTvdtlsDriverLicenseNbr() != null ? truckTransaction.getTranTruckVisit().getTvdtlsDriverLicenseNbr() : "")
                jsonObject.put("driverName", truckTransaction.getTranTruckVisit()?.getTvdtlsDriverName() != null ? truckTransaction.getTranTruckVisit().getTvdtlsDriverName() : "")
                jsonObject.put("truckingCoScac", truckTransaction.getTranTruckingCompany()?.getBzuScac() != null ? truckTransaction.getTranTruckingCompany().getBzuScac() : "")
                jsonObject.put("truckingCoName", truckTransaction.getTranTruckingCompany()?.getBzuName() != null ? truckTransaction.getTranTruckingCompany().getBzuName() : "")
                // jsonObject.put("clerkName", document. != null ? ISO_DATE_FORMAT.format(vvd.getCvdETA()) : "")

                if (truckTransaction.getTranCtrNbr() != null || truckTransaction.getTranCtrNbrAssigned()) {
                    String containerNbr = truckTransaction.getTranCtrNbr() != null ? truckTransaction.getTranCtrNbr() : truckTransaction.getTranCtrNbrAssigned()
                    jsonObject.put("containerNumber", containerNbr)
                    Container container = Container.findContainer(containerNbr)
                    if (container != null) {
                        ctrSizeType = new StringBuilder().append(container.getEqEquipType().getEqtypNominalLength().getKey().substring(3, 5))
                                .append(container.getEqEquipType().getEqtypIsoGroup().getKey())
                                .append(container.getEqEquipType().getEqtypNominalHeight().getKey().substring(3, 5)).toString()
                        jsonObject.put("containerSzTpHt", ctrSizeType)
                    }
                }
                if (truckTransaction.getTranUfv() != null && truckTransaction.getTranUfv().isTransitState(UfvTransitStateEnum.S40_YARD)) {
                    jsonObject.put("containerSpotNum", truckTransaction.getTranUfv().getUfvLastKnownPosition().getPosName())
                }

                if (truckTransaction.getTranChsNbr() != null) {
                    jsonObject.put("chassisNumber", truckTransaction.getTranChsNbr())
                    Chassis chassis = Chassis.findChassis(truckTransaction.getTranChsNbr())
                    if (chassis != null) {
                        chassisSizeType = new StringBuilder().append(chassis.getEqEquipType().getEqtypNominalLength().getKey().substring(3, 5))
                                .append(chassis.getEqEquipType().getEqtypIsoGroup().getKey()).toString()
                        jsonObject.put("chassisSzTp", chassisSizeType)
                    }
                    if (truckTransaction.getTranChsPosition() != null && truckTransaction.getTranChsPosition().getPosName() != null && truckTransaction.getTranChsPosition().getPosName().startsWith(YARD_POS)) {
                        jsonObject.put("chassisSpotNum", truckTransaction.getTranChsPosition().getPosName())
                    }
                }


                if (truckTransaction.getTranCtrAccNbr() != null || truckTransaction.getTranChsAccNbr() != null) {
                    jsonObject.put("gensetNumber", truckTransaction.getTranCtrAccNbr() != null ? truckTransaction.getTranCtrAccNbr() : truckTransaction.getTranChsAccNbr())
                }
                if (truckTransaction.getTranCarrierVisit() != null && truckTransaction.getTranCarrierVisit().getCarrierVehicleName() != null) {
                    jsonObject.put("vesselName", truckTransaction.getTranCarrierVisit().getCarrierVehicleName())
                }
                if (truckTransaction.getTranCarrierVisit() != null) {
                    String voyageCall
                    /*if (UnitCategoryEnum.IMPORT.equals(truckTransaction.getTranUnitCategory())) {
                        jsonObject.put("voyageCall", new StringBuilder().append(truckTransaction.getTranCarrierVisit().getCarrierIbVoyNbrOrTrainId())
                                .append(" ").append(truckTransaction.getTranCarrierVisit().getCarrierIbVisitCallNbr()).toString())
                    } else if (UnitCategoryEnum.EXPORT.equals(truckTransaction.getTranUnitCategory())) {
                        jsonObject.put("voyageCall", new StringBuilder().append(truckTransaction.getTranCarrierVisit().getCarrierObVoyNbrOrTrainId())
                                .append(" ").append(truckTransaction.getTranCarrierVisit().getCarrierObVisitCallNbr()).toString())
                    }*/

                    jsonObject.put("voyageCall", new StringBuilder().append(truckTransaction.getTranCarrierVisit().getCarrierObVoyNbrOrTrainId())
                            .append(" ").append(truckTransaction.getTranCarrierVisit().getCarrierObVisitCallNbr()).toString())
                }
                if (truckTransaction.getTranEqoNbr() != null || truckTransaction.getTranBlNbr() != null) {
                    jsonObject.put("cargoRefNum", truckTransaction.getTranEqoNbr() != null ? truckTransaction.getTranEqoNbr() : truckTransaction.getTranBlNbr())
                }
                if (truckTransaction.getTranTruckVisit() != null && truckTransaction.getTranTruckVisit().getTvdtlsTruckAeiTagId() != null) {
                    jsonObject.put("truckTagId", truckTransaction.getTranTruckVisit().getTvdtlsTruckAeiTagId())
                }
                if (document != null) {
                    Set<DocumentMessage> documentMessages = (Set<DocumentMessage>) document.getDocMessages()
                    Iterator<DocumentMessage> iterator = documentMessages.iterator();
                    for (int i = 1; i < 6 && iterator.hasNext(); i++) {
                        DocumentMessage documentMessage = iterator.next()
                        jsonObject.put("messageTxt${i}", documentMessage != null ? documentMessage.getDocmsgMsgText() : "")

                    }
                }
            }
        }
        return jsonObject.toJSONString()
    }

    private String validateMadtoryFields(String ticketNbr, String tranDate) {
        StringBuilder stringBuilder = new StringBuilder()
        if (StringUtils.isEmpty(ticketNbr)) {
            stringBuilder.append("Missing required parameter : ticketNumber.").append(" :: ")
        }
        if (StringUtils.isEmpty(tranDate)) {
            stringBuilder.append("Missing required parameter : gateTransDate.")
        }
        return stringBuilder.toString()
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


    private static DateFormat SRC_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy")
    private static DateFormat DST_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd")
    private static DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("YYYY-MM-DD'T'HH:mm:ss");
    private static final String TERMINAL_NAME = "International Transportation Service, Inc."
    private static final String BARE_IN = "BARE-IN"
    private static final String BARE_OUT = "BARE-OUT"
    private static final String EMPTY_IN = "EMPTY-IN"
    private static final String EMPTY_OUT = "EMPTY-OUT"
    private static final String FULL_IN = "FULL-IN"
    private static final String FULL_OUT = "FULL-OUT"
    private static final String YARD_POS = "Y-ITS"

    private static Logger LOGGER = Logger.getLogger(this.class);


}

