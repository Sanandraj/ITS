package henken

import com.navis.argo.ContextHelper
import com.navis.argo.business.integration.IntegrationServiceMessage
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.xps.model.Che
import com.navis.argo.business.xps.model.EcEvent
import com.navis.carina.integrationservice.business.IntegrationService
import com.navis.external.framework.util.ExtensionUtils
import com.navis.framework.business.atoms.IntegrationServiceDirectionEnum
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.moves.WorkInstruction
import com.navis.inventory.business.units.Unit
import com.navis.xpscache.business.atoms.EquipBasicLengthEnum
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import com.navis.argo.web.ArgoGuiMetafield
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChange
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.metafields.MetafieldId
import org.apache.log4j.Level
import org.apache.log4j.Logger


/**
 * @author <a href="mailto:sramasamy@weservetech.com"> Ramasamy Sathappan</a>
 * @since 08-Aug-2022
 * */
class ITSEcEventELI extends AbstractEntityLifecycleInterceptor {

    @Override
    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        notifyHKI(inEntity, inOriginalFieldChanges, inMoreFieldChanges);
    }

    private void notifyHKI(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        try {
            LOGGER.setLevel(Level.DEBUG);
            logMsg("notifyHKI - inEntity:: " + inEntity + ", inOriginalFieldChanges: " + inOriginalFieldChanges + ", inMoreFieldChanges: " + inMoreFieldChanges);
            String ecEventTypeDesc = (String) getNewFieldValue(inOriginalFieldChanges, ArgoGuiMetafield.ECEVENT_TYPE_DESCRIPTION);
            logMsg("ecEventTypeDesc: " + ecEventTypeDesc);

            String cheId, cheStatus, chePowName, transactionNumber, message;
            double MsgHighlight = 26;
            EcEvent ecEvent = (EcEvent) inEntity._entity;
            if (ecEvent) {
                cheId = ecEvent.getEceventCheName();
                chePowName = ecEvent.getEceventPowName();
                transactionNumber = ecEvent.getEceventGkey();
                transactionNumber = transactionNumber != null? transactionNumber : DEFAULT_TRANSACTION_NBR;
            }

            logMsg("cheId: " + cheId + ", cheStatus: " + cheStatus + ", chePowName: " + chePowName + ", transactionNumber: " + transactionNumber);
            // On LogOff the Che
            if (T_LGOF == ecEventTypeDesc) { // Logout
                //cheId = "T998";
                //chePool = "YARDB";
                //transactionNumber = "1234567890";
                message = frameMessage(buildFirstLine(cheId, T_UNAVAILABLE, chePowName), LINE_EMPTY, LINE_EMPTY, LINE_LOGGED_OUT, LINE_EMPTY, LINE_CALL_CLERK, LINE_EMPTY, LINE_EMPTY);

            } else if (T_UNAV == ecEventTypeDesc) { // Not available
                message = frameMessage(buildFirstLine(cheId, T_UNAVAILABLE, chePowName), LINE_EMPTY, LINE_EMPTY, LINE_CALL_CLERK, LINE_EMPTY, LINE_TO_LOG_IN, LINE_EMPTY, LINE_EMPTY);

                //Testing - to be removed
                List<WorkInstruction> wiList = WorkInstruction.findAssociatedWorkInstruction(ecEvent.getEceventWorkAssignmentGkey());
                logMsg("WI list: " + wiList);
                for (WorkInstruction wi : wiList) {
                    logMsg("fromPos: " + wi.getWiFromPosition() + ", toPos: " + wi.getWiToPosition() + ", wiPos: "+wi.getWiPosition());
                }

            } else if (T_IDLE == ecEventTypeDesc) { // Available
                message = frameMessage(buildFirstLine(cheId, null, chePowName), LINE_EMPTY, LINE_WAITING_FOR, LINE_EMPTY, LINE_JOB_INSTRUCTION, LINE_EMPTY, LINE_EMPTY, LINE_EMPTY);

             } else if (T_TVCO == ecEventTypeDesc || T_TRCO == ecEventTypeDesc) { // Truck assigned job 1,2
                List<WorkInstruction> wiList = WorkInstruction.findAssociatedWorkInstruction(ecEvent.getEceventWorkAssignmentGkey());
                logMsg("WI list: "+wiList);

                String fetchCheId, wiCarrierLocId, unitType;
                String line3 = LINE_EMPTY;
                String line5 = LINE_EMPTY;
                //WorkInstruction wi = !wiList.isEmpty()? wiList.get(0) : null;
                int counter = 0;
                for (WorkInstruction wi : wiList) {
                    counter++;
                    wiCarrierLocId = wi.getWiCarrierLocId();
                    Che fetchChe = wi.getMvhsFetchChe();
                    logMsg("fetchChe: "+fetchChe);
                    fetchCheId = fetchChe? fetchChe.getCheShortName() : T_FOUR_SPACE;
                    unitType = getEquipmentFeetType(wi);

                    if (counter == 1) {
                        line3 = String.format(LINE_GOTO, fetchCheId, unitType);
                    } else {
                        line5 = String.format(LINE_GOTO, fetchCheId, unitType);
                    }
                }
                logMsg("fetchCheId: " + fetchCheId + ", wiCarrierLocId: "+wiCarrierLocId + ", unitType: "+unitType);
                message = frameMessage(buildFirstLine(cheId, null, chePowName),
                        LINE_EMPTY,
                        wrapSpace(line3),
                        LINE_EMPTY,
                        wrapSpace(line5),
                        LINE_EMPTY,
                        LINE_EMPTY,
                        wrapSpace(String.format((T_TVCO == ecEventTypeDesc? LINE_VESSEL : LINE_RAIL), wiCarrierLocId)));


            } else if (T_TYCO == ecEventTypeDesc) { // Truck assigned job 3,4,5
                //yard position - bay.block
                //unit feet type
                // yard che
                List<WorkInstruction> wiList = WorkInstruction.findAssociatedWorkInstruction(ecEvent.getEceventWorkAssignmentGkey());
                logMsg("WI list: "+wiList);
                String fetchCheId, wiCarrierLocId, unitType;
                //WorkInstruction wi = !wiList.isEmpty()? wiList.get(0) : null;
                String line3 = LINE_EMPTY;
                String line4 = LINE_EMPTY;
                String line6 = LINE_EMPTY;
                String line7 = LINE_EMPTY;
                //WorkInstruction wi = !wiList.isEmpty()? wiList.get(0) : null;
                int counter = 0;
                for (WorkInstruction wi : wiList) {
                    counter++;
                    LocPosition position = wi.getWiFromPosition();
                    //blockName: F1, posName: Y-PIERG-F1.50.02.4, posSlot: F15002.4, posLocId: PIERG
                    logMsg("blockName: " + position.getBlockName() + ", PosSlot: " + position.getPosSlot() + ", PosLocId: " + position.getPosLocId() + ", PosName: " + position.getPosName());

                    wiCarrierLocId = wi.getWiCarrierLocId();
                    Che fetchChe = wi.getMvhsFetchChe();
                    logMsg("fetchChe: "+fetchChe);
                    fetchCheId = fetchChe? fetchChe.getCheShortName() : null;
                    unitType = getEquipmentFeetType(wi);

                    if (counter == 1) {
                        if (position.isWheeled() || position.isWheeledHeap()) {
                            line3 = String.format(LINE_GOTO_SLOT_WHEELED, formatPosition(position), (wi.getWiUfv().getUfvUnit().getUnitId()));
                        } else {
                            line3 = String.format(LINE_GOTO_SLOT, formatPosition(position), unitType);
                        }
                        line4 = fetchCheId? String.format(LINE_CHE, fetchCheId) : LINE_EMPTY;

                    } else {
                        if (position.isWheeled() || position.isWheeledHeap()) {
                            line6 = String.format(LINE_GOTO_SLOT_WHEELED, formatPosition(position), (wi.getWiUfv().getUfvUnit().getUnitId()));
                        } else {
                            line6 = String.format(LINE_GOTO_SLOT, formatPosition(position), unitType);
                        }
                        line7 = fetchCheId? String.format(LINE_CHE, fetchCheId) : LINE_EMPTY;
                    }
                }
                logMsg("fetchCheId: " + fetchCheId + ", wiCarrierLocId: "+wiCarrierLocId + ", unitType: "+unitType);
                message = frameMessage(buildFirstLine(cheId, null, chePowName),
                        LINE_EMPTY,
                        wrapSpace(line3),
                        wrapSpace(line4),
                        LINE_EMPTY,
                        wrapSpace(line6),
                        wrapSpace(line7),
                        LINE_EMPTY);

            } else if (T_AVCO == ecEventTypeDesc || T_ARCO == ecEventTypeDesc) { //Unladen at dest - 1,2
                List<WorkInstruction> wiList = WorkInstruction.findAssociatedWorkInstruction(ecEvent.getEceventWorkAssignmentGkey());
                logMsg("WI list: "+wiList);

                String fetchCheId, wiCarrierLocId, unitType;
                String line3 = LINE_EMPTY;
                String line5 = LINE_EMPTY;
                //WorkInstruction wi = !wiList.isEmpty()? wiList.get(0) : null;
                int counter = 0;
                for (WorkInstruction wi : wiList) {
                    counter++;
                    wiCarrierLocId = wi.getWiCarrierLocId();
                    Che fetchChe = wi.getMvhsFetchChe();
                    logMsg("fetchChe: "+fetchChe);
                    fetchCheId = fetchChe? fetchChe.getCheShortName() : T_FOUR_SPACE;
                    unitType = getEquipmentFeetType(wi);

                    if (counter == 1) {
                        line3 = String.format(LINE_WAIT_AT_FOR, fetchCheId, unitType);
                    } else {
                        line5 = String.format(LINE_WAIT_AT_FOR, fetchCheId, unitType);
                    }
                }
                logMsg("fetchCheId: " + fetchCheId + ", wiCarrierLocId: "+wiCarrierLocId + ", unitType: "+unitType);
                message = frameMessage(buildFirstLine(cheId, null, chePowName),
                        LINE_EMPTY,
                        wrapSpace(line3),
                        LINE_EMPTY,
                        wrapSpace(line5),
                        LINE_EMPTY,
                        LINE_EMPTY,
                        wrapSpace(String.format((T_AVCO == ecEventTypeDesc? LINE_VESSEL : LINE_RAIL), wiCarrierLocId)));

            } else if (T_AYCO == ecEventTypeDesc) { //Unladen at dest - 3,4,5
                List<WorkInstruction> wiList = WorkInstruction.findAssociatedWorkInstruction(ecEvent.getEceventWorkAssignmentGkey());
                logMsg("WI list: "+wiList);

                String fetchCheId, wiCarrierLocId, unitType;
                String line3 = LINE_EMPTY;
                String line4 = LINE_EMPTY;
                String line6 = LINE_EMPTY;
                String line7 = LINE_EMPTY;
                //WorkInstruction wi = !wiList.isEmpty()? wiList.get(0) : null;
                int counter = 0;
                for (WorkInstruction wi : wiList) {
                    counter++;
                    logMsg("fromPos: " + wi.getWiFromPosition() + ", toPos: " + wi.getWiToPosition());

                    LocPosition position = wi.getWiFromPosition();
                    wiCarrierLocId = wi.getWiCarrierLocId();
                    Che fetchChe = wi.getMvhsFetchChe();
                    logMsg("fetchChe: "+fetchChe);
                    fetchCheId = fetchChe? fetchChe.getCheShortName() : null;
                    unitType = getEquipmentFeetType(wi);

                    //blockName: F1, posName: Y-PIERG-F1.50.02.4, posSlot: F15002.4, posLocId: PIERG
                    logMsg("blockName: " + position.getBlockName() + ", PosSlot: " + position.getPosSlot() + ", PosLocId: " + position.getPosLocId() + ", PosName: " + position.getPosName());

                    if (counter == 1) {
                        if (position.isWheeled() || position.isWheeledHeap()) {
                            line3 = String.format(LINE_GOTO, formatPosition(position), unitType);
                        } else {
                            line3 = String.format(LINE_AT_FOR, formatPosition(position), unitType);
                        }
                        line4 = fetchCheId? String.format(LINE_CHE, fetchCheId) : LINE_EMPTY;

                    } else {
                        if (position.isWheeled() || position.isWheeledHeap()) {
                            line6 = String.format(LINE_GOTO, formatPosition(position), unitType);
                        } else {
                            line6 = String.format(LINE_AT_FOR, formatPosition(position), unitType);
                        }
                        line7 = fetchCheId? String.format(LINE_CHE, fetchCheId) : LINE_EMPTY;
                    }
                }
                logMsg("fetchCheId: " + fetchCheId + ", wiCarrierLocId: "+wiCarrierLocId + ", unitType: "+unitType);
                message = frameMessage(buildFirstLine(cheId, null, chePowName),
                        LINE_EMPTY,
                        wrapSpace(line3),
                        wrapSpace(line4),
                        LINE_EMPTY,
                        wrapSpace(line6),
                        wrapSpace(line7),
                        LINE_EMPTY);


            } else if (T_TVDR == ecEventTypeDesc || T_TRDR == ecEventTypeDesc) { //Laden to dest - 1,2
                List<WorkInstruction> wiList = WorkInstruction.findAssociatedWorkInstruction(ecEvent.getEceventWorkAssignmentGkey());
                logMsg("WI list: "+wiList);

                String fetchCheId, wiCarrierLocId, unitType;
                String line3 = LINE_EMPTY;
                String line5 = LINE_EMPTY;
                //WorkInstruction wi = !wiList.isEmpty()? wiList.get(0) : null;
                int counter = 0;
                for (WorkInstruction wi : wiList) {
                    counter++;
                    //LocPosition position = wi.getWiPosition();
                    wiCarrierLocId = wi.getWiCarrierLocId();
                    Che fetchChe = wi.getMvhsFetchChe();
                    logMsg("fetchChe: "+fetchChe);
                    fetchCheId = fetchChe? fetchChe.getCheShortName() : T_FOUR_SPACE;
                    unitType = getEquipmentFeetType(wi);

                    if (counter == 1) {
                        line3 = String.format(LINE_GOTO_WITH, fetchCheId, wi.getWiUfv().getUfvUnit().getUnitId());
                    } else {
                        line5 = String.format(LINE_GOTO_WITH, fetchCheId, wi.getWiUfv().getUfvUnit().getUnitId());
                    }
                }
                logMsg("fetchCheId: " + fetchCheId + ", wiCarrierLocId: "+wiCarrierLocId + ", unitType: "+unitType);
                message = frameMessage(buildFirstLine(cheId, null, chePowName),
                        LINE_EMPTY,
                        wrapSpace(line3),
                        LINE_EMPTY,
                        wrapSpace(line5),
                        LINE_EMPTY,
                        LINE_EMPTY,
                        wrapSpace(String.format((T_TVDR == ecEventTypeDesc? LINE_VESSEL : LINE_RAIL), wiCarrierLocId)));


            } else if (T_TYDR == ecEventTypeDesc) { //Laden to dest - 3,4
                List<WorkInstruction> wiList = WorkInstruction.findAssociatedWorkInstruction(ecEvent.getEceventWorkAssignmentGkey());
                logMsg("WI list: "+wiList);

                String putCheId, wiCarrierLocId, unitType;
                String line3 = LINE_EMPTY;
                String line4 = LINE_EMPTY;
                String line6 = LINE_EMPTY;
                String line7 = LINE_EMPTY;
                //WorkInstruction wi = !wiList.isEmpty()? wiList.get(0) : null;
                int counter = 0;
                Unit unit;
                for (WorkInstruction wi : wiList) {
                    counter++;
                    LocPosition position = wi.getWiPosition();
                    wiCarrierLocId = wi.getWiCarrierLocId();
                    Che putChe = wi.getMvhsPutChe();
                    logMsg("putChe: "+putChe);
                    putCheId = putChe? putChe.getCheShortName() : null;
                    unitType = getEquipmentFeetType(wi);
                    unit = wi.getWiUfv().getUfvUnit();

                    //blockName: F1, posName: Y-PIERG-F1.50.02.4, posSlot: F15002.4, posLocId: PIERG
                    logMsg("blockName: " + position.getBlockName() + ", PosSlot: " + position.getPosSlot() + ", PosLocId: " + position.getPosLocId() + ", PosName: " + position.getPosName());
                    if (counter == 1) {
                        line3 = String.format(LINE_TAKE, unit.getUnitId(), formatPosition(position), getUnitPod(unit));
                        if (position.isWheeled() || position.isWheeledHeap()) {
                            line4 = LINE_EMPTY;
                        } else {
                            line4 = putCheId? String.format(LINE_CHE, putCheId) : LINE_EMPTY;
                        }
                    } else {
                        line6 = String.format(LINE_TAKE, unit.getUnitId(), formatPosition(position), getUnitPod(unit));
                        if (position.isWheeled() || position.isWheeledHeap()) {
                            line7 = LINE_EMPTY;
                        } else {
                            line7 = putCheId? String.format(LINE_CHE, putCheId) : LINE_EMPTY;
                        }
                    }
                }
                logMsg("putCheId: " + putCheId + ", wiCarrierLocId: "+wiCarrierLocId + ", unitType: "+unitType);
                message = frameMessage(buildFirstLine(cheId, null, chePowName),
                        LINE_EMPTY,
                        wrapSpace(line3),
                        wrapSpace(line4),
                        LINE_EMPTY,
                        wrapSpace(line6),
                        wrapSpace(line7),
                        LINE_EMPTY);

            } else if (T_AVDR == ecEventTypeDesc || T_ARDR == ecEventTypeDesc) { // Laden at dest 1,2
                List<WorkInstruction> wiList = WorkInstruction.findAssociatedWorkInstruction(ecEvent.getEceventWorkAssignmentGkey());
                logMsg("WI list: "+wiList);
                String putCheId, wiContainerId, wiCarrierLocId, unitType;
                //WorkInstruction wi = !wiList.isEmpty()? wiList.get(0) : null;
                String line3 = LINE_EMPTY;
                String line5 = LINE_EMPTY;

                int counter = 0;
                for (WorkInstruction wi : wiList) {
                    counter++;
                    wiCarrierLocId = wi.getWiCarrierLocId();
                    wiContainerId = wi.getWiUfv().getUfvUnit().getUnitId();
                    Che putChe = wi.getMvhsPutChe();
                    logMsg("putChe: "+putChe);
                    putCheId = putChe? putChe.getCheShortName() : T_EMPTY;
                    unitType = getEquipmentFeetType(wi);

                    if (counter == 1) {
                        line3 = String.format(LINE_WAIT_AT_WITH, putCheId, wiContainerId);
                    } else {
                        line5 = String.format(LINE_WAIT_AT_WITH, putCheId, wiContainerId);
                    }
                }
                logMsg("fetchCheId: " + putCheId + ", wiCarrierLocId: "+wiCarrierLocId + ", wiContainerId: "+wiContainerId + ", unitType: "+unitType);
                message = frameMessage(buildFirstLine(cheId, null, chePowName),
                        LINE_EMPTY,
                        wrapSpace(line3),
                        LINE_EMPTY,
                        wrapSpace(line5),
                        LINE_EMPTY,
                        LINE_EMPTY,
                        wrapSpace(String.format((T_AVDR == ecEventTypeDesc? LINE_VESSEL : LINE_RAIL), wiCarrierLocId)));


            } else if (T_AYDR == ecEventTypeDesc) { // Laden at dest 4,5
                List<WorkInstruction> wiList = WorkInstruction.findAssociatedWorkInstruction(ecEvent.getEceventWorkAssignmentGkey());
                logMsg("WI list: "+wiList);
                LocPosition position;
                String putCheId, wiContainerId, wiCarrierLocId, unitType;
                String line3 = LINE_EMPTY;
                String line4 = LINE_EMPTY
                String line7 = LINE_EMPTY;
                String line8 = LINE_EMPTY;
                Unit unit;

                //WorkInstruction wi = !wiList.isEmpty()? wiList.get(0) : null;
                int counter = 0;
                for (WorkInstruction wi : wiList) {
                    counter++;
                    position = wi.getWiPosition();
                    //blockName: F1, posName: Y-PIERG-F1.50.02.4, posSlot: F15002.4, posLocId: PIERG
                    logMsg("blockName: " + position.getBlockName() + ", posName: " + position.getPosName() + ", posSlot: " + position.getPosSlot() + ", posLocId: " + position.getPosLocId());

                    unit = wi.getWiUfv().getUfvUnit();
                    wiCarrierLocId = wi.getWiCarrierLocId();
                    wiContainerId = unit.getUnitId();
                    Che putChe = wi.getMvhsPutChe();
                    logMsg("putChe: "+putChe);
                    putCheId = putChe ? putChe.getCheShortName() : null;
                    unitType = getEquipmentFeetType(wi);


                    if (counter == 1) {
                        if (position.isWheeled() || position.isWheeledHeap()) {
                            line3 = String.format(LINE_PARK, wiContainerId, (position? position.getBlockName() : T_EMPTY));
                            line4 = putCheId? String.format(LINE_CHE, putCheId) : LINE_EMPTY;
                        } else {
                            line3 = String.format(LINE_WAIT_AT, formatPosition(position), wiContainerId, getUnitPod(unit)); //POD
                            line4 = putCheId? String.format(LINE_CHE, putCheId) : LINE_EMPTY;
                        }
                    } else {
                        if (position.isWheeled() || position.isWheeledHeap()) {
                            line7 = String.format(LINE_PARK, wiContainerId, (position? position.getBlockName() : T_EMPTY));
                            line8 = putCheId? String.format(LINE_CHE, putCheId) : LINE_EMPTY;
                        } else {
                            line7 = String.format(LINE_WAIT_AT, formatPosition(position), wiContainerId, getUnitPod(unit)); //POD
                            line8 = putCheId? String.format(LINE_CHE, putCheId) : LINE_EMPTY;
                        }
                    }
                    logMsg("fetchCheId: " + putCheId + ", wiCarrierLocId: " + wiCarrierLocId + ", wiContainerId: " + wiContainerId + ", unitType: " + unitType);
                }

                message = frameMessage(buildFirstLine(cheId, null, chePowName),
                        LINE_EMPTY,
                        wrapSpace(line3), //MEM or HOU
                        wrapSpace(line4),
                        LINE_EMPTY,
                        LINE_EMPTY,
                        wrapSpace(line7),
                        wrapSpace(line8));
            }

            logMsg("message:\n"+message);
            if (message != null) {
                // Used for any parameters to send to XML-RPC Data
                Vector params = new Vector();
                params.addElement(message);
                params.addElement(MsgHighlight);
                params.addElement(cheId);
                params.addElement(transactionNumber);
                params.addElement(T_5000);

                XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
                config.setServerURL(new URL(SERVER_URL));

                XmlRpcClient client = new XmlRpcClient();
                client.setConfig(config);

                logMsg("params: "+params);

                def library = ExtensionUtils.getLibrary(ContextHelper.getThreadUserContext(), LIBRARY);
                logMsg("library: "+library);
                IntegrationServiceMessage ism;
                if (library) {
                    IntegrationService iServ = library.getUriFromIntegrationService(T__HKI, IntegrationServiceDirectionEnum.OUTBOUND);
                    if (iServ) {
                        ism = library.createIntegrationSrcMsg(iServ, params.toString(), cheId, ecEventTypeDesc, null);
                    }
                }

                Object result;
                try {
                    result = client.execute(HKI_API, params);
                } catch (Exception e) {
                    //result = "Connection timeout error occurred";
                    result = e.getMessage();
                    LOGGER.error("Exception while pushing the request : "+result);
                }
                logMsg("result: "+ (String)result);

                if (ism) {
                    String resultMessage = result? (String) result : T_EMPTY;
                    logMsg("resultMessage: "+resultMessage);
                    ism.setIsmUserString4(resultMessage);
                    HibernateApi.getInstance().save(ism);
                    HibernateApi.getInstance().flush();
                }
                logMsg("Result from Client : " + result);
            }

        } catch (Exception e) {
            LOGGER.error("Exception in notifyRTLS : "+e.getMessage());
        }
    }

    //Y-PIERG-F1.50.02.4
    private String formatPosition(LocPosition inLocPosition) {
        try {
            String inPosName;
            if (inLocPosition.isWheeled() || inLocPosition.isWheeledHeap())
                return inLocPosition.getBlockName()
            else
                inPosName = inLocPosition.getPosName()

            logMsg("inPosName: "+inPosName)
            String[] array = inPosName.split(T_HYPHEN)
            logMsg("array: "+array)
            if (array.size() > 2) {
                logMsg("array[2] : "+array[2])
                //array[2] : F1.50.02.4
                String[] posArray = (array[2]).split(T_DOT_SPLITTER)
                logMsg("posArray: "+posArray)
                if (posArray.size() > 1) {
                    return posArray[0] + T_DOT + posArray[1]
                }
            }
        } catch (Exception e) {
            LOGGER.error("Exception in formatPosition : "+e.getMessage())
        }
        return T_UNKNOWN
    }

    private String getUnitPod(Unit inUnit) {
        if (inUnit && inUnit.getUnitRouting() && inUnit.getUnitRouting().getRtgPOD1())
            return inUnit.getUnitRouting().getRtgPOD1().getPointId();
        else
            return T_EMPTY;
    }

    private String buildFirstLine(String inCheId, String inCheStatus, String inChePowName) {
        try {
            StringBuilder sbFirstLine = new StringBuilder();
            if (!inCheId.isEmpty()) {
                sbFirstLine.append(inCheId); // 4 chars
                sbFirstLine.append(T_FIVE_SPACE); // 5 chars
                if (inCheStatus != null)
                    sbFirstLine.append(inCheStatus); // 11 chars
                else
                    sbFirstLine.append(T_ELEVEN_SPACE);

                sbFirstLine.append(T_FIVE_SPACE); // 5 chars
                if (inChePowName != null)
                    sbFirstLine.append(inChePowName); // 5 chars
            }
            return wrapSpace(sbFirstLine.toString());

        } catch (Exception e) {
            LOGGER.error("Exception in buildFirstLine : "+e.getMessage());
        }
        return LINE_EMPTY;
    }

    private String frameMessage(String inLine1, String inLine2, String inLine3, String inLine4, String inLine5, String inLine6, String inLine7, String inLine8) {
        try {
            logMsg("frameMessage BEGIN : inLine1 : " + inLine1 + "::");
            logMsg("Line2: " + inLine2 + "::");
            logMsg("Line3: " + inLine3 + "::");
            logMsg("Line4: " + inLine4 + "::");
            logMsg("Line5: " + inLine5 + "::");
            logMsg("Line6: " + inLine6 + "::");
            logMsg("Line7: " + inLine7 + "::");
            logMsg("Line8: " + inLine8 + "::");

            /*StringBuilder sb = new StringBuilder();
            if (!inCheId.isEmpty()) {
                sb.append(inCheId); // 4 chars
                sb.append(T_FIVE_SPACE); // 5 chars
                if (inCheStatus != null)
                    sb.append(inCheStatus); // 11 chars
                else
                    sb.append(T_ELEVEN_SPACE);

                sb.append(T_FIVE_SPACE); // 5 chars
                if (inChePowName.length() > POW_SPACE_LIMIT) {
                    inChePowName = inChePowName.substring(0, POW_SPACE_LIMIT);
                }
                sb.append(inChePowName); // 5 chars

                if (inChePowName.length() < POW_SPACE_LIMIT) {
                    for (int i=POW_SPACE_LIMIT; i >= inChePowName.length(); i--) {
                        sb.append(T_SPACE);
                    }
                }
            }*/
            StringBuilder sb = new StringBuilder();
            sb.append(inLine1);
            sb.append(inLine2);
            sb.append(inLine3);
            sb.append(inLine4);
            sb.append(inLine5);
            sb.append(inLine6);
            sb.append(inLine7);
            sb.append(inLine8);
            logMsg("frameMessage END");

            return sb.toString();

        } catch (Exception e) {
            LOGGER.error("Exception in frameMessage : "+e.getMessage());
        }
        return null;
    }

    private String wrapSpace(String inMessage) {
        if (inMessage) {
            StringBuilder sb = new StringBuilder();
            sb.append(inMessage);
            for (int i = COLUMN_COUNT_LIMIT; i > inMessage.length(); i--) {
                sb.append(T_SPACE);
            }
            return sb.toString();
        } else {
            return LINE_EMPTY;
        }
    }

    private String getEquipmentFeetType(WorkInstruction inWorkInstruction) {
        EquipBasicLengthEnum basicLengthEnum = inWorkInstruction.getWiUfv().getUfvUnit().getBasicLength();
        String unitType = basicLengthEnum? basicLengthEnum.getKey() : T_EMPTY;
        if (!unitType.isEmpty())
            unitType = unitType.substring(5) + T_FT;
        return unitType;
    }

    private Object getNewFieldValue(EFieldChangesView inOriginalFieldChanges, MetafieldId inMetaFieldId) {
        EFieldChange fieldChange = inOriginalFieldChanges.findFieldChange(inMetaFieldId);
        return fieldChange ? fieldChange.getNewValue() : null;
    }

    private void logMsg(Object inMsg) {
        LOGGER.debug(inMsg);
    }

    private static final String DEFAULT_TRANSACTION_NBR = "1234567890";
    private static final String T_FT = "ft";
    private static final String T_EMPTY = "";
    private static final String T_UNKNOWN   = "UNKNOWN";
    private static final String T_FOUR_SPACE   = "    ";
    private static final String T_FIVE_SPACE   = "     ";
    private static final String T_ELEVEN_SPACE = "           ";
    private static final String T_SPACE = " ";
    private static final String T_DOT = ".";
    private static final String T_DOT_SPLITTER = "\\.";
    private static final String T_HYPHEN = "-";
    private static final String T_UNAVAILABLE = "UNAVAILABLE";
    private static final String LINE_EMPTY = "                              ";
    private static final String LINE_LOGGED_OUT   = "         LOGGED OUT           ";
    private static final String LINE_CALL_CLERK   = "         CALL CLERK           ";
    private static final String LINE_TO_LOG_IN    = "         TO LOG IN            ";
    private static final String LINE_CHE          = "CHE: %s                     ";
    private static final String LINE_WAITING_FOR  = "WAITING FOR                   ";
    private static final String LINE_WAIT_AT_WITH = "WAIT AT %s WITH %s";
    private static final String LINE_WAIT_AT      = "WAIT AT %s %s %s    "; //block, bay, container, MEM/HOU
    private static final String LINE_WAIT_AT_FOR  = "WAIT AT %s FOR %s   ";
    private static final String LINE_AT_FOR       = "AT %s FOR %s     ";
    private static final String LINE_PARK         = "PARK %s FOR %s     ";
    private static final String LINE_JOB_INSTRUCTION  = "JOB INSTRUCTION               ";

    private String LINE_GOTO      = "GOTO %s FOR %s";
    private String LINE_GOTO_SLOT = "GOTO %s.%s FOR %s";
    private String LINE_GOTO_SLOT_WHEELED = "GOTO %s FOR %s";
    private String LINE_GOTO_WITH = "GO TO %s WITH %s";
    //private String LINE_TAKE           = "TAKE %s TO %s.%s";
    private String LINE_TAKE           = "TAKE %s TO %s %s";
    private String LINE_VESSEL = "        VESSEL %s";
    private String LINE_RAIL   = "        RAIL %s";


    private static final String T_5000 = "5000";
    private static final String HKI_API = "ECN4XMLtoHKIMDT";

    private final int COLUMN_COUNT_LIMIT = 30;
    private final int ROW_COUNT_LIMIT = 8;
    private final int POW_SPACE_LIMIT = 5;

    private static final String SERVER_URL = "http://192.168.94.8:9003";
    private static final String T_LGOF = "LGOF";
    private static final String T_UNAV = "UNAV";
    private static final String T_IDLE = "IDLE";
    private static final String T_TRCO = "TRCO";
    private static final String T_TVCO = "TVCO";
    private static final String T_TYCO = "TYCO";
    private static final String T_TVDR = "TVDR";
    private static final String T_TYDR = "TYDR";
    private static final String T_TRDR = "TRDR";
    private static final String T_AVDR = "AVDR";
    private static final String T_ARDR = "ARDR";
    private static final String T_AYDR = "AYDR";
    private static final String T_AVCO = "AVCO";
    private static final String T_AYCO = "AYCO";
    private static final String T_ARCO = "ARCO";

    private static final String T__HKI = "HKI";
    private static final String LIBRARY = "ITSDraymanGateAdaptor";

    private static final Logger LOGGER = Logger.getLogger(ITSEcEventELI.class);

}
