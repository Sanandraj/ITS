import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.EquipClassEnum
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.external.framework.util.ExtensionUtils
import com.navis.framework.business.Roastery
import com.navis.framework.query.common.api.QueryResult
import com.navis.framework.zk.util.JSONBuilder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.sun.jmx.snmp.ThreadContext
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

        boolean isFullMode = !StringUtils.isEmpty(fetchMode) && fetchMode.equalsIgnoreCase(FULL_MODE)
        if (errorMessage != null) {
            mainObj.put(ERROR_MESSAGE, _errorMessage)
        } else {
            String[] ctrNbrs = ctrNumber?.toUpperCase()?.split(",")*.trim()

            QueryResult rs = library.fetchUnitList(ctrNbrs, EquipClassEnum.CONTAINER)
            LOGGER.debug("rs count" +rs.getTotalResultCount())
            Map<Serializable, String> map = library.getUnitMap(rs, isFullMode)
            LOGGER.debug("map" +map)
            JSONBuilder jsonArray = JSONBuilder.createArray()
            for (Map.Entry<Serializable, String> entry : map.entrySet()) {
                Map<String, String> carrierParms = new HashMap<>()
                boolean proceedFullMode = false;
                UnitFacilityVisit unitFacilityVisit = UnitFacilityVisit.hydrate(entry.getKey())
                if (unitFacilityVisit != null) {
                    Unit unit = unitFacilityVisit.getUfvUnit()
                    if (unit != null) {
                        List<Event> eventsList = library.getSpecificEventList(unit, isFullMode, EVENT_LIST)
                        if (isFullMode) {
                            for (Event evt : eventsList) {
                                if (EVENT_LIST_START.contains(evt.getEventTypeId())) {
                                    proceedFullMode = true;
                                    break
                                }
                            }
                        }
                        // skip the container record if a container record dint have visit event incase of full mode
                        if(isFullMode && !proceedFullMode){
                            continue
                        }
                        for (Event event : eventsList) {
                            if (event != null /*&& (!isFullMode || (isFullMode && proceedFullMode))*/) {
                                JSONBuilder jsonEventObject = JSONBuilder.createObject();
                                jsonEventObject.put(CONTAINER_NUMBER, unit.getUnitId())
                                jsonEventObject.put(EVENT_CD, event.getEventTypeId() != null ? event.getEventTypeId() : EMPTY_STR)
                                jsonEventObject.put(EVENT_DT_TM, event.getEvntAppliedDate() != null ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : EMPTY_STR)
                                jsonEventObject.put(SHIPPING_LINE_SCAC, unit.getUnitLineOperator()?.getBzuId() != null ? unit.getUnitLineOperator().getBzuId() : EMPTY_STR)
                                UnitCategoryEnum unitCategoryEnum = unit.getUnitCategory();
                                String ctrStatusCd = EMPTY_STR
                                String vesselVoyage = null
                                if (unitCategoryEnum != null) {
                                    if (unitCategoryEnum.equals(UnitCategoryEnum.STORAGE))
                                        ctrStatusCd = EMPTY
                                    else {
                                        ctrStatusCd = unitCategoryEnum.getKey().substring(0, 3)
                                        if (unitCategoryEnum.equals(UnitCategoryEnum.EXPORT)) {
                                            if (unitFacilityVisit.getUfvActualObCv() != null && unitFacilityVisit.getUfvActualObCv().getCarrierObVoyNbrOrTrainId() != null && unitFacilityVisit.getUfvActualObCv().getCarrierVehicleName() != null)
                                                vesselVoyage = new StringBuilder().append(unitFacilityVisit.getUfvActualObCv().getCarrierVehicleName()).append(" ")
                                                        .append(unitFacilityVisit.getUfvActualObCv().getCarrierObVoyNbrOrTrainId()).toString()
                                        } else if (unitCategoryEnum.equals(UnitCategoryEnum.IMPORT)) {
                                            if (unitFacilityVisit.getUfvActualIbCv() != null && unitFacilityVisit.getUfvActualIbCv().getCarrierIbVoyNbrOrTrainId() != null && unitFacilityVisit.getUfvActualIbCv().getCarrierVehicleName() != null)
                                                vesselVoyage = new StringBuilder().append(unitFacilityVisit.getUfvActualIbCv().getCarrierVehicleName()).append(" ")
                                                        .append(unitFacilityVisit.getUfvActualIbCv().getCarrierIbVoyNbrOrTrainId()).toString()
                                        }
                                    }
                                }
                                jsonEventObject.put(CONTAINER_STATUS_CD, ctrStatusCd)
                                carrierParms = getCarrierParams(event.getEventTypeId(), unitFacilityVisit)
                                jsonEventObject.put(CONTAINER_SZ_TP_HT, new StringBuilder().append(unit.getUnitEquipment()?.getEqEquipType()?.getEqtypNominalLength()?.getKey()?.substring(3, 5))
                                        .append(unit.getUnitEquipment()?.getEqEquipType()?.getEqtypIsoGroup()?.getKey())
                                        .append(unit.getUnitEquipment()?.getEqEquipType()?.getEqtypNominalHeight()?.getKey()?.substring(3, 5)).toString())
                                jsonEventObject.put(CARRIER_TYPE_CD, carrierParms.get(CARRIER_TYPE_CD) != null ? carrierParms.get(CARRIER_TYPE_CD) : EMPTY_STR)
                                jsonEventObject.put(CARRIER_CD, carrierParms.get(CARRIER_CD) != null ? carrierParms.get(CARRIER_CD) : EMPTY_STR)
                                // optional
                                if (carrierParms.get(POSITION) != null) {
                                    jsonEventObject.put(POSITION, carrierParms.get(POSITION))
                                }
                                if (vesselVoyage != null) {
                                    jsonEventObject.put(CARGO_VVC, vesselVoyage)
                                }
                                if (unit.getUnitRouting()?.getRtgPOL()?.getPointUnLoc()?.getUnlocId() != null) {
                                    jsonEventObject.put(POL_CD, unit.getUnitRouting().getRtgPOL().getPointUnLoc().getUnlocId())
                                }
                                if (unit.getUnitRouting()?.getRtgPOD1()?.getPointUnLoc()?.getUnlocId() != null) {
                                    jsonEventObject.put(POD_CD, unit.getUnitRouting().getRtgPOD1().getPointUnLoc().getUnlocId())
                                }
                                if (unit.getUnitCarriageUnit()?.getUnitId() != null) {
                                    jsonEventObject.put(CHASSIS_NUMBER, unit.getUnitCarriageUnit().getUnitId())
                                }
                                jsonArray.add(jsonEventObject)

                            }
                            if (EVENT_LIST_START.contains(event.getEventTypeId())) {
                                break
                            }
                        }

                    }
                }
            }
            mainObj.put(CONTAINER_EVENT_HISTORIES, jsonArray)

        }
        return mainObj.toJSONString()
    }

    private Map<String, String> getCarrierParams(String evtId, UnitFacilityVisit ufv) {
        Map<String, String> carrierParams = new HashMap<>()
        String carrierTypeCode = null
        String carrierCd = null;
        String position = null;
        switch (evtId) {
            case EventEnum.UNIT_IN_GATE.getKey():
            case EventEnum.UNIT_RECEIVE.getKey():
                carrierTypeCode = TRUCK
                carrierCd = ufv.getUfvActualIbCv()?.getCvOperator()?.getBzuId()
                break;
            case EventEnum.UNIT_OUT_GATE.getKey():
            case EventEnum.UNIT_DELIVER.getKey():
                carrierTypeCode = TRUCK
                carrierCd = ufv.getUfvActualObCv()?.getCvOperator()?.getBzuId()
                break;
            case EventEnum.UNIT_IN_VESSEL.getKey():
            case EventEnum.UNIT_DISCH.getKey():
                carrierTypeCode = VESSEL
                carrierCd = ufv.getUfvActualIbCv()?.getCvOperator()?.getBzuId()
                position = ufv.getUfvArrivePosition()?.getPosSlot()
                break;
            case EventEnum.UNIT_OUT_VESSEL.getKey():
            case EventEnum.UNIT_LOAD.getKey():
                carrierTypeCode = VESSEL
                carrierCd = ufv.getUfvActualObCv()?.getCvOperator()?.getBzuId()
                position = ufv.getUfvLastKnownPosition()?.getPosSlot()
                break;
            case EventEnum.UNIT_IN_RAIL.getKey():
            case EventEnum.UNIT_DERAMP.getKey():
                carrierTypeCode = RAIL
                carrierCd = ufv.getUfvActualIbCv()?.getCvOperator()?.getBzuId()
                position = ufv.getUfvArrivePosition()?.getPosSlot()
                break;
            case EventEnum.UNIT_OUT_RAIL.getKey():
            case EventEnum.UNIT_RAMP.getKey():
                carrierTypeCode = RAIL
                carrierCd = ufv.getUfvActualObCv()?.getCvOperator()?.getBzuId()
                position = ufv.getUfvLastKnownPosition()?.getPosSlot()
                break;
            default:
                carrierTypeCode = YARD
                carrierCd = ContextHelper.getThreadYard().getId()
                position = ufv.getUfvLastKnownPosition()?.getPosSlot()
        }
        carrierParams.put(CARRIER_TYPE_CD, carrierTypeCode)
        carrierParams.put(CARRIER_CD, carrierCd)
        carrierParams.put(POSITION, position)
        return carrierParams;
    }
    private static final String FULL_MODE = "FULL"
    private static final String CARRIER_TYPE_CD = "carrierTypeCd"
    private static final String CARRIER_CD = "carrierCd"
    private static final String POSITION = "position"
    private static final String CONTAINER_NUMBER = "containerNumber"
    private static final String EVENT_CD = "eventCd"
    private static final String EVENT_DT_TM = "eventDtTm"
    private static final String SHIPPING_LINE_SCAC = "shippingLineScac"
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
    private static final String EMPTY = "MTY"
    private static final String TRUCK = "T"
    private static final String RAIL = "R"
    private static final String VESSEL = "V"
    private static final String YARD = "Y"
    private static final String EMPTY_STR = ""
    private static final String _errorMessage = "Missing required parameter : containerNumbers."
    private static final List<String> EVENT_LIST =
            [EventEnum.UNIT_IN_GATE.getKey(), EventEnum.UNIT_OUT_GATE.getKey(), EventEnum.UNIT_LOAD.getKey(), EventEnum.UNIT_DISCH.getKey(), EventEnum.UNIT_RAMP.getKey(), EventEnum.UNIT_DERAMP.getKey(), EventEnum.UNIT_IN_RAIL.getKey(), EventEnum.UNIT_OUT_RAIL.getKey(), EventEnum.UNIT_IN_VESSEL.getKey(), EventEnum.UNIT_OUT_VESSEL.getKey(), EventEnum.UNIT_DELIVER.getKey(), EventEnum.UNIT_RECEIVE.getKey()]
    private static final List<String> EVENT_LIST_START =
            [EventEnum.UNIT_IN_GATE.getKey(), EventEnum.UNIT_IN_RAIL.getKey(), EventEnum.UNIT_IN_VESSEL.getKey()]
    private static ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
    private static DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("YYYY-MM-DD'T'HH:mm:ss");
    private static Logger LOGGER = Logger.getLogger(this.class);
}
