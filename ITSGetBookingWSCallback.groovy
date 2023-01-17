package ITS

import com.navis.argo.ArgoRefField
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.reference.EquipType
import com.navis.argo.business.reference.Equipment
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.query.common.api.QueryResult
import com.navis.framework.zk.util.JSONBuilder
import com.navis.inventory.InvEntity
import com.navis.inventory.InvField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.orders.OrdersEntity
import com.navis.orders.OrdersField
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.road.RoadApptsField
import com.navis.road.business.appointment.model.GateAppointment
import com.navis.road.business.atoms.AppointmentStateEnum
import com.navis.road.business.atoms.TruckerFriendlyTranSubTypeEnum
import com.navis.services.business.rules.Flag
import com.navis.services.business.rules.FlagType
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

import java.text.DateFormat
import java.text.SimpleDateFormat

/*
 * @Author <a href="mailto:annalakshmig@weservetech.com">ANNALAKSHMI G</a>
 * Date: 08/01/2022
 * Requirements:-  Receives one or more Booking Number(s) via Http Get and returns a list of Booking details in JSON format.
 *  @Inclusion Location	: Incorporated as a code extension of the type TRANSACTED_BUSINESS_FUNCTION --> Paste this code (ITSGetBookingWSCallback.groovy)
 *
 */

class ITSGetBookingWSCallback extends AbstractExtensionPersistenceCallback {

