/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.*
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.api.VesselVisitFinder
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.EquipIsoGroupEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Complex
import com.navis.argo.business.model.Facility
import com.navis.argo.business.reference.EquipType
import com.navis.argo.business.reference.LineOperator
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.edi.business.edimodel.EdiConsts
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageCollectorUtils
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.orders.OrdersPropertyKeys
import com.navis.orders.business.eqorders.Booking
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.vessel.business.schedule.VesselVisitLine
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject
import org.jetbrains.annotations.Nullable
import org.zkoss.zhtml.Pre

import java.text.DateFormat
import java.text.SimpleDateFormat

/*
 *
 * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 10/March/2022
 *
 *
 * Requirements : This groovy is used to update the msg funciton and EDO Type based on the criteria.
 *
 *
 * Modified by @Author <a href="mailto:sanandaraj@servimostech.com">S Anandaraj</a>, 04/NOV/2022
*
* Requirements : This groovy is used to validate an EDI Booking Vessel Visit Cut Off/Line Cut Off and also to send the booking details to Emodal while deleting the booking.
*
 * @Inclusion Location	: Incorporated as a code extension of the type EDI_POST_INTERCEPTOR.
 *
 *  Load Code Extension to N4:
        1. Go to Administration --> System --> Code Extensions
        2. Click Add (+)
        3. Enter the values as below:
            Code Extension Name:  ITSBookingPostInterceptor
            Code Extension Type:  EDI_POST_INTERCEPTOR
            Groovy Code: Copy and paste the contents of groovy code.
        4. Click Save button

 Attach code extension to EDI session:
        1. Go to Administration-->EDI-->EDI configuration
        2. Select the EDI session and right click on it
        3. Click on Edit
        4. Select the extension in "Post Code Extension" tab
        5. Click on save
 *
 */


class ITSBookingPostInterceptor extends AbstractEdiPostInterceptor {


    private static final Logger LOGGER = Logger.getLogger(this.class)

