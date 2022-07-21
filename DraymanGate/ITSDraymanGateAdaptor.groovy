import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.EquipNominalHeightEnum
import com.navis.argo.business.integration.IntegrationServiceMessage
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.LocPosition
import com.navis.carina.integrationservice.business.IntegrationService
import com.navis.framework.IntegrationServiceField
import com.navis.framework.business.Roastery
import com.navis.framework.business.atoms.IntegrationServiceDirectionEnum
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.Disjunction
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.scope.ScopeCoordinates
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.RoadEntity
import com.navis.road.RoadField
import com.navis.road.business.atoms.GateStageTypeEnum
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.model.*
import com.navis.spatial.business.model.AbstractBin
import com.navis.xpscache.business.atoms.EquipBasicLengthEnum
import groovy.sql.Sql
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.text.SimpleDateFormat
import java.util.concurrent.*

/**
 * @author <a href="mailto:sramasamy@weservetech.com"> Ramasamy Sathappan</a>
 * @since 18-May-2022
 *
 * library class for processing and sending the drayman gate message
 *
 * */
class ITSDraymanGateAdaptor {

    // On MOVE_TV event - call from ITSDraymanGateMessageUpdate general notice groovy
    public void execute(TruckVisitDetails inTv) {
        try {
            LOGGER.setLevel(Level.DEBUG);
            if (inTv == null)
                return;

            logMsg("inTv nextstageId: "+inTv.getTvdtlsNextStageId())
            if (!T_YARD.equals(inTv.getTvdtlsNextStageId()))
                return;

            List inGateReceivalList = new ArrayList();
            List inGateDeliveryList = new ArrayList();
            List outGateReceivalList = new ArrayList();

            Set<TruckTransaction> truckTransactions = inTv.getTvdtlsTruckTrans();
            for (TruckTransaction tran : truckTransactions) {
                if (TranStatusEnum.COMPLETE == tran.getTranStatus() || TranStatusEnum.OK == tran.getTranStatus()) {
                    Map containerDetailsMap = getContainerDetailsMap(tran, null);
                    GateStageTypeEnum gateStage = getGateStageType(tran);
                    if (gateStage == GateStageTypeEnum.OUT) {
                        outGateReceivalList.add(containerDetailsMap);
                    } else if (gateStage == GateStageTypeEnum.IN) {
                        if (tran.isReceival()) {
                            inGateReceivalList.add(containerDetailsMap);
                        } else {
                            inGateDeliveryList.add(containerDetailsMap);
                        }
                    }
                }
            }
            logMsg("inGateReceivalList: " + inGateReceivalList + ", inGateDeliveryList: " + inGateDeliveryList + ", outGateReceivalList: " + outGateReceivalList);
            if (inGateReceivalList.isEmpty() && inGateDeliveryList.isEmpty() && outGateReceivalList.isEmpty())
                return;

            frameAndSendMessage(inTv, T__SITE_ARRIVAL, inGateReceivalList);
            frameAndSendMessage(inTv, T__SITE_DEPARTURE, inGateDeliveryList);
            frameAndSendMessage(inTv, T__PICKUP, outGateReceivalList);

        } catch (Exception e) {
            LOGGER.error("Exception in execute: " + e.getMessage());
        }
    }

    // On cancelling transaction - call from TruckTransaction ELI
    public void prepareAndPushMessage(TruckTransaction inTruckTran, String inMessageType) {
        logMsg("prepareAndPushMessage END");
        List containerList = new ArrayList();
        containerList.add(getContainerDetailsMap(inTruckTran, null));
        frameAndSendMessage(inTruckTran.getTranTruckVisit(), inMessageType, containerList);
        logMsg("prepareAndPushMessage END");
    }

    // On cancelling transaction - call from TruckTransaction ELI
    public void prepareAndPushMessageForTvdtls(TruckVisitDetails inTvdtls, String inMessageType) {
        logMsg("prepareAndPushMessageForTvdtls TransactionsInProgress - " + inTvdtls.getTransactionsInProgress());
        logMsg("prepareAndPushMessageForTvdtls TransactionsToBeClosed - " + inTvdtls.getTransactionsToBeClosed());
        logMsg("prepareAndPushMessageForTvdtls TransactionsToBeHandled - " + inTvdtls.getTransactionsToBeHandled());

        List containerList = new ArrayList();
        for (TruckTransaction tran : inTvdtls.getTransactionsInProgress())
            containerList.add(getContainerDetailsMap(tran, null));
        frameAndSendMessage(inTvdtls, inMessageType, containerList);

        logMsg("prepareAndPushMessageForTvdtls END");
    }


