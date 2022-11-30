/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.*
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.VesselVisitFinder
import com.navis.argo.business.atoms.*
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Complex
import com.navis.argo.business.reference.EquipType
import com.navis.argo.business.reference.LineOperator
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.edi.business.edimodel.EdiConsts
import com.navis.edi.business.util.StringUtil
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.orders.OrdersPropertyKeys
import com.navis.orders.business.eqorders.Booking
import com.navis.road.business.util.RoadBizUtil
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.vessel.business.schedule.VesselVisitLine
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject
import org.jetbrains.annotations.Nullable

/*
 *
 * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 10/March/2022
 *
 * Requirements :
 * #1: To check whether the Visit phase of EDI Vessel Visit is closed.
 * #2: To update the order as EDO if the requested vessel does not call the facility
 * #3: To validate the container availability in yard against requested booking and restrict booking cancellation
 * #4: To validate the doNotOverrideByEDI flag and removes hazmat/commodity details if valid
 * #5: To validate order item and quantity
 * #6: To validate the reefer requirements
 * #7: To validate the equipment type against AWK booking
 *
 * @Inclusion Location	: Incorporated as a code extension of the type EDI_POST_INTERCEPTOR.
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
 *  *  S.No    Modified Date                       Modified By                             Jira      Description
 *      1      21/11/2022   <a href="mailto:sanandaraj@servimostech.com">S Anandaraj</a>  IP-324    To validate the EDI Booking Cut-Off and Line EDI Booking Cut-Off against requested Vessel Visit
 */

class ITSBookingPostInterceptor extends AbstractEdiPostInterceptor {
    private static Logger LOGGER = Logger.getLogger(ITSBookingPostInterceptor.class)

    @Override
    void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        LOGGER.setLevel(Level.INFO)
        logMsg("Execution started.")
        if (!BookingTransactionsDocument.class.isAssignableFrom(inXmlTransactionDocument.getClass())) {
            throw BizFailure.create(OrdersPropertyKeys.ERRKEY__TYPE_MISMATCH_XMLBEAN, null, inXmlTransactionDocument.getClass().getName())
        }

