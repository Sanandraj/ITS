package ITS

import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.GeneralReference
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
import com.navis.road.business.atoms.TranSubTypeEnum
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
        String errorMessage = validateMandatoryFields(ticketNbr, tranDate)
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
            String ctrSpotNum
            Document document
            if (truckTransaction != null) {
                if (truckTransaction.getTranDocuments() != null && truckTransaction.getTranDocuments().size() > 0) {
                    List<Document> documentList = truckTransaction.getTranDocuments().stream().collect(Collectors.toList());
                    Collections.sort(documentList, new Comparator<Document>() {
                        @Override
                        int compare(Document doc1, Document doc2) {
                            return -doc1.getDocCreated().compareTo(doc2.getDocCreated())
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
                        ctrSpotNum = getDeliveryCtrSpotNum(truckTransaction)
                        break;

                    case TruckerFriendlyTranSubTypeEnum.DOE:
                    case TruckerFriendlyTranSubTypeEnum.DOI:
                        gateTransactionFunction = FULL_IN
                        ctrSpotNum = getReceivalCtrSpotNum(truckTransaction)
                        break;

                    case TruckerFriendlyTranSubTypeEnum.PUM:
                        gateTransactionFunction = EMPTY_OUT
                        ctrSpotNum = getDeliveryCtrSpotNum(truckTransaction)
                        break;

                    case TruckerFriendlyTranSubTypeEnum.DOM:
                        gateTransactionFunction = EMPTY_IN
                        ctrSpotNum = getReceivalCtrSpotNum(truckTransaction)
                        break;
                }
                jsonObject.put("ticketNum", truckTransaction.getTranNbr()?.toString() != null ? truckTransaction.getTranNbr().toString() : "")
                jsonObject.put("gateTransDtTm", truckTransaction.getTranCreated() != null ? ISO_DATE_FORMAT.format(truckTransaction.getTranCreated()) : "")
                jsonObject.put("printDtTm", document?.getDocCreated() != null ? ISO_DATE_FORMAT.format(document.getDocCreated()) : "")
                jsonObject.put("terminalCd", ContextHelper.getThreadOperator()?.getOprId() != null ? ContextHelper.getThreadOperator().getOprId() : "")
                GeneralReference generalReference = GeneralReference.findUniqueEntryById("ITS", "TERMINAL_NAME")
                if (generalReference != null && generalReference.getRefValue1() != null) {
                    jsonObject.put("terminalFullName", generalReference.getRefValue1())
                }
                jsonObject.put("shippingLineCd", truckTransaction.getTranLineId() != null ? truckTransaction.getTranLineId() : "")


                String shippingLineName = ""
                String slScac = ""
                if (truckTransaction.getTranLine() != null) {
                    shippingLineName = truckTransaction.getTranLine().getBzuName()
                    slScac = truckTransaction.getTranLine().getBzuScac()
                } else if (truckTransaction.getTranLineId() != null) {
                    LineOperator lineOperator = LineOperator.findLineOperatorById(truckTransaction.getTranLineId())
                    if (lineOperator != null) {
                        shippingLineName = lineOperator.getBzuName()
                        slScac = lineOperator.getBzuScac()
                    }
                }
                jsonObject.put("shippingLineName", shippingLineName)
                jsonObject.put("shippingLineScac", slScac)
                jsonObject.put("laneNum", truckTransaction.getTranTruckVisit()?.getTvdtlsExitLane()?.getLaneId() != null ? truckTransaction.getTranTruckVisit().getTvdtlsExitLane().getLaneId() : truckTransaction.getTranTruckVisit()?.getTvdtlsEntryLane()?.getLaneId())
                jsonObject.put("gateTransFunctionDsc", gateTransactionFunction)
                jsonObject.put("ticketTypeDsc", document?.getDocDocType()?.getDoctypeId() != null ? document.getDocDocType().getDoctypeId() : "")
                jsonObject.put("driverLicenseNum", truckTransaction.getTranTruckVisit()?.getTvdtlsDriverLicenseNbr() != null ? truckTransaction.getTranTruckVisit().getTvdtlsDriverLicenseNbr() : "")
                jsonObject.put("driverName", truckTransaction.getTranTruckVisit()?.getTvdtlsDriverName() != null ? truckTransaction.getTranTruckVisit().getTvdtlsDriverName() : "")
                jsonObject.put("truckingCoScac", truckTransaction.getTranTruckingCompany()?.getBzuId() != null ? truckTransaction.getTranTruckingCompany().getBzuId() : "")
                jsonObject.put("truckingCoName", truckTransaction.getTranTruckingCompany()?.getBzuName() != null ? truckTransaction.getTranTruckingCompany().getBzuName() : "")
                jsonObject.put("clerkName", truckTransaction.getTranCreator() != null ? truckTransaction.getTranCreator() : "")

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
                    if (!StringUtils.isEmpty(ctrSpotNum)) {
                        jsonObject.put("containerSpotNum", ctrSpotNum)
                    }
                }


                if (truckTransaction.getTranChsNbr() != null) {
                    jsonObject.put("chassisNumber", truckTransaction.getTranChsNbr())
                    Chassis chassis = Chassis.findChassis(truckTransaction.getTranChsNbr())
                    if (chassis != null) {
                        chassisSizeType = new StringBuilder().append(chassis.getEqEquipType().getEqtypNominalLength().getKey().substring(3, 5))
                                .append(chassis.getEqEquipType().getEqtypIsoGroup().getKey()).toString()
                        jsonObject.put("chassisSzTp", chassisSizeType)
                    }
                    if (TranSubTypeEnum.RC.equals(truckTransaction.getTranSubType()) || TranSubTypeEnum.DC.equals(truckTransaction.getTranSubType())) {
                        if (truckTransaction.getTranUfv() != null && truckTransaction.getTranUfv().isTransitState(UfvTransitStateEnum.S40_YARD)) {
                            jsonObject.put("chassisSpotNum", truckTransaction.getTranUfv().getUfvLastKnownPosition().getPosName())
                        }
                    } else {
                        if (truckTransaction.getTranChsPosition() != null && truckTransaction.getTranChsPosition().getPosName() != null && truckTransaction.getTranChsPosition().getPosName().startsWith(YARD_POS)) {
                            jsonObject.put("chassisSpotNum", truckTransaction.getTranChsPosition().getPosName())
                        }
                    }
                }


                if (truckTransaction.getTranCtrAccNbr() != null || truckTransaction.getTranChsAccNbr() != null) {
                    jsonObject.put("gensetNumber", truckTransaction.getTranCtrAccNbr() != null ? truckTransaction.getTranCtrAccNbr() : truckTransaction.getTranChsAccNbr())
                }
                if (truckTransaction.getTranCarrierVisit() != null && truckTransaction.getTranCarrierVisit().getCarrierVehicleName() != null) {
                    jsonObject.put("vesselName", truckTransaction.getTranCarrierVisit().getCarrierVehicleName())
                }
                if (truckTransaction.getTranCarrierVisit() != null) {

                    if (UnitCategoryEnum.IMPORT.equals(truckTransaction.getTranUnitCategory())) {
                        jsonObject.put("voyageCall", truckTransaction.getTranCarrierVisit().getCarrierIbVoyNbrOrTrainId())

                    } else if (UnitCategoryEnum.EXPORT.equals(truckTransaction.getTranUnitCategory())) {
                        jsonObject.put("voyageCall", truckTransaction.getTranCarrierVisit().getCarrierObVoyNbrOrTrainId())
                    }


                }
                if (truckTransaction.getTranEqoNbr() != null || truckTransaction.getTranBlNbr() != null) {
                    jsonObject.put("cargoRefNum", truckTransaction.getTranEqoNbr() != null ? truckTransaction.getTranEqoNbr() : truckTransaction.getTranBlNbr())
                }
                if (truckTransaction.getTranTruckVisit() != null && truckTransaction.getTranTruckVisit().getTvdtlsTruckAeiTagId() != null) {
                    jsonObject.put("truckTagId", truckTransaction.getTranTruckVisit().getTvdtlsTruckAeiTagId())
                }
                if (document != null) {
                    Set<DocumentMessage> documentMessages = (Set<DocumentMessage>) document.getDocMessages()
                    if (documentMessages != null && documentMessages.size() > 0) {
                        Iterator<DocumentMessage> iterator = documentMessages.iterator();
                        for (int i = 1; i < 6 && iterator.hasNext(); i++) {
                            DocumentMessage documentMessage = iterator.next()
                            jsonObject.put("messageTxt${i}", documentMessage != null ? documentMessage.getDocmsgMsgText() : "")

                        }
                    } else {
                        List<DocumentMessage> docMsgs = DocumentMessage.findByTransactionForStageId(truckTransaction, document.getDocStageId())
                        List<DocumentMessage> docMsg = new ArrayList<>()
                        if (docMsgs != null && docMsgs.size() > 0) {
                            Iterator<DocumentMessage> iterator = docMsgs.iterator();
                            for (int i = 0; i < docMsgs.size() && iterator.hasNext(); i++) {
                                DocumentMessage docMessage = iterator.next()
                                if (docMessage != null && document.getDocCreated().format("dd/MM/yyyy HH:mm").equals(docMessage.getDocmsgCreated().format("dd/MM/yyyy HH:mm"))) {

                                    docMsg.add(docMessage)
                                }

                            }
                        }
                        if (docMsg != null && docMsg.size() > 0) {
                            Iterator<DocumentMessage> docMsgIterator = docMsg.iterator();
                            for (int i = 1; i < 6 && docMsgIterator.hasNext(); i++) {
                                DocumentMessage documentMessage = docMsgIterator.next()
                                jsonObject.put("messageTxt${i}", documentMessage != null ? documentMessage.getDocmsgMsgText() : "")

                            }

                        }

                    }
                }
            }
        }
        return jsonObject.toJSONString()
    }


    private String validateMandatoryFields(String ticketNbr, String tranDate) {
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

    private String getDeliveryCtrSpotNum(TruckTransaction truckTransaction) {
        String ctrSpotNumber
        if (truckTransaction != null && truckTransaction.getTranUfv() != null && truckTransaction.getTranUfv().getUfvLastKnownPosition() != null
                && truckTransaction.getTranUfv().getUfvLastKnownPosition().getPosName() != null) {

            if (truckTransaction.getTranUfv().getUfvLastKnownPosition().getPosName().startsWith("Y")) {
                ctrSpotNumber = StringUtils.substringAfter(truckTransaction.getTranUfv().getUfvLastKnownPosition().getPosName(), "Y-PIERG-")
            }

        }
        return ctrSpotNumber
    }

    private String getReceivalCtrSpotNum(TruckTransaction truckTransaction) {
        String ctrSpotNumber
        if (truckTransaction != null && truckTransaction.getTranUfv() != null) {
            if (truckTransaction.getTranUfv().getFinalPlannedPosition() != null
                    && truckTransaction.getTranUfv().getFinalPlannedPosition().getPosName() != null
                    && truckTransaction.getTranUfv().getFinalPlannedPosition().getPosName().startsWith("Y")) {
                ctrSpotNumber = StringUtils.substringAfter(truckTransaction.getTranUfv().getFinalPlannedPosition().getPosName(), "Y-PIERG-")
            }
        }
        return ctrSpotNumber
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
    private static DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    //private static final String TERMINAL_NAME = "International Transportation Service, Inc."
    private static final String BARE_IN = "BARE-IN"
    private static final String BARE_OUT = "BARE-OUT"
    private static final String EMPTY_IN = "EMPTY-IN"
    private static final String EMPTY_OUT = "EMPTY-OUT"
    private static final String FULL_IN = "FULL-IN"
    private static final String FULL_OUT = "FULL-OUT"
    private static final String YARD_POS = "Y-ITS"
    private static final String EIN_EIR = "EXPORT-IN EIR"
    private static final String IIN_EIR = "IMPORT-IN EIR"
    private static final String MIN_EIR = "EMPTY-IN EIR"
    private static final String BCIN_EIR = "BARE CHASSIS-IN EIR"
    private static final String EXPORT_PICKUP = "EXPORT PICKUP"
    private static final String IMPORT_PICKUP = "IMPORT PICKUP"
    private static final String EMPTY_PICKUP = "EMPTY PICKUP"
    private static final String BARE_CHASSIS_PICKUP = "BARE CHASSIS PICKUP"
    private static final String EXPORT_OUT_EIR = "EXPORT-OUT EIR"
    private static final String IMPORT_OUT_EIR = "IMPORT-OUT EIR"
    private static final String EMPTY_OUT_EIR = "EMPTY-OUT EIR"
    private static final String BARE_CHASSIS_OUT_EIR = "BARE CHASSIS-OUT EIR"
    private static final String TROUBLE_TICKET = "TROUBLE TICKET"
    private static final String TURN_AROUND_TICKET = "TURN-AROUND TICKET"
    private static Logger LOGGER = Logger.getLogger(this.class);


}