    // On position / planned position update - Call from WI ELI
    public void prepareAndPushMessageForPositionChange(Unit inUnit, LocPosition inLocPosition) {
        logMsg("prepareAndPushMessageForPositionChange BEGIN ");
        TruckTransaction transaction = findTransactionsForUnitId(inUnit.getUnitId());
        TruckVisitDetails truckVisitDetails = transaction ? transaction.getTranTruckVisit() : null;
        String locPosition = (truckVisitDetails ? (truckVisitDetails.getTvdtlsPosition() ? truckVisitDetails.getTvdtlsPosition().getPosSlot() : null) : null);
        logMsg("locPosition: "+locPosition);
        if (truckVisitDetails && T__TIP.equals(locPosition)) {
            List containerList = new ArrayList();
            Map msgDetails = getGenericDetails(truckVisitDetails);
            if (msgDetails != null) { //position change
                /*if (inLocPosition == null) {
                    if (transaction.isDelivery()) {
                        msgDetails.put(T__MSG_TYPE, T__PICKUP);
                        containerList.add(getContainerDetailsMap(transaction, null));
                    }
                } else { //planned position change
                    if (transaction.isReceival()) {
                        msgDetails.put(T__MSG_TYPE, T__SITE_ARRIVAL);
                        containerList.add(getContainerDetailsMap(transaction, inLocPosition));
                    }
                }*/

                if (transaction.isReceival()) {
                    msgDetails.put(T__MSG_TYPE, T__SITE_ARRIVAL);
                    containerList.add(getContainerDetailsMap(transaction, inLocPosition));
                } else {
                    msgDetails.put(T__MSG_TYPE, T__PICKUP);
                    containerList.add(getContainerDetailsMap(transaction, null));
                }

                if (containerList.size() > 0) {
                    msgDetails.put(T__CNTR, containerList);
                    createAndSendDraymanMessage(msgDetails);
                }
            }
        }
        LOGGER.debug("prepareAndPushMessageForPositionChange END");
    }


    private TruckTransaction findTransactionsForUnitId(String inContainerNumber) {
        Disjunction ctrNbrOrRequested = (Disjunction) PredicateFactory.disjunction()
                .add(PredicateFactory.eq(RoadField.TRAN_CTR_NBR_ASSIGNED, inContainerNumber))
                .add(PredicateFactory.eq(RoadField.TRAN_CTR_NBR, inContainerNumber));

        DomainQuery dq = QueryUtils.createDomainQuery(RoadEntity.TRUCK_TRANSACTION)
                        .addDqPredicate(ctrNbrOrRequested)
                        .addDqPredicate(PredicateFactory.eq(RoadField.TRAN_STATUS, TranStatusEnum.OK))
                        .addDqOrdering(Ordering.desc(RoadField.TRAN_CREATED))

        List<TruckTransaction> transactions = (List<TruckTransaction>) HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

        return (transactions && transactions.size()>0)? transactions.get(0) : null;
    }

    private void frameAndSendMessage(TruckVisitDetails visitDetails, String msgType, List containerList) {
        if (containerList.size() > 0) {
            Map msgDetails = getGenericDetails(visitDetails);
            logMsg("msgDetails: " + msgDetails);
            if (msgDetails != null) {
                msgDetails.put(T__MSG_TYPE, msgType);
                msgDetails.put(T__CNTR, containerList);
                createAndSendDraymanMessage(msgDetails);
            }
        }
    }

