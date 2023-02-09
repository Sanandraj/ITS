/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.*
import com.navis.argo.business.api.VesselVisitFinder
import com.navis.argo.business.atoms.*
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Complex
import com.navis.argo.business.model.Facility
import com.navis.argo.business.reference.EquipType
import com.navis.argo.business.reference.LineOperator
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.business.reference.SpecialStow
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
 * Requirements : This groovy is used to update the msg function and EDO Type based on the criteria.
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
 *S.No    Modified Date    Modified By         Jira      Description
 *   1      05/02/2022       madhavanm         IP-94     Refeer and OOG validation
 */
 */

class ITSBookingPostInterceptor extends AbstractEdiPostInterceptor {
    private static final Logger LOGGER = Logger.getLogger(this.class)

    @Override
    void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        LOGGER.setLevel(Level.DEBUG)
        logMsg("Execution started.")
        if (!BookingTransactionsDocument.class.isAssignableFrom(inXmlTransactionDocument.getClass())) {
            throw BizFailure.create(OrdersPropertyKeys.ERRKEY__TYPE_MISMATCH_XMLBEAN, null, inXmlTransactionDocument.getClass().getName());
        }

        BookingTransactionsDocument bkgDocument = (BookingTransactionsDocument) inXmlTransactionDocument;
        final BookingTransactionsDocument.BookingTransactions bkgtrans = bkgDocument.getBookingTransactions();
        //todo, using deprecated method, see getBookingTransactionList method
        final BookingTransactionDocument.BookingTransaction[] bkgtransArray = bkgtrans.getBookingTransactionArray();
        if (bkgtransArray.length != 1) {
            throw BizFailure.create(OrdersPropertyKeys.ERRKEY__XML_BOOKING_TRANSACTION_DOCUMENT_LENGTH_EXCEED, null, String.valueOf(bkgtransArray.length));
        }
        BookingTransactionDocument.BookingTransaction bkgTrans = bkgtransArray[0];
        LOGGER.debug("bkgTrans1:: " + bkgTrans)
        //todo, till this can be replaced when getBookingTransactionList is used instead of getBookingTransactionArray
        if (bkgTrans == null) {
            logMsg("Booking Transaction is null returning ");
            return;
        }
        if (bkgTrans != null) {
            // todo use null checks (use .? notion)
            EdiBooking ediBooking = bkgTrans.getEdiBooking()
            String bookingNbr = ediBooking != null ? ediBooking.getBookingNbr() : null;
            logMsg("Booking Nbr: " + bookingNbr)
            EdiOperator ediOp = bkgTrans.getLineOperator();
            Booking booking = null;
            EdiVesselVisit ediVV = bkgTrans.getEdiVesselVisit();
            Facility facility = ContextHelper.getThreadFacility();
            // todo, this can be null based on the scope od code-extension deployment
            Complex complex = ContextHelper.getThreadComplex();

            //todo, you are calling method without null check on ediVV
            ScopedBizUnit bkgLineOp = ediOp != null ? this.resolveLineOperator(ediVV, ediOp) : null;
            //todo, you are calling method without null check on ediVV, facility and bkgLineOp
            CarrierVisit ediCv = bkgLineOp != null ? this.resolveCarrierVisit(ediVV, complex, facility, bkgLineOp) : null;
            log(Level.DEBUG, "EDI Line Operator: " + bkgLineOp)
            log(Level.DEBUG, "EDI Carrier Visit: " + ediCv)
            VesselVisitDetails vvd = ediCv != null && LocTypeEnum.VESSEL.equals(ediCv.getCvCarrierMode()) ?
                    VesselVisitDetails.resolveVvdFromCv(ediCv) : null;
            if (vvd != null && CarrierVisitPhaseEnum.CLOSED.equals(vvd.getVvdVisitPhase())) {
                registerError("Vessel Visit " + vvd.getCvdCv().getCvId() + " is closed, cannot process EDI.")
                inParams.put(EdiConsts.SKIP_POSTER, true)
                return
            }
            if (vvd != null) {
                log(Level.DEBUG, "EDI Vessel Visit: " + vvd)
                // To validate EDI Booking Cut-Off - Begin
                Date currentDate = new Date()//todo, there is no null check on bkgLineOp
                VesselVisitLine vvLine = VesselVisitLine.findVesselVisitLine(vvd, bkgLineOp)
                Date ediLineCutoffDate = vvLine != null ? vvLine.getVvlineTimeActivateYard() : null
                log(Level.DEBUG, "EDI Booking Cut-Off: " + vvd.getVvFlexDate02())
                log(Level.DEBUG, "EDI Line Booking Cut-Off (" + bkgLineOp.getBzuId() + "): " + ediLineCutoffDate)
                if (ediLineCutoffDate != null && currentDate.after(ediLineCutoffDate)) {
                    registerError("Vessel Visit " + vvd.getCvdCv().getCvId() + " past Line EDI Booking Cut-off " + ediLineCutoffDate + " for line " + bkgLineOp.getBzuId() + ", cannot process EDI.")
                    return
                } else {
                    Date ediCutoffDate = vvd.getVvFlexDate02()
                    if (ediCutoffDate != null && currentDate.after(ediCutoffDate)) {
                        registerError("Vessel Visit " + vvd.getCvdCv().getCvId() + " past the default EDI Booking Cut-off " + ediCutoffDate + " for line " + bkgLineOp.getBzuId() + ", cannot process EDI.")
                        return
                    }
                }

                log(Level.DEBUG, "Non-Visit Vessel: " + vvd.getVvFlexString01())
                // To validate EDI Booking Cut-Off - End
                if (NON_VISIT.equalsIgnoreCase(vvd.getVvFlexString01())) {
                    bkgTrans.setEdiBookingType(EDO)
                    ediBooking.setBookingHandlingInstructions("Requested Vessel does not call at ITS. [Vessel: " + ediVV.getVesselName() + ". Voyage: " + ediVV.getOutVoyageNbr() + "]")
                    EqoOrderPurpose orderPurpose = ediBooking.getEqoOrderPurpose() == null ? ediBooking.addNewEqoOrderPurpose() : ediBooking.getEqoOrderPurpose()
                    if (orderPurpose != null) {
                        orderPurpose.setOrderPurposeId(ORDER_PURPOSE)
                    }
                }
            }
            booking = Booking.findBookingWithoutVesselVisit(bookingNbr, bkgLineOp)
            LOGGER.debug("Booking:: " + booking)

            /*..EDI Special Stow Validation - Start..*/
            SpecialStow specialStow = null;
            LOGGER.debug("SpecialStow:: " + specialStow)
            if (ediBooking?.getSpecialStowInstructionsList() != null && ediBooking?.getSpecialStowInstructionsList().size() != 0) {
                SpecialStowInstruction specialStowInstructionList = ediBooking?.getSpecialStowInstructionsList()?.first()
                LOGGER.debug("getSpecialStowInstructionsList():: " + ediBooking?.getSpecialStowInstructionsList())
                LOGGER.debug("specialStowInstructionList:: " + specialStowInstructionList)
                LOGGER.debug("getId:: " + specialStowInstructionList?.getId())
                if (booking != null && specialStowInstructionList != null) {
                    specialStow = booking.getEqoSpecialStow()
                    LOGGER.debug("specialStow:: " + specialStow)
                    LOGGER.debug("Booking.getStwId():: " + specialStow.getStwId())
                    LOGGER.debug("Edibooking.getId():: " + specialStowInstructionList.getId())
                    if (specialStow.getStwId() != specialStowInstructionList.getId()) {
                        registerWarning("Special Stow " + specialStowInstructionList.getId() + " provided in EDI for " + ediBooking.getBookingNbr() + " does not match with booking special stow " + specialStow.getStwId())
                        bkgTrans?.getEdiBooking()?.removeSpecialStowInstructions(0)
                        LOGGER.debug("bkgTrans2:: " + bkgTrans)
                    }
                }
            }
            /*..EDI Special Stow Validation - End..*/


            List<BookingTransactionDocument.BookingTransaction.EdiBookingItem> bookingItemList = bkgTrans.getEdiBookingItemList()
            if (bookingItemList.size() == 0) {
                registerError("Booking " + bookingNbr + " received without order item, cannot process EDI.")
                return
            }

            /*  Booking update logic for Reefer Requirements- start */
            LOGGER.debug("tallyReceive1:: " + booking.getEqoTally())
            //LOGGER.debug()
            for (BookingTransactionDocument.BookingTransaction.EdiBookingItem ediBkgItem : bookingItemList) {
                LOGGER.debug("Inside of bookingItemList")
                boolean isTempRequired = ediBkgItem?.getTemperatureRequired() != null && ediBkgItem?.getTemperatureRequired().length() > 0 ? Boolean.TRUE : Boolean.FALSE
                if (booking != null && booking.isUnitsReservedForBooking()) {
                    if (booking.getEqoTally() > 0) {
                        if (!booking.getEqoHasReefers()) { // Non-Reefer booking
                            if (isTempRequired) {   //EDI booking item Reefer
                                registerError("EDI received with Set Temp (" + ediBkgItem?.getTemperatureRequired() + ") for Non-Reefer booking " + bookingNbr + " that has container already received, cannot process EDI")
                                return
                            }
                        } else if (booking.getEqoHasReefers()) {  //Reefer booking
                            if (!isTempRequired) {
                                registerError("EDI received without Set Temp for Reefer booking " + bookingNbr + " that has container already received, cannot process EDI")
                                return
                            }
                        }
                    }
                }
                /* Update OOG Flag set and unset overdimension value*/
                
                LOGGER.debug("Inside of overDimension")
                if (ediBkgItem != null && booking != null) {
                    Dimension dimension = ediBkgItem.getDimension()
                    if (dimension != null) {
                        booking.setIsEqoOOD(true)
                        ediBkgItem.unsetDimension()
                    }
                }

              

                /* if(ediBkgItem != null && booking != null){
                       LOGGER.debug("Inside of overDimension")
                       Dimension dimension = ediBkgItem.getDimension()
                       LOGGER.debug("overDimension:: "+dimension)
                       if(dimension != null){
                         Set bkgEqOrdItems = booking.getEqboOrderItems()
                           LOGGER.debug("bkgEqOrdItems:: "+bkgEqOrdItems)
                           if(bkgEqOrdItems != null){
                           for(EquipmentOrderItem ordItem : bkgEqOrdItems){
                               LOGGER.debug("ordItem:: "+ordItem)
                               LOGGER.debug("ediBkgItem.getISOcode:: "+ediBkgItem.getISOcode())
                               LOGGER.debug("ediBkgItem.getEqtypId:: "+ordItem ?.getEqoiSampleEquipType() ?.getEqtypId())
                               if(ordItem != null && ordItem.getEqoiSampleEquipType() != null && ediBkgItem.getISOcode().equals(ordItem ?.getEqoiSampleEquipType() ?.getEqtypId())){
                                   LOGGER.debug("Inside of overDimension")
                                   ordItem.setEqoiIsOog(true)
                                   ediBkgItem.unsetDimension()

                               }
                           }
                           }
                       }
                   }*/
            }
            /* Booking update logic for Reefer Requirements- end*/

            LOGGER.debug("bkgTrans2:: " + bkgTrans)

            log(Level.DEBUG, "Existing booking: " + booking)
            // Gap list #9-4: 301 - Block EDI update if any container has been received (in yard) for the booking.
            if (booking != null) {
                if (booking.isUnitsReservedForBooking()) {
                    int yardUnitCount = findYardUnitCount(booking)
                    if (yardUnitCount != null && yardUnitCount > 0) {
                        inParams.put(EdiConsts.SKIP_POSTER, true)
                        if (bkgTrans.getMsgFunction() != null && (MSG_FUNCTION_DELETE.equals(bkgTrans.getMsgFunction()) || MSG_FUNCTION_REMOVE.equals(bkgTrans.getMsgFunction()))) {
                            registerError("Container already received against booking: " + bookingNbr + ", cannot be deleted.")
                        }
                        return;
                    }
                }
                // Do not override by EDI - Begin
                Map<String, String> dffMap = booking.getBookCustomFlexFields()
                if (dffMap != null) {
                    String doNotOverrideByEdi = dffMap.get("bkgCustomDFFDoNotOverrideByEDI")
                    boolean stopBkgOverride = doNotOverrideByEdi != null && "true".equalsIgnoreCase(doNotOverrideByEdi) ? Boolean.TRUE : Boolean.FALSE
                    log(Level.DEBUG, "doNotOverrideByEdi: " + doNotOverrideByEdi)
                    if (stopBkgOverride) {
                        bkgTrans.setEdiHazardArray(null)
                        BookingTransactionDocument.BookingTransaction.EdiBookingItem[] itemArray = bkgTrans.getEdiBookingItemArray()
                        for (BookingTransactionDocument.BookingTransaction.EdiBookingItem bookingItem : itemArray) {
                            bookingItem.unsetEdiCommodity()
                        }
                        registerWarning("DoNotOverrideByEDI flag is enabled for booking: " + bookingNbr + ". Could not update Hazmat/Commodity")
                    }
                }
                // Do not override by EDI - End

                // Do not roll booking by EDI validation - Begin

                CarrierVisit bkgCv = booking.getEqoVesselVisit();
                VesselVisitDetails bkgVvd = bkgCv != null ? VesselVisitDetails.resolveVvdFromCv(bkgCv) : null
                if (bkgVvd != null && bkgVvd.getVvFlexString03() != null && "YES".equalsIgnoreCase(bkgVvd.getVvFlexString03())) {
                    log(Level.DEBUG, "DoNotRollBookingByEDI: " + bkgVvd.getVvFlexString03())
                    registerError("DoNotRollBookingByEDI flag against Vessel Visit " + bkgCv.getCvId() + " for booking " + bookingNbr + " is active, cannot process EDI.")
                }

            }
            // Do not roll booking by EDI validation - End

            for (BookingTransactionDocument.BookingTransaction.EdiBookingItem bookingItem : bookingItemList) {
                //Reefer Validation - Begin
                log(Level.DEBUG, "bookingItem: " + bookingItem)
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
                    log(Level.DEBUG, "isTempRequired: " + isTempRequired)
                    log(Level.DEBUG, "ventRequired: " + ventRequired)
                    log(Level.DEBUG, "humidityRequired: " + humidityRequired)
                    log(Level.DEBUG, "o2Required: " + o2Required)
                    log(Level.DEBUG, "co2Required: " + co2Required)
                    boolean hasReeferError = false


                    /*  //Booking update logic for Reefer Requirements- start
                   // LOGGER.debug(" Booking update logic for Reefer Requirements- start")

                    if (booking != null){
                        LOGGER.debug("getEqboOrderItems:: "+booking.getEqboOrderItems())
                        LOGGER.debug("booking:: "+booking)
                        Long tallyReceive = booking.getEqoTallyReceive()
                        LOGGER.debug("tallyReceive1:: "+tallyReceive)

                        LOGGER.debug("getEqoHasReefers:: "+booking.getEqoHasReefers())
                        if(tallyReceive >0){
                            if(!booking.getEqoHasReefers()){ // Non-Reefer booking
                                if(isTempRequired){   //EDI booking item Reefer
                                    registerError("EDI received with Set Temp (" + bookingItem.getTemperatureRequired() + ") for Non-Reefer booking " + bookingNbr + " that has container already received, cannot process EDI")
                                    return
                                }
                            }else if (booking.getEqoHasReefers()){  //Reefer booking
                                if(!isTempRequired){
                                    registerError("EDI received without Set Temp for Reefer booking " + bookingNbr + " that has container already received, cannot process EDI")
                                    return
                                }
                            }
                        }
                    }
                    // Booking update logic for Reefer Requirements- end */


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
                            EquipIsoGroupEnum.PT, EquipIsoGroupEnum.PS);
                    LOGGER.debug("equipIsoGroupList:: " + equipIsoGroupList)
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
        LOGGER.debug("ITSBookingPostInterceptor - " + inMsg);
    }