    @Override
    void execute(@Nullable Map inMap, @Nullable Map outMap) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSGetBookingWSCallback :: start")
        String refType = inMap.containsKey(REF_TYPE) ? inMap.get(REF_TYPE) : null
        String refNumber = inMap.containsKey(REF_NUMS) ? inMap.get(REF_NUMS) : null
        outMap.put("RESPONSE", prepareBookingMessageToITS(refType, refNumber))
    }

    String prepareBookingMessageToITS(String refType, String refNumber) {
        String[] refNumbers = refNumber?.toUpperCase()?.split(",")*.trim()
        String errorMessage = validateMandatoryFields(refType, refNumber)
        JSONBuilder bookingsObj = JSONBuilder.createObject();

        if (errorMessage.length() > 0) {
            bookingsObj.put(ERROR_MESSAGE, errorMessage)
        } else {

            DomainQuery dq = QueryUtils.createDomainQuery(OrdersEntity.BOOKING)
                    .addDqPredicate(PredicateFactory.in(InvField.EQBO_NBR, refNumbers))
            /*      if (EMPTY.equalsIgnoreCase(refType)) {
                      dq.addDqPredicate(PredicateFactory.eq(OrdersField.EQO_EQ_STATUS, FreightKindEnum.MTY))
                  } else {
                      dq.addDqPredicate(PredicateFactory.ne(OrdersField.EQO_EQ_STATUS, FreightKindEnum.MTY))
                  }*/
            LOGGER.debug("ITSGetBookingWSCallback :: dq" + dq)
            Serializable[] bookingGkeys = HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq)
            if (bookingGkeys != null && bookingGkeys.size() > 0) {
                JSONBuilder jsonArray = JSONBuilder.createArray()
                Booking booking = null;
                for (Serializable bkgGkey : bookingGkeys) {
                    booking = Booking.hydrate((bkgGkey))
                    if (booking != null) {
                        JSONBuilder jsonObject = JSONBuilder.createObject();
                        jsonObject.put(BOOKING_ID, booking.getEqboGkey());
                        jsonObject.put(BOOKING_NUM, booking.getEqboNbr() ?: EMPTY_STR)
                        jsonObject.put(SHIPPING_LINE_CD, booking.getEqoLine()?.getBzuId() ?: EMPTY_STR)
                        jsonObject.put(SHIPPING_LINE_SCAC, booking.getEqoLine()?.getBzuScac() ?: EMPTY_STR)
                        jsonObject.put(VESSEL_NAME, booking.getEqoVesselVisit()?.getCarrierVehicleName() ?: EMPTY_STR)
                        jsonObject.put(VOYAGE_NUM, booking.getEqoVesselVisit()?.getCarrierObVoyNbrOrTrainId() ?: EMPTY_STR)
                        jsonObject.put(POD_CD, booking.getEqoPod1()?.getPointUnlocId() ?: EMPTY_STR)
                        Set bkgItems = booking.getEqboOrderItems()
                        StringBuilder cmdtyStr = new StringBuilder()
                        if (bkgItems != null && bkgItems.size() > 0) {
                            Iterator bkgIterator = bkgItems.iterator();
                            while (bkgIterator.hasNext()) {
                                EquipmentOrderItem item = (EquipmentOrderItem) bkgIterator.next();
                                if (item != null && item.getEqoiCommodity() != null && item.getEqoiCommodity().getCmdyId() != null) {
                                    cmdtyStr.append(item.getEqoiCommodity().getCmdyId()).append(",")
                                }
                            }
                        }
                        jsonObject.put(CMDTY_CD, cmdtyStr.length() > 0 ? cmdtyStr.substring(0, cmdtyStr.length() - 1) : EMPTY_STR)
                        jsonObject.put(BOOKED_QTY, booking.getEqoQuantity())

                        if (refType.equalsIgnoreCase(EXP)) {
                            //jsonObject.put(RECEIVED_QTY, booking.getEqoTallyReceive())
                            boolean isTMFHold = false
                            boolean isPortFeeHold = false
                            Collection flagsOnEntity = FlagType.findActiveFlagsOnEntity(booking);
                            if (flagsOnEntity != null && flagsOnEntity.size() > 0) {
                                for (Flag flag : flagsOnEntity) {
                                    if (flag.getFlagFlagType() != null) {
                                        String flagId = flag.getFlagFlagType().getFlgtypId();
                                        if (TMF_HOLD.equalsIgnoreCase(flagId)) {
                                            isTMFHold = true
                                        }
                                        if (PORT_FEE_HOLD_BKG.equalsIgnoreCase(flagId)) {
                                            isPortFeeHold = true
                                        }
                                    }
                                }
                            }
                            jsonObject.put(IS_TMF_STATUS_RELEASED, !isTMFHold)
                            jsonObject.put(IS_PORT_FEE_STATUS_RELEASED, !isPortFeeHold)
                        } else {
                            jsonObject.put(DELIVERED_QTY, "0")
                            if (!StringUtils.isEmpty(booking.getEqoNotes())) {

                                jsonObject.put(REMARKS_TXT, booking.getEqoNotes())
                            }
                        }

                        DomainQuery containerDomainQuery = QueryUtils.createDomainQuery(InvEntity.UNIT_FACILITY_VISIT)
                                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_DEPARTURE_ORDER,booking.getEqboGkey()))
                                .addDqField(UnitField.UFV_DECLARED_IB_MODE)
                                .addDqField(UnitField.UFV_TRANSIT_STATE)
                                .addDqField(UnitField.UFV_UNIT_ID)
                                .addDqField(InvField.UFV_ACTUAL_OB_CV)
                                .addDqField(InvField.UFV_ACTUAL_IB_CV)
                                .addDqField(InvField.UFV_LAST_KNOWN_POSITION)
                                .addDqField(InvField.UFV_GAPPT_NBR)
                                .addDqField(InvField.UFV_TIME_IN)
                                .addDqField(InvField.UFV_TIME_OUT)
                                .addDqField(UnitField.UFV_EQ);
                        if (EMPTY.equalsIgnoreCase(refType)) {
                            containerDomainQuery.addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_CATEGORY, UnitCategoryEnum.STORAGE));
                        } else  {
                            containerDomainQuery.addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_CATEGORY, UnitCategoryEnum.EXPORT));
                        }

                        QueryResult ufvQueryResult = HibernateApi.getInstance().findValuesByDomainQuery(containerDomainQuery)
                        LOGGER.debug("ITSGetBookingWSCallback :: containerDomainQuery count" + ufvQueryResult.getTotalResultCount())
                        JSONBuilder fclTalliesArray = JSONBuilder.createArray()
                        int totalToComeQty = 0;
                        int totalDeliveredQty = 0;


                        if (bkgItems != null && bkgItems.size() > 0) {
                            Iterator bkgIterator = bkgItems.iterator();
                            while (bkgIterator.hasNext()) {
                                EquipmentOrderItem item = (EquipmentOrderItem) bkgIterator.next();

                                if (item != null) {
                                    JSONBuilder fclTallyObject = JSONBuilder.createObject();
                                    fclTallyObject.put(CONTAINER_SZ_TP_HT, new StringBuilder().append(item.getEqoiEqSize()?.getKey()?.substring(3))
                                            .append(item.getEqoiEqIsoGroup()?.getKey())
                                            .append(item.getEqoiEqHeight()?.getKey()?.substring(3)).toString())
                                    fclTallyObject.put(BOOKED_QTY, item.getEqoiQty())
                                    if (refType.equalsIgnoreCase(EXP)) {
                                        fclTallyObject.put(RECEIVED_QTY, item.getEqoiTallyReceive())
                                        int truckQty = 0;
                                        // no.of. to come ctrs (for an item) on truck = advised/inbound count without appt + appt count
                                        int trainQty = 0;
                                        int vesselQty = 0;
                                        Equipment equipment = null
                                        int truckqtyFromGA = 0
                                        LOGGER.debug("ITSGetBookingWSCallback :: item.getEqoiSampleEquipType()" + item.getEqoiSampleEquipType())
                                        if (item.getEqoiSampleEquipType() != null) {

                                            truckqtyFromGA = getCtrCountFromGateAppt(booking, item.getEqoiSampleEquipType())


                                        }
                                        for (int i = 0; i < ufvQueryResult.getTotalResultCount(); i++) {
                                            if (ufvQueryResult.getValue(i, UnitField.UFV_EQ) != null) {
                                                equipment = (Equipment) HibernateApi.getInstance().load(Equipment.class, (Serializable) ufvQueryResult.getValue(i, UnitField.UFV_EQ));
                                            }
                                            LOGGER.debug("ITSGetBookingWSCallback :: equipment.getEqEquipType()" + equipment.getEqEquipType())
                                            if (equipment!= null && item.getEqoiSampleEquipType() != null && item.getEqoiSampleEquipType().isEqualSizeTypeHeight(equipment.getEqEquipType())) {
                                                LOGGER.debug("item.getEqoiSampleEquipType()"+item.getEqoiSampleEquipType())

                                                if ((UfvTransitStateEnum.S10_ADVISED.equals((UfvTransitStateEnum) ufvQueryResult.getValue(i, UnitField.UFV_TRANSIT_STATE))
                                                        || UfvTransitStateEnum.S20_INBOUND.equals((UfvTransitStateEnum) ufvQueryResult.getValue(i, UnitField.UFV_TRANSIT_STATE)))){

                                                    GateAppointment gateAppointment = GateAppointment.findGateAppointment((Long) ufvQueryResult.getValue(i, InvField.UFV_GAPPT_NBR))
                                                    if (gateAppointment == null || (gateAppointment != null && !AppointmentStateEnum.CREATED.equals(gateAppointment.getApptState()))) {
                                                        if (LocTypeEnum.TRUCK.equals((LocTypeEnum) ufvQueryResult.getValue(i, UnitField.UFV_DECLARED_IB_MODE))) {
                                                            truckQty += 1
                                                        }
                                                        if (LocTypeEnum.TRAIN.equals((LocTypeEnum) ufvQueryResult.getValue(i, UnitField.UFV_DECLARED_IB_MODE))) {
                                                            trainQty += 1
                                                        }
                                                        if (LocTypeEnum.VESSEL.equals((LocTypeEnum) ufvQueryResult.getValue(i, UnitField.UFV_DECLARED_IB_MODE))) {
                                                            vesselQty += 1
                                                        }
                                                    }

                                                }
                                            }
                                        }
                                        truckQty = truckQty + truckqtyFromGA
                                        totalToComeQty = truckQty + trainQty + vesselQty + totalToComeQty
                                        fclTallyObject.put(TOCOME_ON_TRUCK_QTY, truckQty)
                                        fclTallyObject.put(TOCOME_ON_RAIL_QTY, trainQty)
                                        fclTallyObject.put(TOCOME_ON_VESSEL_QTY, vesselQty)
                                    } else {
                                        int itemDelQty = item.getOrderItemDeliveredUnits().size();
                                        totalDeliveredQty +=itemDelQty;
                                        fclTallyObject.put(DELIVERED_QTY, itemDelQty);
                                        fclTallyObject.put(PRE_ASSIGNED_QTY, item.getOrderItemReservedUnits().size())
                                    }

                                    fclTalliesArray.add(fclTallyObject)
                                }
                            }

                        }
                        if (refType.equalsIgnoreCase(EXP)) {
                            jsonObject.put(RECEIVED_QTY, (booking.getEqoTallyReceive() + totalToComeQty))
                            jsonObject.put(FCL_TALLIES, fclTalliesArray)
                        } else {
                            jsonObject.put(EMPTY_TALLIES, fclTalliesArray)
                            jsonObject.put(DELIVERED_QTY, totalDeliveredQty)
                        }

                        JSONBuilder bkgCtrsArray = JSONBuilder.createArray()
                        for (int i = 0; i < ufvQueryResult.getTotalResultCount(); i++) {
                            JSONBuilder bkgCtrObject = JSONBuilder.createObject();
                            bkgCtrObject.put(CONTAINER_NUMBER, ufvQueryResult.getValue(i, UnitField.UFV_UNIT_ID))
                            Equipment unitEquipment = null
                            if (ufvQueryResult.getValue(i, UnitField.UFV_EQ) != null) {
                                unitEquipment = (Equipment) HibernateApi.getInstance().load(Equipment.class, (Serializable) ufvQueryResult.getValue(i, UnitField.UFV_EQ));
                            }
                            bkgCtrObject.put(CONTAINER_SZ_TP_HT, new StringBuilder().append(unitEquipment?.getEqEquipType()?.getEqtypNominalLength()?.getKey()?.substring(3))
                                    .append(unitEquipment?.getEqEquipType()?.getEqtypIsoGroup()?.getKey())
                                    .append(unitEquipment?.getEqEquipType()?.getEqtypNominalHeight()?.getKey()?.substring(3)).toString())
                            LocPosition ufvPosition = (LocPosition) ufvQueryResult.getValue(i, InvField.UFV_LAST_KNOWN_POSITION)
                            CarrierVisit obCV = null
                            CarrierVisit ibCV = null
                            if (ufvQueryResult.getValue(i, InvField.UFV_ACTUAL_OB_CV) != null) {
                                obCV = CarrierVisit.hydrate((Serializable) ufvQueryResult.getValue(i, InvField.UFV_ACTUAL_OB_CV))
                            }
                            if (ufvQueryResult.getValue(i, InvField.UFV_ACTUAL_IB_CV) != null) {
                                ibCV = CarrierVisit.hydrate((Serializable) ufvQueryResult.getValue(i, InvField.UFV_ACTUAL_IB_CV))
                            }
                            if (refType.equalsIgnoreCase(EXP)) {

                                String receivedTime = null;
                                if (ufvQueryResult.getValue(i, InvField.UFV_TIME_IN) != null) {
                                    receivedTime = ISO_DATE_FORMAT.format((Date) ufvQueryResult.getValue(i, InvField.UFV_TIME_IN))
                                }

                                bkgCtrObject.put(FULL_EMPTY_CD, "F")

                                bkgCtrObject.put(CURRENT_POSITION, ufvPosition.getPosName())
                                if (receivedTime != null) {
                                    bkgCtrObject.put(RECEIVED_DT_TM, receivedTime)
                                }
                            } else {
                                if (ufvQueryResult.getValue(i, InvField.UFV_TIME_OUT) != null) {
                                    String deliveredTime = ISO_DATE_FORMAT.format((Date) ufvQueryResult.getValue(i, InvField.UFV_TIME_OUT))
                                    bkgCtrObject.put(DELIVERED_DT_TM, deliveredTime)
                                    if (ufvPosition != null && ufvPosition.getPosLocType() != null) {
                                        if (LocTypeEnum.TRUCK.equals(ufvPosition.getPosLocType())) {
                                            String truckingCmpyScac = obCV?.getCvOperator()?.getBzuId()
                                            bkgCtrObject.put(TRUCKING_CO_SCAC, truckingCmpyScac)
                                        }
                                    }
                                }
                            }
                            bkgCtrsArray.add(bkgCtrObject)
                            if (refType.equalsIgnoreCase(EXP)) {
                                jsonObject.put(BOOKING_CONTAINERS, bkgCtrsArray)
                            } else {
                                jsonObject.put(EMPTY_BOOKING_CONTAINERS, bkgCtrsArray)
                            }
                        }

                        jsonArray.add(jsonObject)

                    }
                    if (refType.equalsIgnoreCase(EXP)) {
                        bookingsObj.put(BOOKINGS, jsonArray)
                    } else {
                        bookingsObj.put(EMPTY_BOOKINGS, jsonArray)
                    }
                }

                // bookingsObj.add(bookingsObject)
                LOGGER.debug("Response string" + bookingsObj.toJSONString())

            }
        }
        return bookingsObj.toJSONString()
    }


    private String validateMandatoryFields(String refType, String refNumber) {
        StringBuilder stringBuilder = new StringBuilder()
        if (StringUtils.isEmpty(refType)) {
            stringBuilder.append("Missing required parameter : refType.").append(" :: ")
        }
        if (!StringUtils.isEmpty(refType) && !(EMPTY.equalsIgnoreCase(refType) || EXP.equalsIgnoreCase(refType))) {
            stringBuilder.append("Invalid value for parameter : refType.")
        }
        if (StringUtils.isEmpty(refNumber)) {
            stringBuilder.append("Missing required parameter : refNums.")
        }
        return stringBuilder.toString()
    }

    private int getCtrCountFromGateAppt(Booking booking, EquipType equipType) {
        DomainQuery gateApptDq = QueryUtils.createDomainQuery("GateAppointment")
                .addDqPredicate(PredicateFactory.eq(GAPPPT_EQBO_NBR, booking.getEqboNbr()))
                .addDqPredicate(PredicateFactory.eq(GAPPPT_LINE_ID, booking.getEqoLine().getBzuId()))
                .addDqPredicate(PredicateFactory.eq(GAPPPT_CTR_EQ_TYPE_ISO, equipType.getEqtypIsoGroup()))
                .addDqPredicate(PredicateFactory.eq(GAPPPT_CTR_EQ_TYPE_NOM_LENGTH, equipType.getEqtypNominalLength()))
                .addDqPredicate(PredicateFactory.eq(GAPPPT_CTR_EQ_TYPE_NOM_HEIGHT, equipType.getEqtypNominalHeight()))
                .addDqPredicate(PredicateFactory.eq(RoadApptsField.GAPPT_STATE, AppointmentStateEnum.CREATED))
                .addDqPredicate(PredicateFactory.eq(RoadApptsField.GAPPT_TRAN_TYPE, TruckerFriendlyTranSubTypeEnum.DOE))

        return HibernateApi.getInstance().findCountByDomainQuery(gateApptDq)
    }
    public static final MetafieldId GAPPPT_EQBO_NBR = MetafieldIdFactory.getCompoundMetafieldId(RoadApptsField.GAPPT_ORDER, InvField.EQBO_NBR)
    public static final MetafieldId GAPPPT_LINE_ID = MetafieldIdFactory.getCompoundMetafieldId(RoadApptsField.GAPPT_LINE_OPERATOR, ArgoRefField.BZU_ID)
    public static final MetafieldId GAPPPT_CTR_EQ_TYPE_ISO = MetafieldIdFactory.getCompoundMetafieldId(RoadApptsField.GAPPT_CTR_EQUIP_TYPE, ArgoRefField.EQTYP_ISO_GROUP)
    public static final MetafieldId GAPPPT_CTR_EQ_TYPE_NOM_LENGTH = MetafieldIdFactory.getCompoundMetafieldId(RoadApptsField.GAPPT_CTR_EQUIP_TYPE, ArgoRefField.EQTYP_NOMINAL_LENGTH)
    public static final MetafieldId GAPPPT_CTR_EQ_TYPE_NOM_HEIGHT = MetafieldIdFactory.getCompoundMetafieldId(RoadApptsField.GAPPT_CTR_EQUIP_TYPE, ArgoRefField.EQTYP_NOMINAL_HEIGHT)
    private static final String REF_TYPE = "refType"
    private static final String REF_NUMS = "refNums"
    private static final String EXP = "EXP"
    private static final String EMPTY = "MTY"
    private static final String BOOKING_NUM = "bookingNum"
    private static final String SHIPPING_LINE_SCAC = "shippingLineScac"
    private static final String SHIPPING_LINE_CD = "shippingLineCd"

    private static final String VESSEL_NAME = "vesselName"
    private static final String VOYAGE_NUM = "voyageNum"
    private static final String POD_CD = "podCd"
    private static final String CMDTY_CD = "cmdtyCd"
    private static final String BOOKED_QTY = "bookedQty"
    private static final String RECEIVED_QTY = "receivedQty"
    private static final String IS_TMF_STATUS_RELEASED = "isTmfStatusReleased"
    private static final String IS_PORT_FEE_STATUS_RELEASED = "isPortFeeStatusReleased"

    private static final String CONTAINER_SZ_TP_HT = "containerSzTpHt"
    private static final String TOCOME_ON_TRUCK_QTY = "tocomeOnTruckQty"
    private static final String TOCOME_ON_RAIL_QTY = "tocomeOnRailQty"
    private static final String TOCOME_ON_VESSEL_QTY = "tocomeOnVesselQty"
    private static final String CONTAINER_NUMBER = "containerNumber"
    private static final String FULL_EMPTY_CD = "fullEmptyCd"
    private static final String CARRIER_TYPE_CD = "carrierTypeCd"
    private static final String CARRIER_CD = "carrierCd"
    private static final String CURRENT_POSITION = "currentPosition"
    private static final String RECEIVED_DT_TM = "receivedDtTm"
    private static final String BOOKINGS = "bookings"
    private static final String FCL_TALLIES = "fclTallies"
    private static final String BOOKING_CONTAINERS = "bookingContainers"
    private static final String TMF_HOLD = "TMF BKG"
    private static final String PORT_FEE_HOLD_BKG = "PORT FEE HOLD EXP"
    private static final String EMPTY_BOOKINGS = "emptyBookings"
    private static final String DELIVERED_QTY = "deliveredQty"
    private static final String REMARKS_TXT = "remarksTxt"
    private static final String EMPTY_TALLIES = "emptyTallies"
    private static final String PRE_ASSIGNED_QTY = "preAssignedQty"
    private static final String EMPTY_BOOKING_CONTAINERS = "emptyBookingContainers"
    private static final String DELIVERED_DT_TM = "deliveredDtTm"
    private static final String TRUCKING_CO_SCAC = "truckingCoScac"
    private static final String BOOKING_ID = "bookingId"



    private static final String ERROR_MESSAGE = "errorMessage"
    private static final String EMPTY_STR = ""
    private UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
    private static DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static Logger LOGGER = Logger.getLogger(this.class);

}