    private Map getGenericDetails(TruckVisitDetails inTv) {
        LOGGER.setLevel(Level.DEBUG);
        logMsg("getGenericDetails - BEGIN");
        GateLane tvdtlsEntryLane = inTv.getTvdtlsEntryLane();
        GateLane tvdtlsExitLane = inTv.getTvdtlsExitLane();
        if (tvdtlsEntryLane == null && tvdtlsExitLane == null)
            return null;

        String laneId = T_EMPTY;
        if (tvdtlsExitLane != null) { // Exit lane
            laneId = tvdtlsExitLane.getLaneId();
        } else if ((tvdtlsEntryLane != null)) {  // Entry lane
            laneId = tvdtlsEntryLane.getLaneId();
        }

        Map genericMap = new HashMap();
        genericMap.put(T__TIME, dateFormatter.format(new Date()));
        genericMap.put(T__TRUCK_ID, inTv.getTvdtlsTruckLicenseNbr());
        genericMap.put(T__TRUCK_TYPE, T__DRAYMAN);
        genericMap.put(T__TAG_ID, inTv.getTvdtlsTruck()? inTv.getTvdtlsTruck().getTruckAeiTagId() : T_EMPTY);
        genericMap.put(T__LICENCE_NBR, inTv.getTvdtlsTruckLicenseNbr());
        genericMap.put(T__LICENCE_STATE, inTv.getTvdtlsTruck().getTruckLicenseState() ? inTv.getTvdtlsTruck().getTruckLicenseState() : T_EMPTY);
        genericMap.put(T__EX_ERR_REASON, T_EMPTY);
        genericMap.put(T__EX_TEC, T_EMPTY);
        genericMap.put(T___TYPE, T__INBOUND);
        genericMap.put(T__LANE, laneId);
        logMsg("getGenericDetails - END");

        return genericMap;
    }


    private Map getContainerDetailsMap(TruckTransaction tran, LocPosition locPos) {
        logMsg("getContainerDetailsMap - BEGIN : " + tran.getTranCtrNbr())
        /*logMsg("appt: "+tran.getTranAppointment())
        Unit unit = tran.getTranUnit()? tran.getTranUnit() : tran.getTranAppointment().getGapptUnit();

        String unitId = unit? unit.getUnitId() : T_EMPTY;*/

        String eqLength = "20";
        if (EquipBasicLengthEnum.BASIC40 == tran.getTranEqLength(EquipBasicLengthEnum.BASIC40)) {
            eqLength = "40";
        }
        String eqWeight = tran.getTranCtrGrossWeight() ? tran.getTranCtrGrossWeight().toString() : T_EMPTY;
        String eqHeight = getCntrHeight(tran.getTranEqoEqHeight());
        String chassisPos = tran.getTranCtrTruckPosition() ? tran.getTranCtrTruckPosition().toString() : T_EMPTY;

        Map posValues = new HashMap();
        if (locPos == null) {
            posValues = getValueFromPosition(getCtrPosition(tran));
        } else {
            posValues = getValueFromPosition(locPos);
        }

        logMsg("frame container details")
        Map containerDetails = new HashMap();
        containerDetails.put(T__ID, tran.getTranCtrNbr());
        containerDetails.put(T__LENGTH, eqLength);
        containerDetails.put(T__WEIGHT, eqWeight);
        containerDetails.put(T__HEIGHT, eqHeight);
        containerDetails.put(T__LOAD_STATUS, T__L);
        containerDetails.put(T__CHASSIS_POSITION, chassisPos);
        containerDetails.put(T__CUSTOM1, getCustom1(posValues.get(T__CELL)));
        containerDetails.put(T__ROW, posValues.get(T__ROW));
        containerDetails.put(T__BAY, posValues.get(T__BAY));
        containerDetails.put(T__CELL, posValues.get(T__CELL));
        containerDetails.put(T__TIER, posValues.get(T__TIER));
        containerDetails.put(T__SLOT, posValues.get(T__SLOT));

        logMsg("getContainerDetailsMap - END : " + containerDetails);
        return containerDetails;
    }

    private void createAndSendDraymanMessage(Map msgDetails) {
        String message = makeDraymanMessage(msgDetails);
        logMsg("message: " + message)
        if (ArgoUtils.isNotEmpty(message)) {
            pushDraymanMessageViaJDBC(msgDetails.get(T__TIME), message, msgDetails);
        };
    }

