package ITS

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.EquipClassEnum
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.Equipment
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.external.framework.util.ExtensionUtils
import com.navis.framework.business.Roastery
import com.navis.framework.query.common.api.QueryResult
import com.navis.framework.zk.util.JSONBuilder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.rail.business.entity.TrainVisitDetails
import com.navis.road.business.model.TruckVisitDetails
import com.navis.services.business.event.Event
import com.navis.services.business.event.EventFieldChange
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

import java.text.DateFormat
import java.text.SimpleDateFormat

/*
 * @Author <a href="mailto:annalakshmig@weservetech.com">ANNALAKSHMI G</a>
 * Date: 08/01/2022
 * Requirements:-  Returns Container Event Histories in JSON format for the requested containers.
 * Returns the most recent visit Arrival and Departure (if already  departed) events only. All in-yard events and past visit events
   will be excluded for default fetch.
   Returns all events including in-yard events and past visit events in case of FULL mode
 *  @Inclusion Location	: Incorporated as a code extension of the type TRANSACTED_BUSINESS_FUNCTION --> Paste this code (ITSGetContainerEventHistoryWSCallback.groovy)
 *
 */

class ITSGetContainerEventHistoryWSCallback extends AbstractExtensionPersistenceCallback {

    @Override
    void execute(@Nullable Map inMap, @Nullable Map outMap) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSGetContainerEventHistoryWSCallback :: start")
        String ctrNbr = inMap.containsKey(CONTAINER_NUMBERS) ? inMap.get(CONTAINER_NUMBERS) : null
        String mode = inMap.containsKey(FETCH_MODE) ? inMap.get(FETCH_MODE) : null
        outMap.put("RESPONSE", prepareContainerEventHistoryToITS(ctrNbr, mode))
    }

    String prepareContainerEventHistoryToITS(String ctrNumber, String fetchMode) {
        def library = ExtensionUtils.getLibrary(ContextHelper.getThreadUserContext(), "ITSBaseUtilLibrary");
        String errorMessage = library.validateMandatoryFields(ctrNumber)
        JSONBuilder mainObj = JSONBuilder.createObject();


        // boolean isFullMode = !StringUtils.isEmpty(fetchMode) && fetchMode.equalsIgnoreCase(FULL_MODE)
        boolean isFullMode = true
        if (errorMessage != null) {
            mainObj.put(ERROR_MESSAGE, _errorMessage)
        } else {
            String[] ctrNbrs = ctrNumber?.toUpperCase()?.split(",")*.trim()

            QueryResult rs = library.fetchUnitList(ctrNbrs, EquipClassEnum.CONTAINER)
            LOGGER.debug("rs count" + rs.getTotalResultCount())
            Map<Serializable, String> map = library.getUnitMap(rs, isFullMode)
            LOGGER.debug("map" + map)
            JSONBuilder jsonArray = JSONBuilder.createArray()
            for (Map.Entry<Serializable, String> entry : map.entrySet()) {
                boolean proceedFullMode = false;
                UnitFacilityVisit unitFacilityVisit = UnitFacilityVisit.hydrate(entry.getKey())
                if (unitFacilityVisit != null) {
                    Unit unit = unitFacilityVisit.getUfvUnit()
                    if (unit != null) {
                        List<Event> eventsList = library.getSpecificEventList(unit, isFullMode, EVENT_LIST)
                        LOGGER.debug("eventList" + eventsList?.toString())
                        CarrierVisit obCV = unitFacilityVisit.getUfvActualObCv()
                        CarrierVisit ibCV = unitFacilityVisit.getUfvActualIbCv()
                        StringBuilder sb = new StringBuilder()
                        StringBuilder stringBuilder = new StringBuilder()
                        String inBoundCarrier = null
                        String outBoundCarrier = null
                        if (ibCV != null) {
                            if (LocTypeEnum.TRUCK.equals(ibCV.getCvCarrierMode())) {
                                TruckVisitDetails truckVisitDetails = TruckVisitDetails.resolveFromCv(ibCV)
                                if (truckVisitDetails != null && truckVisitDetails.getTvdtlsTrkCompany() != null) {
                                    inBoundCarrier = sb.append("T-").append(truckVisitDetails.getTvdtlsTrkCompany().getBzuScac()).toString()
                                }
                            } else if (LocTypeEnum.TRAIN.equals(ibCV.getCvCarrierMode())) {
                                TrainVisitDetails trainVisitDetails = TrainVisitDetails.resolveTvdFromCv(ibCV)
                                if (trainVisitDetails != null && trainVisitDetails.getRvdtlsId() != null) {
                                    inBoundCarrier = sb.append("R-").append(trainVisitDetails.getRvdtlsId()).toString()
                                }
                            } else if (LocTypeEnum.VESSEL.equals(ibCV.getCvCarrierMode())) {
                                VesselVisitDetails vesselVisitDetails = VesselVisitDetails.resolveVvdFromCv(ibCV)
                                if (vesselVisitDetails != null && vesselVisitDetails.getVesselId() != null) {
                                    inBoundCarrier = sb.append("V-").append(vesselVisitDetails.getVesselId()).toString()
                                }
                            }
                        }
                        if (obCV != null) {
                            if (LocTypeEnum.TRUCK.equals(obCV.getCvCarrierMode())) {
                                TruckVisitDetails truckVisitDetails = TruckVisitDetails.resolveFromCv(obCV)
                                if (truckVisitDetails != null && truckVisitDetails.getTvdtlsTrkCompany() != null) {
                                    outBoundCarrier = stringBuilder.append("T-").append(truckVisitDetails.getTvdtlsTrkCompany().getBzuScac()).toString()
                                }
                            } else if (LocTypeEnum.TRAIN.equals(obCV.getCvCarrierMode())) {
                                TrainVisitDetails trainVisitDetails = TrainVisitDetails.resolveTvdFromCv(obCV)
                                if (trainVisitDetails != null && trainVisitDetails.getRvdtlsId() != null) {
                                    outBoundCarrier = stringBuilder.append("R-").append(trainVisitDetails.getRvdtlsId()).toString()
                                }
                            } else if (LocTypeEnum.VESSEL.equals(obCV.getCvCarrierMode())) {
                                VesselVisitDetails vesselVisitDetails = VesselVisitDetails.resolveVvdFromCv(obCV)
                                if (vesselVisitDetails != null && vesselVisitDetails.getVesselId() != null) {
                                    outBoundCarrier = stringBuilder.append("V-").append(vesselVisitDetails.getVesselId()).toString()
                                }
                            }
                        }
                        for (Event event : eventsList) {
                            String fromPosition = null
                            String toPosition = null
                            String carrier = null
                            boolean isValidEventToSend = false
                            if (event != null && event.getEventTypeId() != null) {
                                if (MOVE_EVENT_LIST.contains(event.getEventTypeId())) {
                                    isValidEventToSend = true
                                    if(EventEnum.UNIT_IN_GATE.getKey().equals(event.getEventTypeId()) || EventEnum.UNIT_IN_RAIL.getKey().equals(event.getEventTypeId()) || EventEnum.UNIT_IN_VESSEL.getKey().equals(event.getEventTypeId())){
                                        carrier = inBoundCarrier
                                    }else{
                                        carrier = outBoundCarrier
                                    }
                                } else {
                                    Set<EventFieldChange> fcList = (Set<EventFieldChange>) event.getFieldChanges()
                                    if (fcList != null && fcList.size() > 0) {
                                        for (EventFieldChange efc : fcList) {
                                            if (efc != null && "posName".equals(efc.getMetafieldId())) {
                                                isValidEventToSend = true
                                                fromPosition = efc.getPrevVal()
                                                toPosition = efc.getNewVal()
                                                if (EventEnum.UNIT_BRING_BACK_INTO_YARD.getKey().equals(event.getEventTypeId()) || EventEnum.UNIT_RECTIFY.getKey().equals(event.getEventTypeId())) {
                                                    carrier = ""
                                                } else {
                                                    if (!StringUtils.isEmpty(fromPosition) && !StringUtils.isEmpty(toPosition)) {
                                                        if (!fromPosition.startsWith("Y") && toPosition.startsWith("Y")) {
                                                            carrier = inBoundCarrier
                                                        } else if (fromPosition.startsWith("Y") && !toPosition.startsWith("Y")) {
                                                            carrier = outBoundCarrier
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (isValidEventToSend) {

                                    JSONBuilder jsonEventObject = JSONBuilder.createObject();
                                    //jsonEventObject.put("EventGkey", event.getEventGKey().toString())
                                    jsonEventObject.put(UNIT_ID, unitFacilityVisit.getUfvGkey())
                                    jsonEventObject.put(CONTAINER_NUMBER, unit.getUnitId())
                                    jsonEventObject.put(EVENT_CD, event.getEventTypeId() != null ? event.getEventTypeId() : EMPTY_STR)
                                    jsonEventObject.put(EVENT_DT_TM, event.getEvntAppliedDate() != null ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : EMPTY_STR)
                                    jsonEventObject.put(SHIPPING_LINE_CD, unit.getUnitLineOperator()?.getBzuId() != null ? unit.getUnitLineOperator().getBzuId() : EMPTY_STR)
                                    jsonEventObject.put(SHIPPING_LINE_SCAC, unit.getUnitLineOperator()?.getBzuScac() != null ? unit.getUnitLineOperator().getBzuScac() : EMPTY_STR)
                                    UnitCategoryEnum unitCategoryEnum = unit.getUnitCategory();
                                    String ctrStatusCd = EMPTY_STR
                                    String vesselVoyage = null
                                    if (unitCategoryEnum != null) {
                                        if (unitCategoryEnum.equals(UnitCategoryEnum.STORAGE))
                                            ctrStatusCd = EMPTY
                                        else {
                                            ctrStatusCd = unitCategoryEnum.getKey().substring(0, 3)
                                            if (unitCategoryEnum.equals(UnitCategoryEnum.EXPORT)) {
                                                if (obCV != null)
                                                    vesselVoyage = obCV.getCvId()
                                            } else if (unitCategoryEnum.equals(UnitCategoryEnum.IMPORT)) {
                                                if (ibCV != null)
                                                    vesselVoyage = ibCV.getCvId()
                                            }
                                        }
                                    }
                                    jsonEventObject.put(CONTAINER_STATUS_CD, ctrStatusCd)
                                    jsonEventObject.put(CARRIER, carrier != null ? carrier : EMPTY_STR)
                                    if (fromPosition != null) {
                                        jsonEventObject.put(FROM_POSITION, fromPosition)
                                    }
                                    if (toPosition != null) {
                                        jsonEventObject.put(TO_POSITION, toPosition)
                                    }

                                    jsonEventObject.put(CONTAINER_SZ_TP_HT, new StringBuilder().append(unit.getUnitEquipment()?.getEqEquipType()?.getEqtypNominalLength()?.getKey()?.substring(3, 5))
                                            .append(unit.getUnitEquipment()?.getEqEquipType()?.getEqtypIsoGroup()?.getKey())
                                            .append(unit.getUnitEquipment()?.getEqEquipType()?.getEqtypNominalHeight()?.getKey()?.substring(3, 5)).toString())

                                    if (vesselVoyage != null) {
                                        jsonEventObject.put(CARGO_VVC, vesselVoyage)
                                    }
                                    if (unit.getUnitRouting()?.getRtgPOL()?.getPointUnLoc()?.getUnlocId() != null) {
                                        jsonEventObject.put(POL_CD, unit.getUnitRouting().getRtgPOL().getPointUnLoc().getUnlocId())
                                    }
                                    if (unit.getUnitRouting()?.getRtgPOD1()?.getPointUnLoc()?.getUnlocId() != null) {
                                        jsonEventObject.put(POD_CD, unit.getUnitRouting().getRtgPOD1().getPointUnLoc().getUnlocId())
                                    }
                                    if (event.getEvntRelatedEntityId() != null) {
                                        Equipment equipment = Equipment.findEquipment(event.getEvntRelatedEntityId())
                                        if (equipment != null && EquipClassEnum.CHASSIS.equals(equipment.getEqClass())) {
                                            jsonEventObject.put(CHASSIS_NUMBER, event.getEvntRelatedEntityId())
                                        }
                                    }

                                    jsonArray.add(jsonEventObject)
                                }
                            }

                        }

                    }
                }
            }
            mainObj.put(CONTAINER_EVENT_HISTORIES, jsonArray)
        }
        return mainObj.toJSONString()
    }

    private static final String FULL_MODE = "FULL"
    private static final String CARRIER = "carrier"
    private static final String CONTAINER_NUMBER = "containerNumber"
    private static final String FROM_POSITION = "fromPosition"
    private static final String TO_POSITION = "toPosition"
    private static final String EVENT_CD = "eventCd"
    private static final String EVENT_DT_TM = "eventDtTm"
    private static final String SHIPPING_LINE_SCAC = "shippingLineScac"
    private static final String SHIPPING_LINE_CD = "shippingLineCd"
    private static final String CONTAINER_STATUS_CD = "containerStatusCd"
    private static final String CONTAINER_SZ_TP_HT = "containerSzTpHt"
    private static final String CARGO_VVC = "cargoVVC"
    private static final String POL_CD = "polCd"
    private static final String POD_CD = "podCd"
    private static final String CHASSIS_NUMBER = "chassisNumber"
    private static final String CONTAINER_NUMBERS = "containerNumbers"

    private static final String FETCH_MODE = "fetchMode"
    private static final String ERROR_MESSAGE = "errorMessage"
    private static final String CONTAINER_EVENT_HISTORIES = "containerEventHistories"
    private static final String UNIT_ID = "unitId"
    private static final String EMPTY = "MTY"
    private static final String EMPTY_STR = ""
    private static final String _errorMessage = "Missing required parameter : containerNumbers."
    private static final List<String> EVENT_LIST =
            [EventEnum.UNIT_IN_GATE.getKey(), /*EventEnum.UNIT_OUT_GATE.getKey(),*/ EventEnum.UNIT_LOAD.getKey(), /*EventEnum.UNIT_DISCH.getKey(),*/ EventEnum.UNIT_RAMP.getKey(), /*EventEnum.UNIT_DERAMP.getKey(), */ EventEnum.UNIT_IN_RAIL.getKey(), /*EventEnum.UNIT_OUT_RAIL.getKey(),*/ EventEnum.UNIT_IN_VESSEL.getKey(), /*EventEnum.UNIT_OUT_VESSEL.getKey(),*/ EventEnum.UNIT_DELIVER.getKey()/*, EventEnum.UNIT_RECEIVE.getKey()*/]


    private static final List<String> MOVE_EVENT_LIST = [EventEnum.UNIT_IN_GATE.getKey(), EventEnum.UNIT_OUT_GATE.getKey(),EventEnum.UNIT_IN_RAIL.getKey(), EventEnum.UNIT_OUT_RAIL.getKey(), EventEnum.UNIT_IN_VESSEL.getKey(),EventEnum.UNIT_OUT_VESSEL.getKey()]

    private static DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static Logger LOGGER = Logger.getLogger(this.class);
}