        BookingTransactionsDocument bkgDocument = (BookingTransactionsDocument) inXmlTransactionDocument
        final BookingTransactionsDocument.BookingTransactions bkgTransactions = bkgDocument.getBookingTransactions()
        List<BookingTransactionDocument.BookingTransaction> bkgTranList = bkgTransactions.getBookingTransactionList()
        if (bkgTranList.size() != 1) {
            throw BizFailure.create(OrdersPropertyKeys.ERRKEY__XML_BOOKING_TRANSACTION_DOCUMENT_LENGTH_EXCEED, null, String.valueOf(bkgTranList.size()))
        }
        BookingTransactionDocument.BookingTransaction bkgTransaction = bkgTranList.get(0)
        if (bkgTransaction == null) {
            logMsg("Booking Transaction is null returning ")
            return
        }
        if (bkgTransaction != null) {
            EdiBooking ediBooking = bkgTransaction.getEdiBooking()
            String bookingNbr = ediBooking != null ? ediBooking.getBookingNbr() : null
            logMsg("Booking Nbr: " + bookingNbr)
            EdiOperator ediOp = bkgTransaction.getLineOperator()
            EdiVesselVisit ediVV = bkgTransaction.getEdiVesselVisit()
            Complex complex = ContextHelper.getThreadComplex()

            ScopedBizUnit bkgLineOp = ediOp != null && ediVV != null ? this.resolveLineOperator(ediVV, ediOp) : null
            CarrierVisit ediCv = bkgLineOp != null && ediVV != null ? this.resolveCarrierVisit(ediVV, complex, bkgLineOp) : null

            VesselVisitDetails vvd = ediCv != null && LocTypeEnum.VESSEL.equals(ediCv.getCvCarrierMode()) ?
                    VesselVisitDetails.resolveVvdFromCv(ediCv) : null
            if (vvd != null && CarrierVisitPhaseEnum.CLOSED.equals(vvd.getVvdVisitPhase())) {
                registerError("Vessel Visit " + vvd.getCvdCv().getCvId() + " is closed, cannot process EDI.")
                inParams.put(EdiConsts.SKIP_POSTER, true)
                return
            }
            if (vvd != null && bkgLineOp != null) {
                Date cutOffDate = vvd.getVvFlexDate02();
                if (vvd != null) {
                    VesselVisitLine vvl = VesselVisitLine.findVesselVisitLine(vvd, bkgLineOp)
                    if (vvl != null && vvl.getVvlineTimeActivateYard() !=null) {
                        cutOffDate = vvl.getVvlineTimeActivateYard()
                    }

                    TimeZone timeZone = ContextHelper.getThreadUserTimezone()
                    Date currentDate = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone)
                    if(cutOffDate!=null){
                        cutOffDate=ArgoUtils.convertDateToLocalDateTime(cutOffDate, timeZone)
                    }

                    //validating Vessel visit Edi cut off date and Line Cut off date
                    if(cutOffDate != null && currentDate!=null){
                        if(currentDate.after(cutOffDate)){
                            getMessageCollector().registerExceptions(BizViolation.create(PropertyKeyFactory.valueOf("Vessel Visit " + vvd.getCvdCv().getCvId() + " past EDI Booking Cut-off Line/Vessel: " +cutOffDate+ ", cannot process EDI."), (BizViolation) null))
                        }
                    }

                }

                if (NON_VISIT.equalsIgnoreCase(vvd.getVvFlexString01())) {
                    bkgTransaction.setEdiBookingType(EDO)
                    ediBooking.setBookingHandlingInstructions("Requested Vessel does not call at ITS. [Vessel: " + ediVV.getVesselName() + ". Voyage: " + ediVV.getOutVoyageNbr() + "]")
                    EqoOrderPurpose orderPurpose = ediBooking.getEqoOrderPurpose() == null ? ediBooking.addNewEqoOrderPurpose() : ediBooking.getEqoOrderPurpose()
                    if (orderPurpose != null) {
                        orderPurpose.setOrderPurposeId(ORDER_PURPOSE)
                    }
                }
            }

            Booking booking = Booking.findBookingWithoutVesselVisit(bookingNbr, bkgLineOp)
            // Gap list #9-4: 301 - Block EDI update if any container has been received (in yard) for the booking.
            if (booking != null) {
                if (booking.isUnitsReservedForBooking()) {
                    int yardUnitCount = findYardUnitCount(booking)
                    if (yardUnitCount > 0) {
                        inParams.put(EdiConsts.SKIP_POSTER, true)
                        if (bkgTransaction.getMsgFunction() != null && (MSG_FUNCTION_DELETE.equals(bkgTransaction.getMsgFunction()) || MSG_FUNCTION_REMOVE.equals(bkgTransaction.getMsgFunction()))) {
                            registerError("Container already received against booking: " + bookingNbr + ", cannot be deleted.")
                        }
                        return
                    }
                }
                // Do not override by EDI - Begin
                Map<String, String> dffMap = booking.getBookCustomFlexFields()
                if (dffMap != null) {
                    String doNotOverrideByEdi = dffMap.get("bkgCustomDFFDoNotOverrideByEDI")
                    boolean stopBkgOverride = doNotOverrideByEdi != null && "true".equalsIgnoreCase(doNotOverrideByEdi) ? Boolean.TRUE : Boolean.FALSE
                    if (stopBkgOverride) {
                        bkgTransaction.setEdiHazardArray(null)
                        List<BookingTransactionDocument.BookingTransaction.EdiBookingItem> bkgItemList = bkgTransaction.getEdiBookingItemList()
                        for (BookingTransactionDocument.BookingTransaction.EdiBookingItem bookingItem : bkgItemList) {
                            bookingItem.unsetEdiCommodity()
                        }
                        registerWarning("DoNotOverrideByEDI flag is enabled for booking: " + bookingNbr + ". Could not update Hazmat/Commodity")
                    }
                }
                // Do not override by EDI - End
                // Do not roll booking by EDI validation - Begin

                CarrierVisit bkgCv = booking.getEqoVesselVisit()
                VesselVisitDetails bkgVvd = bkgCv != null ? VesselVisitDetails.resolveVvdFromCv(bkgCv) : null
                if (bkgVvd != null && bkgVvd.getVvFlexString03() != null && "YES".equalsIgnoreCase(bkgVvd.getVvFlexString03())) {
                    registerError("DoNotRollBookingByEDI flag is enabled for booking: " + bookingNbr + ", cannot process EDI.")
                }
            }
            // Do not roll booking by EDI validation - End
            List<BookingTransactionDocument.BookingTransaction.EdiBookingItem> bookingItemList = bkgTransaction.getEdiBookingItemList()
            if (bookingItemList.size() == 0) {
                registerError("Booking " + bookingNbr + " received without order item, cannot process EDI.")
                return
            }
            //If MSG_FUNCTION_DELETE/REMOVE exist we are skipping the post