    private String makeDraymanMessage(Map msgDetails) {
        String message = T_EMPTY;
        List containerList = msgDetails.get(T__CNTR);
        if (containerList.size() == 0)
            return message;

        int iCount = 0;
        StringBuffer containerMessageSb = new StringBuffer();
        for (Map containerMap : containerList) {
            iCount++;
            containerMessageSb.append(
                    String.format(CONTAINER_MESSAGE, String.valueOf(iCount),
                            containerMap.get(T__ID),
                            containerMap.get(T__LENGTH),
                            containerMap.get(T__WEIGHT),
                            containerMap.get(T__HEIGHT),
                            containerMap.get(T__LOAD_STATUS),
                            containerMap.get(T__CHASSIS_POSITION),
                            containerMap.get(T__CUSTOM1),
                            containerMap.get(T__ROW),
                            containerMap.get(T__BAY),
                            containerMap.get(T__CELL),
                            containerMap.get(T__TIER),
                            containerMap.get(T__SLOT))
            );
        }

        message = String.format(DRAYMAN_MESSAGE,
                msgDetails.get(T__TIME),
                msgDetails.get(T__MSG_TYPE),
                msgDetails.get(T__TRUCK_ID),
                msgDetails.get(T__TRUCK_TYPE),
                msgDetails.get(T__TAG_ID),
                msgDetails.get(T__LICENCE_NBR),
                msgDetails.get(T__LICENCE_STATE),
                msgDetails.get(T__EX_ERR_REASON),
                msgDetails.get(T__EX_TEC),
                containerMessageSb.toString(),
                msgDetails.get(T___TYPE),
                msgDetails.get(T__LANE),
        );

        return message;
    }