    private ScopedBizUnit resolveLineOperator(EdiVesselVisit inEdiVesselVisit, EdiOperator inEdiOperator) {
        ScopedBizUnit inLine = null;
        String lineCode = null;
        String lineCodeAgency = null;
        try {
            if (inEdiOperator != null) {
                lineCode = inEdiOperator.getOperator();
                lineCodeAgency = inEdiOperator.getOperatorCodeAgency();
                if (lineCode != null) {
                    inLine = ScopedBizUnit.resolveScopedBizUnit(lineCode, lineCodeAgency, BizRoleEnum.LINEOP);
                }
            }
            if (inLine == null && inEdiVesselVisit != null && inEdiVesselVisit.getShippingLine() != null) {
                lineCode = inEdiVesselVisit.getShippingLine().getShippingLineCode()
                lineCodeAgency = inEdiVesselVisit.getShippingLine().getShippingLineCodeAgency()
                if (lineCode != null) {
                    inLine = ScopedBizUnit.resolveScopedBizUnit(lineCode, lineCodeAgency, BizRoleEnum.LINEOP)
                }
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
        CarrierVisit carrierVisit = null;
        if (inEdiVv != null) {
            vvConvention = inEdiVv.getVesselIdConvention()
            if (StringUtil.isNotEmpty(vvConvention)) {
                vvId = inEdiVv.getVesselId()
                if (StringUtil.isNotEmpty(vvId)) {
                    VesselVisitFinder vvf = (VesselVisitFinder) Roastery.getBean(VesselVisitFinder.BEAN_ID)
                    if ((inEdiVv.getInVoyageNbr()?.trim() != null) || (inEdiVv.getInOperatorVoyageNbr()?.trim() != null)) {
                        if (null == carrierVisit && (inEdiVv.getInVoyageNbr()?.trim()) != null) {
                            try {
                                carrierVisit = vvf.findVesselVisitForInboundStow(complex, vvConvention, vvId, inEdiVv.getInVoyageNbr(), null, null)
                            } catch (BizViolation violation) {
                                LOGGER.error(violation)
                            }
                        }
                        if (null == carrierVisit && (inEdiVv.getInOperatorVoyageNbr()?.trim() != null)) {
                            try {
                                carrierVisit = vvf.findVesselVisitForInboundStow(complex, vvConvention, vvId, inEdiVv.getInOperatorVoyageNbr(), null, null)
                            } catch (BizViolation violation) {
                                LOGGER.error(violation)
                            }
                        }
                    } else if ((inEdiVv.getOutVoyageNbr()?.trim() != null) || (inEdiVv.getOutOperatorVoyageNbr()?.trim() != null)) {
                        if (null == carrierVisit && (inEdiVv.getOutVoyageNbr()?.trim() != null)) {
                            try {
                                carrierVisit = vvf.findOutboundVesselVisit(complex, vvConvention, vvId, inEdiVv.getOutVoyageNbr(), LineOperator.findLineOperatorById(bkgLineOp.getBzuId()), null)
                            } catch (BizViolation violation) {
                                LOGGER.error(violation)
                            }
                        }
                        if (null == carrierVisit && (inEdiVv.getOutOperatorVoyageNbr()?.trim() != null)) {
                            try {
                                carrierVisit = vvf.findOutboundVesselVisit(complex, vvConvention, vvId, inEdiVv.getOutOperatorVoyageNbr(), LineOperator.findLineOperatorById(bkgLineOp.getBzuId()), null)
                            } catch (BizViolation violation) {
                                LOGGER.error(violation)
                            }
                        }
                    }
                    //LOGGER.warn("cv::" + carrierVisit)
                }
            }
        }
        return carrierVisit;
    }

    private static int findYardUnitCount(Booking inBooking) {
        int yardUnitCount = 0;
        if (inBooking != null) {
            UfvTransitStateEnum[] visitState = new UfvTransitStateEnum[2]
            visitState[0] = UfvTransitStateEnum.S30_ECIN
            visitState[1] = UfvTransitStateEnum.S40_YARD
            DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT);
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_CATEGORY, UnitCategoryEnum.EXPORT))
            dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_TRANSIT_STATE, visitState))
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_DEPARTURE_ORDER, inBooking.getEqboGkey()))
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_DEPART_ORDER_SUB_TYPE, "BOOK"));
            yardUnitCount = HibernateApi.getInstance().findCountByDomainQuery(dq);
        }
        return yardUnitCount;
    }

    private void registerWarning(String inWarningMessage) {
        RoadBizUtil.messageCollector.appendMessage(MessageLevel.WARNING, AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, inWarningMessage);
    }

    private static final String EDO = "EDO"
    private static final String MSG_FUNCTION_REMOVE = "R"
    private static final String MSG_FUNCTION_DELETE = "D"
    private static final String NON_VISIT = "YES"
    private static final String ORDER_PURPOSE = "OFF DOCK"
}