    @Override
    void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {

        LOGGER.setLevel(Level.DEBUG)
        if (!BookingTransactionsDocument.class.isAssignableFrom(inXmlTransactionDocument.getClass())) {
            throw BizFailure.create(OrdersPropertyKeys.ERRKEY__TYPE_MISMATCH_XMLBEAN, null, inXmlTransactionDocument.getClass().getName());
        }

        BookingTransactionsDocument bkgDocument = (BookingTransactionsDocument) inXmlTransactionDocument;
        final BookingTransactionsDocument.BookingTransactions bkgtrans = bkgDocument.getBookingTransactions();
        final BookingTransactionDocument.BookingTransaction[] bkgtransArray = bkgtrans.getBookingTransactionArray();
        if (bkgtransArray.length != 1) {
            throw BizFailure.create(OrdersPropertyKeys.ERRKEY__XML_BOOKING_TRANSACTION_DOCUMENT_LENGTH_EXCEED, null, String.valueOf(bkgtransArray.length));
        }
        BookingTransactionDocument.BookingTransaction bkgTrans = bkgtransArray[0];
        LOGGER.debug(" bkgTrans:" + bkgTrans)
        if (bkgTrans == null) {
            logMsg("Booking Transaction is null returning ");
            return;
        }

        if (bkgTrans != null) {

            EdiBooking ediBooking = bkgTrans.getEdiBooking()
            String bookingNbr = ediBooking != null ? ediBooking.getBookingNbr() : null;
            EdiOperator ediOp = bkgTrans.getLineOperator();
            Booking booking = null;
            EdiVesselVisit ediVV = bkgTrans.getEdiVesselVisit();
            Facility facility = ContextHelper.getThreadFacility();
            Complex complex = ContextHelper.getThreadComplex();

            ScopedBizUnit bkgLineOp = ediOp != null ? this.resolveLineOperator(ediVV, ediOp) : null;
            CarrierVisit ediCv = bkgLineOp != null ? this.resolveCarrierVisit(ediVV, complex, facility, bkgLineOp) : null;
            VesselVisitDetails vvd = ediCv != null ? VesselVisitDetails.resolveVvdFromCv(ediCv) : null;

            if (vvd != null) {
                Date cutoffDate = vvd.getVvFlexDate02()


                VesselVisitLine vvl = VesselVisitLine.findVesselVisitLine(vvd, bkgLineOp)
                if (vvl != null && vvl.getVvlineTimeActivateYard() !=null) {
                    cutoffDate = vvl.getVvlineTimeActivateYard()
                }
                TimeZone timeZone = ContextHelper.getThreadUserTimezone()
                Date currentDate = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone)
                cutoffDate=ArgoUtils.convertDateToLocalDateTime(cutoffDate, timeZone)

                LOGGER.debug("currentDate EDI POST" + currentDate)
                LOGGER.debug("ediCutoffDate EDI POST" + cutoffDate)

                //validating Vessel visit Edi cut off date and Line Cut off date

                if(cutoffDate != null && currentDate!=null){
                    if(currentDate.after(cutoffDate)){
                        LOGGER.debug("currentDate after cutOffDate EDI POST ::" )
                        getMessageCollector().registerExceptions(BizViolation.create(PropertyKeyFactory.valueOf("VesselVisit EDI CutOff/Line CutOff is Locked. Could not post in EDI POST Interceptor."), (BizViolation) null))
                    }
                }




                //Date currentDate = new Date()
                /*if (cutoffDate != null && currentDate.after(cutoffDate)) {
                    new GroovyApi().registerError("Vessel Visit past EDI cut-off. Could not post EDI.")
                }*/


                if (NON_VISIT.equalsIgnoreCase(vvd.getVvFlexString01())) {
                    bkgTrans.setEdiBookingType(EDO)
                    ediBooking.setBookingHandlingInstructions("Requested Vessel Visit does not have call at ITS. [Vessel: " + ediVV.getVesselName() + ". Voyage: " + ediVV.getOutVoyageNbr() + "]")
                    EqoOrderPurpose orderPurpose = ediBooking.getEqoOrderPurpose() == null ? ediBooking.addNewEqoOrderPurpose() : ediBooking.getEqoOrderPurpose()
                    if(orderPurpose != null){
                        orderPurpose.setOrderPurposeId(ORDER_PURPOSE)
                    }

                    LOGGER.debug(" bkgTrans:" + bkgTrans)
                }
            }

            booking = Booking.findBookingWithoutVesselVisit(bookingNbr, bkgLineOp)
            // Gap list #9-4: 301 - Block EDI update if any container has been received (in yard) for the booking.
            if (booking != null && booking.isUnitsReservedForBooking()) {
                int yardUnitCount = findYardUnitCount(booking)
                if (yardUnitCount != null && yardUnitCount > 0) {
                    inParams.put(EdiConsts.SKIP_POSTER, true)
                    if (bkgTrans.getMsgFunction() != null && (MSG_FUNCTION_DELETE.equals(bkgTrans.getMsgFunction()) || MSG_FUNCTION_REMOVE.equals(bkgTrans.getMsgFunction()))) {
                        new GroovyApi().registerError("Container already received against requested order. Could not delete.")
                    }
                    return;
                }
            }
            List <BookingTransactionDocument.BookingTransaction.EdiBookingItem> bookingItemList = bkgTrans.getEdiBookingItemList()
            for (BookingTransactionDocument.BookingTransaction.EdiBookingItem bookingItem : bookingItemList) {
                //Reefer Validation - Begin
                boolean isTempRequired = bookingItem.getTemperatureRequired() != null && bookingItem.getTemperatureRequired().length() > 0 ? Boolean.TRUE : Boolean.FALSE
                ReeferRqmnts ediReeferRqmts = bookingItem.getEdiReeferRqmnts() != null ? bookingItem.getEdiReeferRqmnts() : null
                String co2Required = ediReeferRqmts != null ? ediReeferRqmts.getRfCo2Required() : null
                String o2Required = ediReeferRqmts != null ? ediReeferRqmts.getRfO2Required() : null
                String ventRequired = ediReeferRqmts != null ? ediReeferRqmts.getRfVentRequired() : null
                String humidityRequired = ediReeferRqmts != null ? ediReeferRqmts.getRfHumidityRequired() : null
                boolean hasReeferRqmnts = (co2Required == null && o2Required == null && humidityRequired == null) ? Boolean.FALSE : Boolean.TRUE
                boolean hasReeferError = false
                if ((!isTempRequired && StringUtils.isNotEmpty(ventRequired)) || (!isTempRequired && hasReeferRqmnts) ) {
                    new GroovyApi().registerError("Temp. Setting is missing for reefer booking. Could not post EDI.")
                    hasReeferError = true
                } else if(isTempRequired && StringUtils.isEmpty(ventRequired)) {
                    new GroovyApi().registerError("Vent Setting is missing for reefer booking. Could not post EDI.")
                    hasReeferError = true
                }
                EquipType equipType = bookingItem.getISOcode() != null ? EquipType.findEquipType(bookingItem.getISOcode()) : null
                if (equipType != null && equipType.isTemperatureControlled() && !isTempRequired && !hasReeferError) {
                    new GroovyApi().registerWarning("EDI received for dry cargo against reefer container.")
                }
                //Reefer Validation - End
                //Booking Item Validation - Begin
                if (equipType == null) {
                    new GroovyApi().registerError("EDI received without order item type. Could not post.")
                } else if (!(bookingItem.getQuantity() != null && bookingItem.getQuantity().length() > 0 && Integer.parseInt(bookingItem.getQuantity()) > 0)) {
                    new GroovyApi().registerError("EDI received without order item quantity. Could not post.")
                }

                //Booking Item Validation - End
                //AWKWARD Validation - Begin
                Dimension overDimension = bookingItem.getDimension()
                if (overDimension != null && overDimension.getFront() != null && overDimension.getFront().length() > 0) {
                    EquipType eqType = bookingItem.getISOcode() != null ? EquipType.findEquipType(bookingItem.getISOcode()) : null
                    List<EquipIsoGroupEnum> equipIsoGroupList = Arrays.asList(EquipIsoGroupEnum.PF,EquipIsoGroupEnum.PC,EquipIsoGroupEnum.PL,EquipIsoGroupEnum.UT,
                            EquipIsoGroupEnum.PT, EquipIsoGroupEnum.PS);
                    if(eqType != null && !equipIsoGroupList.contains(eqType.getEqtypIsoGroup())){
                        // bookingItem.setDimension(null)
                        overDimension.unsetFront()
                        overDimension.unsetFrontUnit()
                        new GroovyApi().registerWarning("Awkward details received against incompatible Equipment type.")
                    }
                }
                //AWKWARD Validation - End
            }

        }


    }

    private static logMsg(String inMsg) {
        LOGGER.debug(inMsg);
    }


    private ScopedBizUnit resolveLineOperator(EdiVesselVisit inEdiVesselVisit, EdiOperator inEdiOperator) {
        LOGGER.warn(" in Resolve Line Operator")
        ScopedBizUnit inLine = null

        String lineCode
        String lineCodeAgency

        try {

            if (inEdiOperator != null) {
                lineCode = inEdiOperator.getOperator()
                lineCodeAgency = inEdiOperator.getOperatorCodeAgency()
                inLine = ScopedBizUnit.resolveScopedBizUnit(lineCode, lineCodeAgency, BizRoleEnum.LINEOP)
            }

            if (inLine == null && inEdiVesselVisit != null && inEdiVesselVisit.getShippingLine() != null) {
                lineCode = inEdiVesselVisit.getShippingLine().getShippingLineCode()
                lineCodeAgency = inEdiVesselVisit.getShippingLine().getShippingLineCodeAgency()
                inLine = ScopedBizUnit.resolveScopedBizUnit(lineCode, lineCodeAgency, BizRoleEnum.LINEOP)
            }
        } catch (Exception e) {
            LOGGER.warn("Cannot Resolve Line Operator" + e)
        }
        return inLine
    }


    private CarrierVisit resolveCarrierVisit(
            @Nullable EdiVesselVisit inEdiVv,
            @Nullable Complex complex, Facility inFacility, ScopedBizUnit bkgLineOp) throws BizViolation {
        if (complex == null) {
            LOGGER.warn(" Thread Complex is Null")
        }
        String vvConvention = null
        String vvId = null
        final String ibVoyg = null
        final String obVoyg = null
        if (inEdiVv != null) {
            vvConvention = inEdiVv.getVesselIdConvention()
            if (null == vvConvention || (vvConvention != null && vvConvention.isEmpty())) {
                return null
            }
            if (vvConvention != null) {
                vvId = inEdiVv.getVesselId()
            }
            if (null == vvId || (vvId != null && vvConvention.isEmpty())) {
                return null
            }
            CarrierVisit cv = null
            VesselVisitFinder vvf = (VesselVisitFinder) Roastery.getBean(VesselVisitFinder.BEAN_ID)

            if ((inEdiVv.getInVoyageNbr()?.trim()) || (inEdiVv.getInOperatorVoyageNbr()?.trim())) {
                if (null == cv && (inEdiVv.getInVoyageNbr()?.trim())) {
                    try {
                        cv = vvf.findVesselVisitForInboundStow(complex, vvConvention, vvId, inEdiVv.getInVoyageNbr(), null, null)
                    } catch (BizViolation violation) {
                        LOGGER.error(violation)
                    }
                }
                if (null == cv && (inEdiVv.getInOperatorVoyageNbr()?.trim())) {
                    try {
                        cv = vvf.findVesselVisitForInboundStow(complex, vvConvention, vvId, inEdiVv.getInOperatorVoyageNbr(), null, null)
                    } catch (BizViolation violation) {
                        LOGGER.error(violation)
                    }
                }
            } else if ((inEdiVv.getOutVoyageNbr()?.trim()) || (inEdiVv.getOutOperatorVoyageNbr()?.trim())) {
                if (null == cv && (inEdiVv.getOutVoyageNbr()?.trim())) {
                    try {
                        cv = vvf.findOutboundVesselVisit(complex, vvConvention, vvId, inEdiVv.getOutVoyageNbr(), LineOperator.findLineOperatorById(bkgLineOp.getBzuId()), null)
                    } catch (BizViolation violation) {
                        LOGGER.error(violation)
                    }
                }
                if (null == cv && (inEdiVv.getOutOperatorVoyageNbr()?.trim())) {
                    try {
                        cv = vvf.findOutboundVesselVisit(complex, vvConvention, vvId, inEdiVv.getOutOperatorVoyageNbr(), LineOperator.findLineOperatorById(bkgLineOp.getBzuId()), null)
                    } catch (BizViolation violation) {
                        LOGGER.error(violation)
                    }
                }
            }
            LOGGER.warn("cv::" + cv)
            return cv
        }
        return null
    }

    private static int findYardUnitCount(Booking inBooking) {
        if (inBooking != null) {
            UfvTransitStateEnum[] visitState = new UfvTransitStateEnum[2]
            visitState[0] = UfvTransitStateEnum.S30_ECIN
            visitState[1] = UfvTransitStateEnum.S40_YARD
            DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT);
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_CATEGORY, UnitCategoryEnum.EXPORT))
            dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_TRANSIT_STATE, visitState))
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_DEPARTURE_ORDER, inBooking.getEqboGkey()))
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_DEPART_ORDER_SUB_TYPE, "BOOK"));
            List<UnitFacilityVisit> ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            return ufvList.size()
        }

        return null;
    }

    private static final String EDO = "EDO"
    private static final String MSG_FUNCTION_REMOVE = "R"
    private static final String MSG_FUNCTION_DELETE = "D"
    private static final String NON_VISIT = "YES"
    private static final String ORDER_PURPOSE = "OFF DOCK"
}