    // send xml message vid JDBC connection to MTS package
    private void pushDraymanMessageViaJDBC(String stampDateTimeMS, String inDraymanMessage, Map inMsgDetails) {
        logMsg("pushDraymanMessageViaJDBC - BEGIN");
        GeneralReference genRefDbConnection = GeneralReference.findUniqueEntryById(T_KALMAR, T_MTS);
        if (genRefDbConnection == null) {
            logMsg("MTS DB connection details missing, return");
            return;
        }
        String username = genRefDbConnection.getRefValue1();
        String password = genRefDbConnection.getRefValue2();
        String databaseName = T_JDBC_PREFIX + genRefDbConnection.getRefValue3();
        String packageName = genRefDbConnection.getRefValue4();

        try {
            Sql sql = Sql.newInstance(databaseName, username, password, DRIVER_NAME);
            String sqlStatement = packageName + "(\'$MTS_GROOVY_NAME\' , \'$inDraymanMessage\')";
            GString gString = GString.EMPTY + "{call " + sqlStatement + "}";
            logMsg("gString : " + gString.toString());

            int callResult;
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            Future<Integer> future = executorService.submit(new Callable<Integer>() {
                @Override
                Integer call() throws Exception {
                    callResult = sql.call(gString);
                }
            })
            logMsg("callResult: " + callResult);

            IntegrationServiceMessage ism;
            IntegrationService iServ = getUriFromIntegrationService(T__DRAYMAN, IntegrationServiceDirectionEnum.OUTBOUND);
            logMsg("iServ: "+iServ)
            ism = createIntegrationSrcMsg(iServ, inDraymanMessage, inMsgDetails.get(T__TAG_ID), inMsgDetails.get(T__MSG_TYPE));

            int timeOut = 4000;
            if (genRefDbConnection.getRefValue5() != null) {
                timeOut = genRefDbConnection.getRefValue5().toInteger();
            }

            try {
                int result = future.get(timeOut, TimeUnit.MILLISECONDS);
                logMsg("result : " + result);
                logMsg("ism: "+ism)
                if (result < 0)
                    ism.setIsmUserString5(T__FAILURE);

                if (ism != null) {
                    HibernateApi.getInstance().save(ism);
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                executorService.shutdown();
            }

        } catch (Exception e) {
            logMsg(e.getMessage())
        }
        logMsg("pushDraymanMessageViaJDBC - END");
    }


    private String getCustom1(String inRow) {
        String retValue = T_EMPTY;
        if (ArgoUtils.isNotEmpty(inRow)) {
            try {
                int numRow = Integer.parseInt(inRow);
                if (numRow >= 1 && numRow <= 7) {
                    retValue = inRow + "-S"
                } else if (numRow >= 8 && numRow <= 14) {
                    retValue = inRow + "-N"
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage());
            }
        }
        return retValue;
    }

    private LocPosition getCtrPosition(TruckTransaction inTran) {
        LocPosition locPosition = null;
        Unit unit = inTran.getTranUnit();
        if (unit) {
            UnitFacilityVisit ufv = unit.getUnitActiveUfv();
            if (ufv) {
                GateStageTypeEnum gateStage = this.getGateStageType(inTran);
                if (gateStage == GateStageTypeEnum.IN) {
                    if (inTran.isReceival()) {
                        locPosition = ufv.getFinalPlannedPosition();
                        if (locPosition == null && inTran.getTranCtrPosition() != null) {
                            locPosition = inTran.getTranCtrPosition().isYardPosition() ? inTran.getTranCtrPosition() : null
                        }
                    }
                    if (inTran.isDelivery()) {
                        locPosition = ufv.getUfvLastKnownPosition();
                    }
                } else if (gateStage == GateStageTypeEnum.OUT) {
                    locPosition = ufv.getUfvLastKnownPosition();
                }
            }
        }
        return locPosition;
    }

    private String getCntrHeight(EquipNominalHeightEnum heightEnum) {
        String heightEnumName = heightEnum ? heightEnum.getName() : T_EMPTY;
        String value = T_EMPTY;
        if (ArgoUtils.isNotEmpty(heightEnumName)) {
            String str = "NOM";
            int indexStart = heightEnumName.indexOf(str);
            int indexEnd = heightEnumName.length();
            if (indexStart >= 0 && indexEnd > 0) {
                value = heightEnumName.substring(indexStart + str.length(), indexEnd);
                if (value.isInteger()) {
                    int num = Integer.parseInt(value);
                    int q = num / 10;
                    int r = num % 10;
                    value = q.toString() + "." + r.toString();
                } else {
                    value = T_EMPTY;
                }
            }
        }
        return value;
    }


    private Map getValueFromPosition(LocPosition locPosition) {
        Map posValues = new HashMap();
        posValues.put(T__ROW, T_EMPTY);
        posValues.put(T__BAY, T_EMPTY);
        posValues.put(T__CELL, T_EMPTY);
        posValues.put(T__TIER, T_EMPTY);
        posValues.put(T__SLOT, T_EMPTY);

        if (locPosition != null) {
            retrievePosition(locPosition, posValues);
            posValues.put(T__TIER, locPosition.getPosTier());
        }
        return posValues;
    }


    private void retrievePosition(LocPosition position, Map posValues) {
        String rowVal = null;
        String slotVal = null;
        String blockVal = null;
        AbstractBin stackBin = position.getPosBin();
        if (stackBin != null) {
            if (ABM_STACK.equalsIgnoreCase(stackBin.getAbnBinType().getBtpId())) {
                String stackBinName = stackBin.getAbnName()
                AbstractBin sectionBin = stackBin.getAbnParentBin();
                if (sectionBin != null && ABM_SECTION.equalsIgnoreCase(sectionBin.getAbnBinType().getBtpId())) {
                    String sectionBinName = sectionBin.getAbnName()
                    rowVal = sectionBinName;
                    slotVal = stackBinName.substring(stackBinName.indexOf(sectionBinName) + sectionBinName.size())
                    AbstractBin blockBin = sectionBin.getAbnParentBin();
                    if (!position.isWheeled() && blockBin != null && ABM_BLOCK.equalsIgnoreCase(blockBin.getAbnBinType().getBtpId())) {
                        String blockBinName = blockBin.getAbnName()
                        blockVal = blockBinName;
                        rowVal = sectionBinName.substring(sectionBinName.indexOf(blockBinName) + blockBinName.size());
                    }
                }
                if (position.isWheeled()) {
                    posValues.put(T__ROW, rowVal);
                    posValues.put(T__SLOT, slotVal);
                } else {
                    posValues.put(T__ROW, blockVal);
                    posValues.put(T__BAY, rowVal);
                    posValues.put(T__CELL, slotVal);
                }

            } else if (position.isWheeledHeap() || ABM_BLOCK.equalsIgnoreCase(stackBin.getAbnBinType().getBtpId())) {
                posValues.put(T__ROW, stackBin.getAbnName());
            }
        }
    }

    private GateStageTypeEnum getGateStageType(TruckTransaction truckTransaction) {
        GateStageTypeEnum gateStageType = null;
        TruckVisitDetails dtlsTV = truckTransaction.getTranTruckVisit();
        if (dtlsTV != null) {
            Gate gate = dtlsTV.getTvdtlsGate();
            if (gate != null) {
                GateConfiguration gateConfig = gate.getGateConfig();
                if (gateConfig != null) {
                    GateConfigStage gateConfStage = GateConfigStage.findGateConfigStage(truckTransaction.getTranStageId(), gateConfig, gate);
                    if (gateConfStage != null) {
                        gateStageType = gateConfStage.getStageType();
                    }
                }
            }
        }
        return gateStageType;
    }


    public IntegrationService getUriFromIntegrationService(String inName, IntegrationServiceDirectionEnum inDirection) {
        DomainQuery dq = QueryUtils.createDomainQuery("IntegrationService")
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_NAME, inName))
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_DIRECTION, inDirection))
                .addDqPredicate(PredicateFactory.eq(IntegrationServiceField.INTSERV_ACTIVE, Boolean.TRUE));
        return (IntegrationService) Roastery.getHibernateApi().getUniqueEntityByDomainQuery(dq);
    }

    //public IntegrationServiceMessage createIntegrationSrcMsg(Event inEvent, String inEntityId, IntegrationService inIntegrationService, String inMessagePayload, String inEventGkey, String inRequestResponseId) {
    public IntegrationServiceMessage createIntegrationSrcMsg(IntegrationService inIntegrationService, String inMessagePayload, String truckTagId, String messageType) {
        LOGGER.setLevel(Level.DEBUG);
        logMsg("createIntegrationSrcMsg");
        IntegrationServiceMessage integrationServiceMessage = new IntegrationServiceMessage();
        try {
            /*if (inEvent) {
                integrationServiceMessage.setIsmEventPrimaryKey((Long) inEvent.getEvntEventType().getPrimaryKey());
                integrationServiceMessage.setIsmEntityClass(inEvent.getEventAppliedToClass());
                integrationServiceMessage.setIsmEventTypeId(inEvent.getEventTypeId());
            }
            if (inEntityId)
                integrationServiceMessage.setIsmEntityNaturalKey(inEntityId);*/

            if (inIntegrationService) {
                integrationServiceMessage.setIsmIntegrationService(inIntegrationService);
                integrationServiceMessage.setIsmFirstSendTime(ArgoUtils.timeNow());
            }

            integrationServiceMessage.setIsmUserString1(truckTagId);
            integrationServiceMessage.setIsmUserString2(messageType);
            integrationServiceMessage.setIsmUserString5(T__SUCCESS);

            /*if (inRequestResponseId) {
                integrationServiceMessage.setIsmUserString2(inRequestResponseId);
            }
            if (inEventGkey) {
                integrationServiceMessage.setIsmUserString3(inEventGkey);
            }*/

            String msg = inMessagePayload.length() > DB_CHAR_LIMIT ? inMessagePayload.substring(0, DB_CHAR_LIMIT) : inMessagePayload;
            integrationServiceMessage.setIsmMessagePayload(msg);

            //logMsg("inMessagePayload length: " + inMessagePayload.length());
            integrationServiceMessage.setIsmMessagePayloadBig(inMessagePayload);
            integrationServiceMessage.setIsmSeqNbr(new IntegrationServMessageSequenceProvider().getNextSequenceId());

            ScopeCoordinates scopeCoordinates = ContextHelper.getThreadUserContext().getScopeCoordinate();
            Long scopeLevel = ScopeCoordinates.GLOBAL_LEVEL;
            String scopeGkey = null;
            if (!scopeCoordinates.isScopeGlobal()) {
                scopeLevel = new Long(ScopeCoordinates.getScopeId(4));
                scopeGkey = (String) scopeCoordinates.getScopeLevelCoord(scopeLevel.intValue());
            }
            integrationServiceMessage.setIsmScopeGkey(scopeGkey);
            integrationServiceMessage.setIsmScopeLevel(scopeLevel);
            integrationServiceMessage.setIsmCreated(new Date());
            integrationServiceMessage.setIsmCreator(ContextHelper.getThreadUserId());
            HibernateApi.getInstance().save(integrationServiceMessage);

            logMsg("ISM sequenceNbr: " + integrationServiceMessage.getIsmSeqNbr());

        } catch (Exception e) {
            LOGGER.error("Exception in createIntegrationSrcMsg : " + e.getMessage());
            return null;
        }
        HibernateApi.getInstance().flush();

        return integrationServiceMessage;
    }


    public static class IntegrationServMessageSequenceProvider extends com.navis.argo.business.model.ArgoSequenceProvider {
        public Long getNextSequenceId() {
            return super.getNextSeqValue(serviceMsgSequence, (Long) ContextHelper.getThreadFacilityKey());
        }
        private String serviceMsgSequence = "DRAYMAN_SEQ";
    }

    private void logMsg(Object inMsg) {
        LOGGER.debug(inMsg);
    }

    private final String CONTAINER_MESSAGE = "<container%s " +
            "id=\"%s\">\n" +
            "<length>%s</length>\n" +
            "<weight>%s</weight>\n" +
            "<height>%s</height>\n" +
            "<loadStatus>%s</loadStatus>\n" +
            "<chassisPosition>%s</chassisPosition>\n" +
            "<custom1>%s</custom1>\n" +
            "<row>%s</row>\n" +
            "<bay>%s</bay>\n" +
            "<cell>%s</cell>\n" +
            "<tier>%s</tier>\n" +
            "<slot>%s</slot>\n" +
            "</container1>";

    private final String DRAYMAN_MESSAGE = "<draymanGate time=\"%s\" type=\"%s\">\n" +
            "<truck id=\"%s\">\n" +
            "<type>%s</type>\n" +
            "<tagID>%s</tagID>\n" +
            "<truckLicense>%s</truckLicense>\n" +
            "<state>%s</state>\n" +
            "</truck>\n" +
            "<tagException>\n" +
            "<errorReason>%s</errorReason>\n" +
            "<tec>%s</tec>\n" +
            "</tagException>\n" +
            "%s\n" +
            "<gate>\n" +
            "<type>%s</type>\n" +
            "<lane>%s</lane>\n" +
            "</gate>\n" +
            "</draymanGate>";


    private static final String T__L = "L";
    private static final String T_EMPTY = "";
    private static final String T__TIME = "stampDateTime";
    private static final String T__TRUCK_ID = "truckID";
    private static final String T__TRUCK_TYPE = "truckType";
    private static final String T__TAG_ID = "tagID";
    private static final String T__LICENCE_NBR = "truckLicenseNbr";
    private static final String T__LICENCE_STATE = "truckLicenseState";
    private static final String T__EX_ERR_REASON = "tExErrorReason";
    private static final String T__EX_TEC = "tExTec";
    private static final String T___TYPE = "type";
    private static final String T__LANE = "lane";
    private static final String T__INBOUND = "Inbound";
    private static final String T_YARD = "yard";

    private static final String T__ID = "id";
    private static final String T__LENGTH = "length";
    private static final String T__WEIGHT = "weight";
    private static final String T__HEIGHT = "height";
    private static final String T__LOAD_STATUS = "loadStatus";
    private static final String T__CHASSIS_POSITION = "chassisPosition";
    private static final String T__CUSTOM1 = "custom1";
    private static final String T__ROW = "row";
    private static final String T__BAY = "bay";
    private static final String T__CELL = "cell";
    private static final String T__TIER = "tier";
    private static final String T__SLOT = "slot";

    private static final String ABM_STACK = "ABM_STACK";
    private static final String ABM_SECTION = "ABM_SECTION";
    private static final String ABM_BLOCK = "ABM_BLOCK";

    private static final String T__TIP = "TIP";
    private static final String T__DRAYMAN = "Drayman";
    private static final String T__SUCCESS = "Success";
    private static final String T__FAILURE = "Failed";
    private static final int DB_CHAR_LIMIT = 3000;

    private static final String T__MSG_TYPE = "msgType";
    private static final String T__CNTR = "cntr";
    private static final String T__CANCEL = "CANCEL";

    private final String T__SITE_ARRIVAL = "SiteArrival";
    private final String T__SITE_DEPARTURE = "SiteDeparture";
    private final String T__PICKUP = "Pickup";


    private static final String T_JDBC_PREFIX = "jdbc:";
    private static final String T_KALMAR = "KALMAR";
    private static final String T_MTS = "MTS";
    private static final String MTS_GROOVY_NAME = "NavisUnitUpdateMTS";
    private static final String DRIVER_NAME = "net.sourceforge.jtds.jdbc.Driver";

    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMddHHmmss");

    private static final Logger LOGGER = Logger.getLogger(ITSDraymanGateAdaptor.class);
}