import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.EquipNominalHeightEnum
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.integration.IntegrationServiceMessage
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.LocPosition
import com.navis.carina.integrationservice.business.IntegrationService
import com.navis.framework.IntegrationServiceField
import com.navis.framework.business.Roastery
import com.navis.framework.business.atoms.IntegrationServiceDirectionEnum
import com.navis.framework.business.atoms.MassUnitEnum
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.Disjunction
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.scope.ScopeCoordinates
import com.navis.framework.util.unit.UnitUtils
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

    // On cancelling transaction - call from TruckTransaction ELI
    public void prepareAndPushMessage(TruckTransaction inTruckTran, String inMessageType) {
        LOGGER.setLevel(Level.DEBUG)
        logMsg("prepareAndPushMessage END");
        List containerList = new ArrayList();
        containerList.add(getContainerDetailsMap(inTruckTran, null));

        Map chsMap = getChassisDetailsMap(inTruckTran);
        List chassisList;
        if (chsMap != null) {
            chassisList = new ArrayList();
            chassisList.add(chsMap);
        }
        frameAndSendMessage(inTruckTran.getTranTruckVisit(), inMessageType, containerList, chassisList);
        logMsg("prepareAndPushMessage END");
    }

    // On truck-In and On transaction-cancel
    public void prepareAndPushMessageForTvdtls(TruckVisitDetails inTvdtls, String inMessageType) {
        LOGGER.setLevel(Level.DEBUG)
        logMsg("prepareAndPushMessageForTvdtls ActiveTransactions - " + inTvdtls.getActiveTransactions());
        logMsg("prepareAndPushMessageForTvdtls CompletedTransactions - " + inTvdtls.getCompletedTransactions() + ", Tv status: " + inTvdtls.getTvdtlsStatus());

        Set transactionSet = inTvdtls.getActiveTransactions();
        if (transactionSet.isEmpty())
            transactionSet = inTvdtls.getCompletedTransactions();

        //int i = 0;
        List receivalContainerList = new ArrayList();
        List deliveryContainerList = new ArrayList();
        List receivalChassisList = new ArrayList();
        List deliveryChassisList = new ArrayList();
        for (TruckTransaction tran : transactionSet) {
            UnitFacilityVisit ufv = tran.getTranUnit() ? tran.getTranUnit().getUnitActiveUfvNowActive() : null;
            LocPosition locPosition = ufv ? ufv.getUfvLastKnownPosition() : null;
            locPosition = locPosition ? (locPosition.isYardPosition() ? locPosition : ufv.getFinalPlannedPosition()) : null;

            /*containerList.add(getContainerDetailsMap(tran, locPosition));
            if (inMessageType == null && i == 0) {
                inMessageType = tran.isReceival() ? T__SITE_ARRIVAL : T__PICKUP;
            }*/
            if (tran.isReceival()) {
                receivalContainerList.add(getContainerDetailsMap(tran, locPosition));
                receivalChassisList.add(getChassisDetailsMap(tran));
            } else {
                deliveryContainerList.add(getContainerDetailsMap(tran, locPosition));
                deliveryChassisList.add(getChassisDetailsMap(tran));
            }
        }

        if (T__SITE_DEPARTURE == inMessageType) {
            frameAndSendMessage(inTvdtls, T__SITE_DEPARTURE, deliveryContainerList, deliveryChassisList);

        } else {
            if (!receivalContainerList.isEmpty()) {
                frameAndSendMessage(inTvdtls, T__SITE_ARRIVAL, receivalContainerList, receivalChassisList);
            }
            if (!deliveryContainerList.isEmpty()) {
                //String type = (TruckVisitStatusEnum.COMPLETE == inTvdtls.getTvdtlsStatus())? T__SITE_DEPARTURE : T__PICKUP;
                frameAndSendMessage(inTvdtls, T__PICKUP, deliveryContainerList, deliveryChassisList);
            }
        }

        logMsg("prepareAndPushMessageForTvdtls END");
    }


    // On position / planned position update - Call from WI ELI
    public void prepareAndPushMessageForPositionChange(Unit inUnit, LocPosition inLocPosition) {
        LOGGER.setLevel(Level.DEBUG)
        logMsg("prepareAndPushMessageForPositionChange BEGIN ");
        TruckTransaction transaction = findTransactionsForUnitId(inUnit.getUnitId());
        TruckVisitDetails truckVisitDetails = transaction ? transaction.getTranTruckVisit() : null;
        String locPosition = (truckVisitDetails ? (truckVisitDetails.getTvdtlsPosition() ? truckVisitDetails.getTvdtlsPosition().getPosSlot() : null) : null);
        logMsg("locPosition: " + locPosition);
        if (truckVisitDetails && T__TIP.equals(locPosition)) {
            List containerList = new ArrayList();
            Map msgDetails = getGenericDetails(truckVisitDetails);
            if (msgDetails != null) { //position change
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

        return (transactions && transactions.size() > 0) ? transactions.get(0) : null;
    }

    private void frameAndSendMessage(TruckVisitDetails visitDetails, String msgType, List containerList, List chassisList) {
        //if (containerList.size() > 0) {
        Map msgDetails = getGenericDetails(visitDetails);
        logMsg("msgDetails: " + msgDetails);
        if (msgDetails != null) {
            msgDetails.put(T__MSG_TYPE, msgType);

            if (containerList == null || containerList.isEmpty())
                msgDetails.put(T__CNTR, null);
            else
                msgDetails.put(T__CNTR, containerList);

            if (chassisList == null || chassisList.isEmpty())
                msgDetails.put(T__CHASSIS, null);
            else
                msgDetails.put(T__CHASSIS, chassisList);

            createAndSendDraymanMessage(msgDetails);
        }
        //}
    }

    private Map getGenericDetails(TruckVisitDetails inTv) {
        Map genericMap = new HashMap();
        try {
            logMsg("getGenericDetails - BEGIN");
            GateLane tvdtlsEntryLane = inTv.getTvdtlsEntryLane();
            GateLane tvdtlsExitLane = inTv.getTvdtlsExitLane();
            if (tvdtlsEntryLane == null && tvdtlsExitLane == null)
                return null;

            String laneId = T_EMPTY;
            String laneType;
            if (tvdtlsExitLane != null) { // Exit lane
                laneId = tvdtlsExitLane.getLaneId();
                laneType = T__OUTBOUND;
            } else if ((tvdtlsEntryLane != null)) {  // Entry lane
                laneId = tvdtlsEntryLane.getLaneId();
                laneType = T__INBOUND;
            }

            Truck truck;
            if (ArgoUtils.isNotEmpty(inTv.getTvdtlsTruckId()))
                truck = Truck.findTruckById(inTv.getTvdtlsTruckId());
            if (truck == null)
                truck = inTv.getTvdtlsTruck();
            String truckLicenceState = truck ? truck.getTruckLicenseState() ? inTv.getTvdtlsTruck().getTruckLicenseState() : T_EMPTY : T_EMPTY

            genericMap.put(T__TIME, dateFormatter.format(new Date()));
            //genericMap.put(T__TRUCK_ID, inTv.getTvdtlsTruckLicenseNbr());
            genericMap.put(T__TRUCK_ID, inTv.getTvdtlsTruck() ? inTv.getTvdtlsTruck().getTruckId() : T_EMPTY);
            genericMap.put(T__TRUCK_TYPE, T__DRAYMAN);
            genericMap.put(T__TAG_ID, inTv.getTvdtlsTruck() ? inTv.getTvdtlsTruck().getTruckAeiTagId() : T_EMPTY);
            genericMap.put(T__LICENCE_NBR, inTv.getTvdtlsTruckLicenseNbr());
            genericMap.put(T__LICENCE_STATE, truckLicenceState);
            genericMap.put(T__EX_ERR_REASON, T_EMPTY);
            genericMap.put(T__EX_TEC, T_EMPTY);
            genericMap.put(T___TYPE, laneType); // Inbound or Outbound
            genericMap.put(T__LANE, laneId);    // lane Id
            logMsg("getGenericDetails - END");

        } catch (Exception e) {
            LOGGER.error("Exception in getGenericDetails : " + e.getMessage());
        }
        return genericMap;
    }

    private Map getChassisDetailsMap(TruckTransaction tran) {
        if (tran.getTranChassis() != null) {
            Map chassisDetails = new HashMap();
            chassisDetails.put(T__ID, tran.getTranChassis().getEqIdFull());
            return chassisDetails;
        }
        return null;
    }

    private Map getContainerDetailsMap(TruckTransaction tran, LocPosition locPos) {
        logMsg("getContainerDetailsMap - BEGIN : " + tran.getTranCtrNbr() + ", tranUnit: " + tran.getTranUnit());
        /*logMsg("appt: "+tran.getTranAppointment())
        Unit unit = tran.getTranUnit()? tran.getTranUnit() : tran.getTranAppointment().getGapptUnit();
        String unitId = unit? unit.getUnitId() : T_EMPTY;*/

        String containerNbr = tran.getTranCtrNbr() ? tran.getTranCtrNbr() : (tran.getTranUnit() ? tran.getTranUnit().getUnitId() : T_EMPTY);

        String eqLength = T_20;
        if (EquipBasicLengthEnum.BASIC40 == tran.getTranEqLength(EquipBasicLengthEnum.BASIC40)) {
            eqLength = T_40;
        }

        String eqWeight = tran.getTranCtrGrossWeight() ? tran.getTranCtrGrossWeight().toString() : T_EMPTY;
        logMsg("KG eqWeight : " + eqWeight)
        if (!eqWeight.isEmpty())
            eqWeight = String.format("%.2f", (UnitUtils.convertTo(Double.valueOf(eqWeight), MassUnitEnum.KILOGRAMS, MassUnitEnum.POUNDS)));
        logMsg("LB eqWeight : " + eqWeight)

        String eqHeight = getCntrHeight(tran.getTranEqoEqHeight());
        String chassisPos = tran.getTranCtrTruckPosition() ? tran.getTranCtrTruckPosition().toString() : T_EMPTY;
        /*if (T_EMPTY == chassisPos)
            chassisPos = "1";*/

        Map posValues = new HashMap();
        if (locPos == null) {
            posValues = getValueFromPosition(getCtrPosition(tran),eqLength);
        } else {
            posValues = getValueFromPosition(locPos, eqLength);
        }

        String loadStatus = T__L;
        if (tran.getTranUnit() != null && FreightKindEnum.MTY == tran.getTranUnit().getUnitFreightKind()) {
            loadStatus = T__E;
        }

        logMsg("frame container details")
        Map containerDetails = new HashMap();
        containerDetails.put(T__ID, containerNbr);
        containerDetails.put(T__LENGTH, eqLength);
        containerDetails.put(T__WEIGHT, eqWeight);
        containerDetails.put(T__HEIGHT, eqHeight);
        containerDetails.put(T__LOAD_STATUS, loadStatus);
        containerDetails.put(T__CHASSIS_POSITION, chassisPos);
        containerDetails.put(T__CUSTOM1, getCustom1(getPositionValue(posValues, T__CELL)));
        containerDetails.put(T__ROW, getPositionValue(posValues, T__ROW));
        containerDetails.put(T__BAY, getPositionValue(posValues, T__BAY));
        containerDetails.put(T__CELL, getPositionValue(posValues, T__CELL));
        containerDetails.put(T__TIER, getPositionValue(posValues, T__TIER));
        containerDetails.put(T__SLOT, getPositionValue(posValues, T__SLOT));

        logMsg("getContainerDetailsMap - END : " + containerDetails);
        return containerDetails;
    }

    private String getPositionValue(Map inPos, String inKey) {
        if (inPos)
            return inPos.get(inKey) ? inPos.get(inKey) : T_PERCENTILE;
        else
            return T_PERCENTILE;
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
        /*if (containerList.size() == 0)
            return message;*/

        logMsg("containerList: "+containerList);
        int iCount = 0;
        StringBuffer containerMessageSb = new StringBuffer();
        if (containerList != null) {
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
                                containerMap.get(T__SLOT),
                                String.valueOf(iCount))
                );
            }
        } else {
            //Add <container1/> dummy tag
            containerMessageSb.append(NO_CONTAINER1_MESSAGE);
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
            logMsg("iServ: " + iServ)
            ism = createIntegrationSrcMsg(iServ, inDraymanMessage, inMsgDetails.get(T__TAG_ID), inMsgDetails.get(T__MSG_TYPE));

            int timeOut = 4000;
            if (genRefDbConnection.getRefValue5() != null) {
                timeOut = genRefDbConnection.getRefValue5().toInteger();
            }

            try {
                int result = future.get(timeOut, TimeUnit.MILLISECONDS);
                logMsg("result : " + result);
                logMsg("ism: " + ism)
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
        String retValue = T_PERCENTILE;
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
        UnitFacilityVisit ufv = unit ? unit.getUnitActiveUfv() : null;
        if (ufv) {
            GateStageTypeEnum gateStage = this.getGateStageType(inTran);
            logMsg("gateStage: " + gateStage)
            if (gateStage == GateStageTypeEnum.IN) {
                logMsg("isReceival: " + inTran.isReceival())
                if (inTran.isReceival()) {
                    locPosition = ufv.getFinalPlannedPosition();
                    if (locPosition == null && inTran.getTranCtrPosition() != null) {
                        locPosition = inTran.getTranCtrPosition().isYardPosition() ? inTran.getTranCtrPosition() : null
                    }
                } else { // for delivery
                    locPosition = ufv.getUfvLastKnownPosition();
                }
            } else {
                locPosition = ufv.getUfvLastKnownPosition();
            }
        }
        logMsg("locPosition: " + locPosition)
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

    /*private AbstractYardBlock getAbstractYardBlock(Serializable gkey) {
        DomainQuery dq = QueryUtils.createDomainQuery("AbstractYardBlock")
        dq.addDqPredicate(PredicateFactory.eq(BinField.ABN_PARENT_BIN, gkey))
        dq.addDqPredicate(PredicateFactory.eq(BinField.ABN_SUB_TYPE, "SBL"))
        dq.setFilter(ObsoletableFilterFactory.createShowActiveFilter())
        dq.addDqOrdering(Ordering.asc(BinField.ABN_NAME))
        List<AbstractYardBlock> list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        if (list != null && !list.isEmpty())
            return list.get(0);
        else
            return null;
    }*/


    private Map getValueFromPosition(LocPosition locPosition, String eqLength) {
        Map posValues = new HashMap();
        logMsg("loc PosSlot: " + locPosition.getPosSlot() + ", PosLocId: "+locPosition.getPosLocId() + ", BlockName: " + locPosition.getBlockName() + ", posBin: "+locPosition.getPosBin()+", posName: "+locPosition.getPosName());

        //loc PosSlot: E130032, PosLocId: PIERG, BlockName: null, posBin: null, posName: Y-PIERG-E130032
        //AbstractYardBlock
        /*AbstractYardBlock abstractYardBlock = getAbstractYardBlock((ContextHelper.getThreadYard()).getYrdBinModel().getAbnGkey());
        logMsg("full label: " + abstractYardBlock.getAyblkLabelUIFullPosition());

        // B2R2C2T1
        // B63607.1
        String fullPosition = abstractYardBlock.getAyblkLabelUIFullPosition();
        logMsg("fullPosition: "+fullPosition);

        int startIndex = 0, endIndex = 0;
        if (fullPosition.size() > 1) {
            startIndex = Integer.parseInt(fullPosition.substring(1, 2));
        }
        if (fullPosition.size() > 3) {
            endIndex = Integer.parseInt(fullPosition.substring(3, 4));
        }
        //logMsg("startIndex: "+startIndex + ", endIndex: "+endIndex);
        (inPosSlot.length() > endIndex) ? inPosSlot.substring(startIndex, startIndex + endIndex) : null;*/

        posValues.put(T__ROW, T_PERCENTILE);
        posValues.put(T__BAY, T_PERCENTILE);
        posValues.put(T__CELL, T_PERCENTILE);
        posValues.put(T__TIER, T_PERCENTILE);
        posValues.put(T__SLOT, T_PERCENTILE);

        if (locPosition != null) {
            boolean hasValuesAssigned = retrievePosition(locPosition, posValues, eqLength);

            //Y-PIERG-E4.01.05.5
            if (!hasValuesAssigned && T_EMPTY == (String) posValues.get(T__ROW) && T_EMPTY == (String) posValues.get(T__BAY) && locPosition && !locPosition.toString().isEmpty()) {
                String[] locArray = locPosition.toString().split(T_HYPHEN);
                if (locArray.size() > 2) {
                    String[] slotArray = locArray[2].split(T_DOT);
                    logMsg("slotArray: "+slotArray)

                    if (slotArray.size() > 0)
                        posValues.put(T__ROW, slotArray[0]);
                    if (slotArray.size() > 1)
                        posValues.put(T__BAY, slotArray[1]);
                    if (slotArray.size() > 2)
                        posValues.put(T__CELL, slotArray[2]);
                    if (slotArray.size() > 3)
                        posValues.put(T__TIER, slotArray[3]);
                }
            }

            if (locPosition.isGrounded())
                posValues.put(T__TIER, locPosition.getPosTier());
        }
        logMsg("posValues: " + posValues);
        return posValues;
    }


    //posValues: [tier:1, row:B6, bay:65, slot:%, cell:20]
    private boolean retrievePosition(LocPosition position, Map posValues, String eqLength) {
        String rowVal = null;
        String slotVal = null;
        String blockVal = null;
        AbstractBin stackBin = position.getPosBin();
        logMsg("stackBin: " + stackBin)
        if (stackBin != null) {
            if (ABM_STACK.equalsIgnoreCase(stackBin.getAbnBinType().getBtpId())) {
                //logMsg("In ABM-Stack")
                String stackBinName = stackBin.getAbnName()
                AbstractBin sectionBin = stackBin.getAbnParentBin();
                if (sectionBin != null && ABM_SECTION.equalsIgnoreCase(sectionBin.getAbnBinType().getBtpId())) {
                    //logMsg("In ABM_SECTION")
                    String sectionBinName = sectionBin.getAbnName()
                    rowVal = sectionBinName;
                    slotVal = stackBinName.substring(stackBinName.indexOf(sectionBinName) + sectionBinName.size())
                    AbstractBin blockBin = sectionBin.getAbnParentBin();
                    if (!position.isWheeled() && blockBin != null && ABM_BLOCK.equalsIgnoreCase(blockBin.getAbnBinType().getBtpId())) {
                        //logMsg("set blockVal")
                        String blockBinName = blockBin.getAbnName()
                        blockVal = blockBinName;
                        rowVal = sectionBinName.substring(sectionBinName.indexOf(blockBinName) + blockBinName.size());
                    }
                }
                if (position.isWheeled()) {
                    //logMsg("isWheeled")
                    posValues.put(T__ROW, rowVal);
                    posValues.put(T__SLOT, slotVal);
                } else {
                    //logMsg("non-wheeled pos")
                    posValues.put(T__ROW, blockVal);
                    //If cntr is 40 ft, then bay should be even. If its 20ft, bay should be odd.
                    //posValues.put(T__BAY, rowVal);
                    if (rowVal) {
                        if (T_40 == eqLength && Integer.parseInt(rowVal)%2 != 0) //If 40' is odd, then change to even bay
                            posValues.put(T__BAY, String.valueOf(Integer.parseInt(rowVal) + 1));
                        else
                            posValues.put(T__BAY, rowVal);
                    }
                    posValues.put(T__CELL, slotVal);
                }
                return true;

            } else if (position.isWheeledHeap() || ABM_BLOCK.equalsIgnoreCase(stackBin.getAbnBinType().getBtpId())) {
                //logMsg("else block")
                posValues.put(T__ROW, stackBin.getAbnName());
                return true;
            }
        }
        logMsg("retrievePosition - posValues: " + posValues)
        return false;
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

    public IntegrationServiceMessage createIntegrationSrcMsg(IntegrationService inIntegrationService, String inMessagePayload, String truckTagId, String messageType) {
        createIntegrationSrcMsg(inIntegrationService, inMessagePayload, truckTagId, messageType, null);
    }

    public IntegrationServiceMessage createIntegrationSrcMsg(IntegrationService inIntegrationService, String inMessagePayload, String truckTagId, String messageType, String responseMessage) {
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

            if (responseMessage) {
                integrationServiceMessage.setIsmUserString4(responseMessage);
            }
            /*if (inEventGkey) {
                integrationServiceMessage.setIsmUserString3(inEventGkey);
            }*/

            String msg = inMessagePayload.length() > DB_CHAR_LIMIT ? inMessagePayload.substring(0, DB_CHAR_LIMIT) : inMessagePayload;
            integrationServiceMessage.setIsmMessagePayload(msg);

            //logMsg("inMessagePayload length: " + inMessagePayload.length());
            integrationServiceMessage.setIsmMessagePayloadBig(inMessagePayload);

            if (T__DRAYMAN.equals(inIntegrationService.getIntservName())) {
                integrationServiceMessage.setIsmSeqNbr(new IntegrationServMessageDraymanSequenceProvider().getNextSequenceId());
            } else if (T__HKI.equals(inIntegrationService.getIntservName())) {
                integrationServiceMessage.setIsmSeqNbr(new IntegrationServMessageHKISequenceProvider().getNextSequenceId());
            }

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


    public static class IntegrationServMessageDraymanSequenceProvider extends com.navis.argo.business.model.ArgoSequenceProvider {
        public Long getNextSequenceId() {
            return super.getNextSeqValue(serviceMsgSequence, (Long) ContextHelper.getThreadFacilityKey());
        }
        private String serviceMsgSequence = "DRAYMAN_SEQ";
    }

    public static class IntegrationServMessageHKISequenceProvider extends com.navis.argo.business.model.ArgoSequenceProvider {
        public Long getNextSequenceId() {
            return super.getNextSeqValue(serviceMsgSequence, (Long) ContextHelper.getThreadFacilityKey());
        }
        private String serviceMsgSequence = "HKI_SEQ";
    }

    private void logMsg(Object inMsg) {
        LOGGER.debug(inMsg);
    }

    private final String NO_CONTAINER1_MESSAGE = "<container1/>";

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
            "</container%s>";

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


    private static final String T__E = "E";
    private static final String T__L = "L";
    private static final String T_EMPTY = "";
    private static final String T_HYPHEN = "-";
    private static final String T_DOT = ".";
    private static final String T_PERCENTILE = "%";
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
    private static final String T__OUTBOUND = "Outbound";
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
    private static final String T_20 = "20";
    private static final String T_40 = "40";

    private static final String T__TIP = "TIP";
    private static final String T__DRAYMAN = "Drayman";
    private static final String T__SUCCESS = "Success";
    private static final String T__FAILURE = "Failed";
    private static final int DB_CHAR_LIMIT = 3000;

    private static final String T__MSG_TYPE = "msgType";
    private static final String T__CNTR = "cntr";
    private static final String T__CHASSIS = "chassis";
    private static final String T__CANCEL = "CANCEL";

    private final String T__SITE_ARRIVAL = "SiteArrival";
    private final String T__SITE_DEPARTURE = "SiteDeparture";
    private final String T__PICKUP = "Pickup";

    private static final String T__HKI = "HKI";

    private static final String T_JDBC_PREFIX = "jdbc:";
    private static final String T_KALMAR = "KALMAR";
    private static final String T_MTS = "MTS";
    private static final String MTS_GROOVY_NAME = "NavisUnitUpdateMTS";
    private static final String DRIVER_NAME = "net.sourceforge.jtds.jdbc.Driver";

    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMddHHmmss");

    private static final Logger LOGGER = Logger.getLogger(ITSDraymanGateAdaptor.class);
}