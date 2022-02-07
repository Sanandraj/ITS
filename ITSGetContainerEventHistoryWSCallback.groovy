import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.EquipClassEnum
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.query.common.api.QueryResult
import com.navis.framework.zk.util.JSONBuilder
import com.navis.inventory.InvEntity
import com.navis.inventory.InvField
import com.navis.inventory.business.api.UnitField
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
        String ctrNbr = inMap.containsKey("containerNumbers") ? inMap.get("containerNumbers") : null
        String mode = inMap.containsKey("fetchMode") ? inMap.get("fetchMode") : null
        outMap.put("RESPONSE", prepareContainerEventHistoryToITS(ctrNbr, mode))
    }


    String prepareContainerEventHistoryToITS(String ctrNumber, String fetchMode) {
        LOGGER.debug("ITSGetContainerEventHistoryWSCallback :: fetchMode" + fetchMode)
        String errorMessage = validateMandatoryFields(ctrNumber)
        JSONBuilder mainObj = JSONBuilder.createObject();
        boolean isFullMode = false
        if (errorMessage.length() > 0) {
            mainObj.put("errorMessage", errorMessage)
        } else {
            String[] ctrNbrs = ctrNumber?.toUpperCase()?.split(",")*.trim()

            DomainQuery dq = QueryUtils.createDomainQuery(InvEntity.UNIT_FACILITY_VISIT)
                    .addDqField(UnitField.UFV_UNIT_ID)
                    .addDqField(InvField.UFV_GKEY)
                    .addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("ufvUnit.unitEquipment.eqClass"), EquipClassEnum.CONTAINER))
                    .addDqPredicate(PredicateFactory.in(UnitField.UFV_UNIT_ID, ctrNbrs))
                    .addDqOrdering(Ordering.desc(InvField.UFV_TIME_OF_LAST_MOVE))
            QueryResult rs = HibernateApi.getInstance().findValuesByDomainQuery(dq)
            Map<Serializable, String> map = new HashMap<>()
            if (rs.getTotalResultCount() > 0) {
                for (int i = 0; i < rs.getTotalResultCount(); i++) {
                    if ((!StringUtils.isEmpty(fetchMode) && !fetchMode.equalsIgnoreCase("FULL")) || StringUtils.isEmpty(fetchMode)) {
                        if (!map.containsValue(rs.getValue(i, UnitField.UFV_UNIT_ID))) {
                            map.put(rs.getValue(i, InvField.UFV_GKEY) as Serializable, rs.getValue(i, UnitField.UFV_UNIT_ID).toString())
                        }
                    } else if (!StringUtils.isEmpty(fetchMode) && fetchMode.equalsIgnoreCase("FULL")) {
                        map.put(rs.getValue(i, InvField.UFV_GKEY) as Serializable, rs.getValue(i, UnitField.UFV_UNIT_ID).toString())
                        isFullMode = true
                    }
                }
            }
            List<Event> eventsList = new ArrayList<>()
            JSONBuilder jsonArray = JSONBuilder.createArray()
            for (Map.Entry<Serializable, String> entry : map.entrySet()) {
                Map<String, String> carrierParms = new HashMap<>()
                boolean proceedFullMode = false;
                UnitFacilityVisit unitFacilityVisit = UnitFacilityVisit.hydrate(entry.getKey())
                if (unitFacilityVisit != null) {
                    Unit unit = unitFacilityVisit.getUfvUnit()
                    if (unit != null) {
                        eventsList = (List<Event>) servicesManager.getEventHistory(unit)
                        if (isFullMode) {
                            for (Event evt : eventsList) {
                                if (EVENT_LIST_START.contains(evt.getEventTypeId())) {
                                    proceedFullMode = true;
                                    break
                                }
                            }
                        }
                        for (Event event : eventsList) {
                            if (event != null && ((!isFullMode && EVENT_LIST.contains(event.getEventTypeId())) || (isFullMode && proceedFullMode))) {

                                JSONBuilder jsonEventObject = JSONBuilder.createObject();
                                jsonEventObject.put("containerNumber", unit.getUnitId())
                                jsonEventObject.put("eventCd", event.getEventTypeId() != null ? event.getEventTypeId() : "")
                                jsonEventObject.put("eventDtTm", event.getEvntAppliedDate() != null ? ISO_DATE_FORMAT.format(event.getEvntAppliedDate()) : "")
                                jsonEventObject.put("shippingLineScac", unit.getUnitLineOperator()?.getBzuId() != null ? unit.getUnitLineOperator().getBzuId() : "")
                                UnitCategoryEnum unitCategoryEnum = unit.getUnitCategory();
                                String ctrStatusCd = ""
                                String vesselVoyage = null
                                if (unitCategoryEnum != null) {
                                    if (unitCategoryEnum.equals(UnitCategoryEnum.STORAGE))
                                        ctrStatusCd = "MTY"
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
                                jsonEventObject.put("containerStatusCd", ctrStatusCd)
                                carrierParms = getCarrierParams(event.getEventTypeId(), unitFacilityVisit)
                                jsonEventObject.put("containerSzTpHt", new StringBuilder().append(unit.getUnitEquipment()?.getEqEquipType()?.getEqtypNominalLength()?.getKey()?.substring(3, 5))
                                        .append(unit.getUnitEquipment()?.getEqEquipType()?.getEqtypIsoGroup()?.getKey())
                                        .append(unit.getUnitEquipment()?.getEqEquipType()?.getEqtypNominalHeight()?.getKey()?.substring(3, 5)).toString())
                                jsonEventObject.put("carrierTypeCd", carrierParms.get("carrierTypeCd") != null ? carrierParms.get("carrierTypeCd") : "")
                                jsonEventObject.put("carrierCd", carrierParms.get("carrierCd") != null ? carrierParms.get("carrierCd") : "")
                                // optional
                                if (carrierParms.get("pos") != null) {
                                    jsonEventObject.put("position", carrierParms.get("pos"))
                                }
                                if (vesselVoyage != null) {
                                    jsonEventObject.put("cargoVVC", vesselVoyage)
                                }
                                if (unit.getUnitRouting()?.getRtgPOL()?.getPointUnLoc()?.getUnlocId() != null) {
                                    jsonEventObject.put("polCd", unit.getUnitRouting().getRtgPOL().getPointUnLoc().getUnlocId())
                                }
                                if (unit.getUnitRouting()?.getRtgPOD1()?.getPointUnLoc()?.getUnlocId() != null) {
                                    jsonEventObject.put("podCd", unit.getUnitRouting().getRtgPOD1().getPointUnLoc().getUnlocId())
                                }
                                if (unit.getUnitCarriageUnit()?.getUnitId() != null) {
                                    jsonEventObject.put("chassisNumber", unit.getUnitCarriageUnit().getUnitId())
                                }
                                jsonArray.add(jsonEventObject)

                            }
                            if(EVENT_LIST_START.contains(event.getEventTypeId())){
                                break
                            }
                        }

                    }
                }
            }
            mainObj.put("containerEventHistories", jsonArray)

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
                carrierTypeCode = "T"
                carrierCd = ufv.getUfvActualIbCv()?.getCvOperator()?.getBzuId()
                break;
            case EventEnum.UNIT_OUT_GATE.getKey():
            case EventEnum.UNIT_DELIVER.getKey():
                carrierTypeCode = "T"
                carrierCd = ufv.getUfvActualObCv()?.getCvOperator()?.getBzuId()
                break;
            case EventEnum.UNIT_IN_VESSEL.getKey():
            case EventEnum.UNIT_DISCH.getKey():
                carrierTypeCode = "V"
                carrierCd = ufv.getUfvActualIbCv()?.getCvOperator()?.getBzuId()
                position = ufv.getUfvArrivePosition()?.getPosSlot()
                break;
            case EventEnum.UNIT_OUT_VESSEL.getKey():
            case EventEnum.UNIT_LOAD.getKey():
                carrierTypeCode = "V"
                carrierCd = ufv.getUfvActualObCv()?.getCvOperator()?.getBzuId()
                position = ufv.getUfvLastKnownPosition()?.getPosSlot()
                break;
            case EventEnum.UNIT_IN_RAIL.getKey():
            case EventEnum.UNIT_DERAMP.getKey():
                carrierTypeCode = "R"
                carrierCd = ufv.getUfvActualIbCv()?.getCvOperator()?.getBzuId()
                position = ufv.getUfvArrivePosition()?.getPosSlot()
                break;
            case EventEnum.UNIT_OUT_RAIL.getKey():
            case EventEnum.UNIT_RAMP.getKey():
                carrierTypeCode = "R"
                carrierCd = ufv.getUfvActualObCv()?.getCvOperator()?.getBzuId()
                position = ufv.getUfvLastKnownPosition()?.getPosSlot()
                break;
            default:
                carrierTypeCode = "Y"
                carrierCd = "PIERG"
                position = ufv.getUfvLastKnownPosition()?.getPosSlot()
        }
        carrierParams.put("carrierTypeCd", carrierTypeCode)
        carrierParams.put("carrierCd", carrierCd)
        carrierParams.put("pos", position)
        return carrierParams;
    }


    private String validateMandatoryFields(String ctrNumber) {
        StringBuilder stringBuilder = new StringBuilder()
        if (StringUtils.isEmpty(ctrNumber)) {
            stringBuilder.append("Missing required parameter : containerNumbers.")

        }

        return stringBuilder.toString()
    }


    private static final List<String> EVENT_LIST =
            [EventEnum.UNIT_IN_GATE.getKey(), EventEnum.UNIT_OUT_GATE.getKey(), EventEnum.UNIT_LOAD.getKey(), EventEnum.UNIT_DISCH.getKey(), EventEnum.UNIT_RAMP.getKey(), EventEnum.UNIT_DERAMP.getKey(), EventEnum.UNIT_IN_RAIL.getKey(), EventEnum.UNIT_OUT_RAIL.getKey(), EventEnum.UNIT_IN_VESSEL.getKey(), EventEnum.UNIT_OUT_VESSEL.getKey(), EventEnum.UNIT_DELIVER.getKey(), EventEnum.UNIT_RECEIVE.getKey()]
    private static final List<String> EVENT_LIST_START =
            [EventEnum.UNIT_IN_GATE.getKey(), EventEnum.UNIT_IN_RAIL.getKey(),  EventEnum.UNIT_IN_VESSEL.getKey()]
    private static ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
    private static DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("YYYY-MM-DD'T'HH:mm:ss");
    private static Logger LOGGER = Logger.getLogger(this.class);

}