            if(MSG_FUNCTION_DELETE.equals(bkgTransaction.getMsgFunction()) || MSG_FUNCTION_REMOVE.equals(bkgTransaction.getMsgFunction())){
                logMsg("Skipping the post msg function for DELETE/REMOVE")
                return
            }
            for (BookingTransactionDocument.BookingTransaction.EdiBookingItem bookingItem : bookingItemList) {
                //Reefer Validation - Begin
                EquipType equipType = bookingItem.getISOcode() != null ? EquipType.findEquipType(bookingItem.getISOcode()) : null
                //Booking Item Validation - Begin
                boolean hasOrderQty = Boolean.TRUE
                if (equipType == null) {
                    registerError("EDI for booking " + bookingNbr + " received without order item, cannot process EDI.")
                } else if (!(bookingItem.getQuantity() != null && bookingItem.getQuantity().length() > 0 && Integer.parseInt(bookingItem.getQuantity()) > 0)) {
                    registerError("EDI for booking " + bookingNbr + " received without order item quantity, cannot process EDI.")
                    hasOrderQty = Boolean.FALSE
                }
                //Booking Item Validation - End

                if (equipType != null && hasOrderQty) {
                    boolean isTempRequired = bookingItem.getTemperatureRequired() != null && bookingItem.getTemperatureRequired().length() > 0 ? Boolean.TRUE : Boolean.FALSE
                    ReeferRqmnts ediReeferRqmts = bookingItem.getEdiReeferRqmnts() != null ? bookingItem.getEdiReeferRqmnts() : null
                    String co2Required = ediReeferRqmts != null ? ediReeferRqmts.getRfCo2Required() : null
                    String o2Required = ediReeferRqmts != null ? ediReeferRqmts.getRfO2Required() : null
                    String ventRequired = ediReeferRqmts != null ? ediReeferRqmts.getRfVentRequired() : null
                    String humidityRequired = ediReeferRqmts != null ? ediReeferRqmts.getRfHumidityRequired() : null
                    boolean hasReeferRqmnts = (co2Required == null && o2Required == null && humidityRequired == null) ? Boolean.FALSE : Boolean.TRUE
                    boolean hasReeferError = false
                    if ((!isTempRequired && StringUtils.isNotEmpty(ventRequired)) || (!isTempRequired && hasReeferRqmnts)) {
                        registerError("Required Temperature is missing for reefer booking " + bookingNbr + " (Vent Set: " + ventRequired + " | Humidity: " + humidityRequired + " | O2 Req: " + o2Required + " | CO2 Req: " + co2Required + "), cannot process EDI.")
                        hasReeferError = true
                    } else if (isTempRequired) {
                        if (StringUtils.isEmpty(ventRequired)) {
                            registerError("Vent Setting is missing for reefer booking " + bookingNbr + " (Temp. Req: " + bookingItem.getTemperatureRequired() + "), cannot process EDI.")
                            hasReeferError = true
                        }
                        if (co2Required != null && o2Required == null) {
                            registerError("EDI for booking " + bookingNbr + " does not have O2 setting (CO2 Req: " + co2Required + "), cannot process EDI.")
                            hasReeferError = true
                        } else if (co2Required == null && o2Required != null) {
                            registerError("EDI for booking " + bookingNbr + " does not have CO2 setting (O2 Req: " + o2Required + "), cannot process EDI.")
                            hasReeferError = true
                        }
                    }

                    if (equipType.isTemperatureControlled() && !isTempRequired && !hasReeferError) {
                        registerWarning("EDI for booking " + bookingNbr + " has dry cargo against reefer container (ISO Type: " + bookingItem.getISOcode() + ").")
                    }
                    //Reefer Validation - End

                    //AWKWARD Validation - Begin
                    Dimension overDimension = bookingItem.getDimension()
                    List<EquipIsoGroupEnum> equipIsoGroupList = Arrays.asList(EquipIsoGroupEnum.PF, EquipIsoGroupEnum.PC, EquipIsoGroupEnum.PL, EquipIsoGroupEnum.UT,
                            EquipIsoGroupEnum.PT, EquipIsoGroupEnum.PS)
                    if (overDimension != null && overDimension.getFront() != null && overDimension.getFront().length() > 0 && !equipIsoGroupList.contains(equipType.getEqtypIsoGroup())) {
                        overDimension.unsetFront()
                        overDimension.unsetFrontUnit()
                        registerWarning("EDI for booking " + bookingNbr + " received with awkward details against incompatible Equipment type: " + bookingItem.getISOcode() + ".")
                    }
                    //AWKWARD Validation - End
                }
            }
        }
        logMsg("Execution completed.")
    }

    private static logMsg(String inMsg) {
        LOGGER.info("ITSBookingPostInterceptor - " + inMsg)
    }

    /**
     * Resolves Line Operator based on the Operator code in the EDI
     * @param inEdiVesselVisit
     * @param inEdiOperator
     * @return
     */
    private ScopedBizUnit resolveLineOperator(EdiVesselVisit inEdiVesselVisit, EdiOperator inEdiOperator) {
        ScopedBizUnit inLine = null
        String lineCode = inEdiOperator != null ? inEdiOperator.getOperator() : null
        String lineCodeAgency = inEdiOperator != null ? inEdiOperator.getOperatorCodeAgency() : null
        try {
            if (lineCode != null && lineCodeAgency != null) {
                inLine = ScopedBizUnit.resolveScopedBizUnit(lineCode, lineCodeAgency, BizRoleEnum.LINEOP)
            }
            if (inLine == null && inEdiVesselVisit != null && inEdiVesselVisit.getShippingLine() != null) {
                lineCode = inEdiVesselVisit.getShippingLine().getShippingLineCode()
                lineCodeAgency = inEdiVesselVisit.getShippingLine().getShippingLineCodeAgency()
                if (lineCode != null) {
                    inLine = ScopedBizUnit.resolveScopedBizUnit(lineCode, lineCodeAgency, BizRoleEnum.LINEOP)
                }
            }
        } catch (Exception e) {
            LOGGER.info("Cannot Resolve Line Operator" + e)
        }
        return inLine
    }

    /**
     * Resolve Vessel Visit based on the EDI Voyage details.
     * @param inEdiVv
     * @param complex
     * @param bkgLineOp
     * @throws BizViolation
     */
    private CarrierVisit resolveCarrierVisit(
            @Nullable EdiVesselVisit inEdiVv,
            @Nullable Complex complex, ScopedBizUnit bkgLineOp) throws BizViolation {
        if (complex == null) {
            LOGGER.info(" Thread Complex is Null")
        }
        String vvConvention = inEdiVv != null ? inEdiVv.getVesselIdConvention() : null
        String vvId = StringUtil.isNotEmpty(vvConvention) ? inEdiVv.getVesselId() : null
        CarrierVisit carrierVisit = null
        if (StringUtil.isNotEmpty(vvId)) {
            VesselVisitFinder vvf = (VesselVisitFinder) Roastery.getBean(VesselVisitFinder.BEAN_ID)
            if ((inEdiVv.getInVoyageNbr()?.trim() != null) || (inEdiVv.getInOperatorVoyageNbr()?.trim() != null)) {
                if (null == carrierVisit && (inEdiVv.getInVoyageNbr()?.trim()) != null) {
                    try {
                        carrierVisit = vvf.findVesselVisitForInboundStow(complex, vvConvention, vvId, inEdiVv.getInVoyageNbr(), null, null)
                    } catch (BizViolation violation) {
                        LOGGER.info(violation)
                    }
                }
                if (null == carrierVisit && (inEdiVv.getInOperatorVoyageNbr()?.trim() != null)) {
                    try {
                        carrierVisit = vvf.findVesselVisitForInboundStow(complex, vvConvention, vvId, inEdiVv.getInOperatorVoyageNbr(), null, null)
                    } catch (BizViolation violation) {
                        LOGGER.info(violation)
                    }
                }
            } else if ((inEdiVv.getOutVoyageNbr()?.trim() != null) || (inEdiVv.getOutOperatorVoyageNbr()?.trim() != null)) {
                if (null == carrierVisit && (inEdiVv.getOutVoyageNbr()?.trim() != null)) {
                    try {
                        carrierVisit = vvf.findOutboundVesselVisit(complex, vvConvention, vvId, inEdiVv.getOutVoyageNbr(), LineOperator.findLineOperatorById(bkgLineOp.getBzuId()), null)
                    } catch (BizViolation violation) {
                        LOGGER.info(violation)
                    }
                }
                if (null == carrierVisit && (inEdiVv.getOutOperatorVoyageNbr()?.trim() != null)) {
                    try {
                        carrierVisit = vvf.findOutboundVesselVisit(complex, vvConvention, vvId, inEdiVv.getOutOperatorVoyageNbr(), LineOperator.findLineOperatorById(bkgLineOp.getBzuId()), null)
                    } catch (BizViolation violation) {
                        LOGGER.info(violation)
                    }
                }
            }
        }
        return carrierVisit
    }

    /**
     * Total number of Export Yard Units associated to the Booking.
     * @param inBooking
     * @return count of Units
     */
    private static int findYardUnitCount(Booking inBooking) {
        int yardUnitCount = 0
        if (inBooking != null) {
            UfvTransitStateEnum[] visitState = new UfvTransitStateEnum[2]
            visitState[0] = UfvTransitStateEnum.S30_ECIN
            visitState[1] = UfvTransitStateEnum.S40_YARD
            DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_CATEGORY, UnitCategoryEnum.EXPORT))
            dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_TRANSIT_STATE, visitState))
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_DEPARTURE_ORDER, inBooking.getEqboGkey()))
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_DEPART_ORDER_SUB_TYPE, "BOOK"))
            yardUnitCount = HibernateApi.getInstance().findCountByDomainQuery(dq)
        }
        return yardUnitCount
    }

    private void registerWarning(String inWarningMessage) {
        RoadBizUtil.messageCollector.appendMessage(MessageLevel.WARNING, AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, inWarningMessage)
    }

    private static final String EDO = "EDO"
    private static final String MSG_FUNCTION_REMOVE = "R"
    private static final String MSG_FUNCTION_DELETE = "D"
    private static final String NON_VISIT = "YES"
    private static final String ORDER_PURPOSE = "OFF DOCK"
}
