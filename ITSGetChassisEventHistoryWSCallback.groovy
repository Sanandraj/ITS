import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.EquipClassEnum
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.external.framework.util.ExtensionUtils
import com.navis.framework.query.common.api.QueryResult
import com.navis.framework.zk.util.JSONBuilder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

import java.text.DateFormat
import java.text.SimpleDateFormat

/*
 * @Author <a href="mailto:annalakshmig@weservetech.com">ANNALAKSHMI G</a>
 * Date: 08/01/2022
 * Requirements:-  Returns Chassis Event Histories in JSON format for the requested chassis.
 * Returns the most recent visit Arrival and Departure (if already  departed) events only. All in-yard events and past visit events
   will be excluded for default fetch.
   Returns all events including in-yard events and past visit events in case of FULL mode
 *  @Inclusion Location	: Incorporated as a code extension of the type TRANSACTED_BUSINESS_FUNCTION --> Paste this code (ITSGetChassisEventHistoryWSCallback.groovy)
 *
 */

class ITSGetChassisEventHistoryWSCallback extends AbstractExtensionPersistenceCallback {

    @Override
    void execute(@Nullable Map inMap, @Nullable Map outMap) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSGetChassisEventHistoryWSCallback :: start")
        String chassisNbr = inMap.containsKey(CHASSIS_NUMBERS) ? inMap.get(CHASSIS_NUMBERS) : null
        String mode = inMap.containsKey(FETCH_MODE) ? inMap.get(FETCH_MODE) : null
        outMap.put("RESPONSE", prepareChassisEventHistoryToITS(chassisNbr, mode))
    }

    String prepareChassisEventHistoryToITS(String chassisNumber, String fetchMode) {
        def library = ExtensionUtils.getLibrary(ContextHelper.getThreadUserContext(), "ITSBaseUtilLibrary");
        String errorMessage = library.validateMandatoryFields(chassisNumber)
        JSONBuilder mainObj = JSONBuilder.createObject();
        boolean isFullMode = !StringUtils.isEmpty(fetchMode) && fetchMode.equalsIgnoreCase(FULL_MODE)
        if (errorMessage != null) {
            mainObj.put(ERROR_MESSAGE, _errorMessage)
        } else {
            String[] chassisNbrs = chassisNumber?.toUpperCase()?.split(",")*.trim()

            QueryResult rs = library.fetchUnitList(chassisNbrs, EquipClassEnum.CHASSIS)
            Map<Serializable, String> map = library.getUnitMap(rs, isFullMode)
            JSONBuilder jsonArray = JSONBuilder.createArray()
            for (Map.Entry<Serializable, String> entry : map.entrySet()) {
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
                        // skip the chassis record incase if a chassis with active inbound record is there but it doesnt have any visit event to send
                        if (isFullMode && !proceedFullMode) {
                            continue
                        }
                        for (Event event : eventsList) {
                            if (event != null /*&& (!isFullMode || (isFullMode && proceedFullMode))*/) {
                                Map<String, String> carrierParms = new HashMap<>()
                                carrierParms = getCarrierParams(event.getEventTypeId(), unitFacilityVisit)
                                JSONBuilder jsonEventObject = JSONBuilder.createObject();
                                jsonEventObject.put(CHASSIS_NUMBER, unit.getUnitId())
                                jsonEventObject.put(EVENT_CD, event.getEventTypeId() != null ? event.getEventTypeId() : "")
                                jsonEventObject.put(EVENT_DT_TM, event.getEvntAppliedDate() != null ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : "")
                                jsonEventObject.put(CHASSIS_OWNER_SCAC, unit.getUnitEquipment()?.getEquipmentOwnerId() != null ? unit.getUnitEquipment().getEquipmentOwnerId() : "")
                                jsonEventObject.put(CHASSIS_SZ_TP, new StringBuilder().append(unit.getUnitEquipment()?.getEqEquipType()?.getEqtypNominalLength()?.getKey()?.substring(3, 5))
                                        .append(unit.getUnitEquipment()?.getEqEquipType()?.getEqtypIsoGroup()?.getKey()).toString())
                                jsonEventObject.put(CARRIER_TYPE_CD, carrierParms.get(CARRIER_TYPE_CD) != null ? carrierParms.get(CARRIER_TYPE_CD) : "")
                                jsonEventObject.put(CARRIER_CD, carrierParms.get(CARRIER_CD) != null ? carrierParms.get(CARRIER_CD) : "")
                                // optional
                                if (carrierParms.get(POSITION) != null) {
                                    jsonEventObject.put(POSITION, carrierParms.get(POSITION))
                                }
                                if (unit.getUnitAcryId() != null) {
                                    jsonEventObject.put(GENSET_NUMBER, unit.getUnitAcryId())
                                }

                                if (unit.getUnitRelatedUnit() != null && unit.getUnitRelatedUnit().getUnitEquipment() != null && EquipClassEnum.CONTAINER.equals(unit.getUnitRelatedUnit().getUnitEquipment().getEqClass())) {
                                    jsonEventObject.put(CONTAINER_NUMBER, unit.getUnitRelatedUnit().getUnitId())
                                    UnitCategoryEnum unitCategoryEnum = unit.getUnitRelatedUnit().getUnitCategory();
                                    String ctrStatusCd = ""
                                    if (unit.getUnitRelatedUnit().getUnitLineOperator() != null) {
                                        jsonEventObject.put(SHIPPING_LINE_SCAC, unit.getUnitRelatedUnit().getUnitLineOperator().getBzuId())
                                    }

                                    if (unitCategoryEnum != null) {
                                        if (unitCategoryEnum.equals(UnitCategoryEnum.STORAGE))
                                            ctrStatusCd = EMPTY
                                        else {
                                            ctrStatusCd = unitCategoryEnum.getKey().substring(0, 3)

                                        }
                                    }
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
            mainObj.put(CHASSIS_EVENT_HISTORIES, jsonArray)

        }
        return mainObj.toJSONString()
    }


    private Map<String, String> getCarrierParams(String evtId, UnitFacilityVisit ufv) {
        Map<String, String> carrierParams = new HashMap<>()
        String carrierTypeCode = null
        String carrierCd = null;
        String position = null;
        switch (evtId) {
            case EventEnum.UNIT_CARRIAGE_IN_GATE.getKey():
            case EventEnum.UNIT_CARRIAGE_RECEIVE.getKey():
            case EventEnum.UNIT_CARRIAGE_DISMOUNT.getKey():
            case EventEnum.UNIT_IN_GATE.getKey():
            case EventEnum.UNIT_RECEIVE.getKey():
                carrierTypeCode = CARRIER_TYPE_CODE_TRUCK
                carrierCd = ufv.getUfvActualIbCv()?.getCvOperator()?.getBzuId()
                break;
            case EventEnum.UNIT_OUT_GATE.getKey():
            case EventEnum.UNIT_DELIVER.getKey():
            case EventEnum.UNIT_CARRIAGE_DELIVER.getKey():
            case EventEnum.UNIT_CARRIAGE_OUT_GATE.getKey():
            case EventEnum.UNIT_CARRIAGE_MOUNT.getKey():
                carrierTypeCode = CARRIER_TYPE_CODE_TRUCK
                carrierCd = ufv.getUfvActualObCv()?.getCvOperator()?.getBzuId()
                break;
            default:
                carrierTypeCode = CARRIER_TYPE_CODE_YARD
                carrierCd = ContextHelper.getThreadYard().getId()
                position = ufv.getUfvLastKnownPosition()?.getPosSlot()
        }
        carrierParams.put(CARRIER_TYPE_CD, carrierTypeCode)
        carrierParams.put(CARRIER_CD, carrierCd)
        carrierParams.put(POSITION, position)
        return carrierParams;
    }

    private static final String CHASSIS_NUMBERS = "chassisNumbers"
    private static final String CHASSIS_NUMBER = "chassisNumber"
    private static final String EVENT_CD = "eventCd"
    private static final String EVENT_DT_TM = "eventDtTm"
    private static final String CHASSIS_OWNER_SCAC = "chassisOwnerScac"
    private static final String CHASSIS_SZ_TP = "chassisSzTp"
    private static final String CARRIER_TYPE_CD = "carrierTypeCd"
    private static final String CARRIER_CD = "carrierCd"
    private static final String POSITION = "position"
    private static final String GENSET_NUMBER = "gensetNumber"
    private static final String CONTAINER_NUMBER = "containerNumber"
    private static final String SHIPPING_LINE_SCAC = "shippingLineScac"
    private static final String EMPTY = "MTY"
    private static final String CHASSIS_EVENT_HISTORIES = "chassisEventHistories"
    private static final String CARRIER_TYPE_CODE_TRUCK = "T"
    private static final String CARRIER_TYPE_CODE_YARD = "Y"
    private static final String FETCH_MODE = "fetchMode"
    private static final String FULL_MODE = "FULL"
    private static final String ERROR_MESSAGE = "errorMessage"
    private static final List<String> EVENT_LIST = [EventEnum.UNIT_CARRIAGE_IN_GATE.getKey(), EventEnum.UNIT_CARRIAGE_RECEIVE.getKey(), EventEnum.UNIT_CARRIAGE_DISMOUNT.getKey(), EventEnum.UNIT_IN_GATE.getKey(), EventEnum.UNIT_RECEIVE.getKey(), EventEnum.UNIT_OUT_GATE.getKey(), EventEnum.UNIT_DELIVER.getKey(), EventEnum.UNIT_CARRIAGE_DELIVER.getKey(), EventEnum.UNIT_CARRIAGE_OUT_GATE.getKey(), EventEnum.UNIT_CARRIAGE_MOUNT.getKey()]
    private static final List<String> EVENT_LIST_START = [EventEnum.UNIT_IN_GATE.getKey(), EventEnum.UNIT_CARRIAGE_IN_GATE.getKey()]
    private static DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("YYYY-MM-DD'T'HH:mm:ss");
    private static final String _errorMessage = "Missing Required Field : chassisNumbers"
    private static Logger LOGGER = Logger.getLogger(this.class);

}
