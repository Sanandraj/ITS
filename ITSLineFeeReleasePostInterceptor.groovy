import com.navis.argo.*
import com.navis.argo.business.api.VesselVisitFinder
import com.navis.argo.business.atoms.CalendarTypeEnum
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.calendar.ArgoCalendar
import com.navis.argo.business.calendar.ArgoCalendarEventType
import com.navis.argo.business.calendar.ArgoCalendarUtil
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Complex
import com.navis.argo.business.model.Facility
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
import com.navis.framework.util.BizViolation
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.util.RoadBizUtil
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject
import org.jetbrains.annotations.Nullable

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat

/**
 * @Copyright 2022 - Code written by WeServe LLC
 * @Author .
 *
 * Requirements :
 * #1: To check whether the Visit phase of EDI Vessel Visit is closed.
 * #2: To validate the Unit Inbound Vessel visit
 * #3: To validate the Unit Category and container availability
 * #4: To validate the LFD from EDI against unit LFD
 *
 *
 * @Inclusion Location	: Incorporated as a code extension of the type EDI_POST_INTERCEPTOR.
 * Load Code Extension to N4:
 1. Go to Administration --> System --> Code Extensions
 2. Click Add (+)
 3. Enter the values as below:
 Code Extension Name:  ITSLineFeeReleasePostInterceptor
 Code Extension Type:  EDI_POST_INTERCEPTOR
 Groovy Code: Copy and paste the contents of groovy code.
 4. Click Save button

 * Attach code extension to EDI session:
 1. Go to Administration-->EDI-->EDI configuration
 2. Select the EDI session and right click on it
 3. Click on Edit
 4. Select the extension in "Post Code Extension" tab
 5. Click on save
 *
 *
 */

class ITSLineFeeReleasePostInterceptor extends AbstractEdiPostInterceptor {
    private static final Logger LOGGER = Logger.getLogger(ITSLineFeeReleasePostInterceptor.class)

    void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        logMsg("beforeEdiPost - Execution started.")
        if (inXmlTransactionDocument == null || !ReleaseTransactionsDocument.class.isAssignableFrom(inXmlTransactionDocument.getClass())) {
            return
        }

        ReleaseTransactionsDocument relTransDoc = (ReleaseTransactionsDocument) inXmlTransactionDocument
        ReleaseTransactionsDocument.ReleaseTransactions releaseTrans = relTransDoc.getReleaseTransactions()
        List<ReleaseTransactionDocument.ReleaseTransaction> transactionList = releaseTrans.getReleaseTransactionList()

        if (transactionList == null) {
            registerError("Release Array is NULL in before EDI post method")
            LOGGER.error("Release Array is NULL in before EDI post method")
            return
        }

        if (transactionList != null && transactionList.size() == 0) {
            registerError("Release Array is NULL in before EDI post method")
            LOGGER.error("Release Array is NULL in before EDI post method")
            return
        }

        ReleaseTransactionDocument.ReleaseTransaction releaseTransaction = transactionList.get(0)
        if (releaseTransaction != null) {
            List<EdiReleaseIdentifier> releaseIdentifierList = releaseTransaction.getEdiReleaseIdentifierList()
            EdiReleaseIdentifier releaseIdentifier = releaseIdentifierList != null ? releaseIdentifierList.get(0) : null
            String releaseId = releaseIdentifier != null ? releaseIdentifier.getReleaseIdentifierNbr() : null
            String releaseIdType = releaseTransaction.getReleaseIdentifierType()
            EdiVesselVisit ediVesselVisit = releaseTransaction.getEdiVesselVisit()
            if (releaseId != null && releaseIdType != null && REL_TYPE_UNIT.equalsIgnoreCase(releaseIdType) && releaseTransaction.getEdiCode() != null) {
                logMsg("Release Identifier: " + releaseId)
                String ediDispCode = releaseTransaction.getEdiCode()
                // ScopedBizUnit line = ediVesselVisit != null ? ArgoEdiUtils.getLineOperatorNullifyIfNotExist(ediVesselVisit) : null
                CarrierVisit carrierVisit = resolveCarrierVisit(ediVesselVisit, ContextHelper.getThreadComplex())
                logMsg("carrierVisit: " + carrierVisit)
                VesselVisitDetails vvd = carrierVisit != null ? VesselVisitDetails.resolveVvdFromCv(carrierVisit) : null
                String eqNbr = releaseIdentifier != null ? releaseIdentifier.getReleaseIdentifierNbr() : null
                UnitFacilityVisit ediUfv = eqNbr != null ? findUnitFacilityVisit(eqNbr) : null

                if ("LFD".equalsIgnoreCase(ediDispCode)) {
                    //LFD Validation
                    EdiFlexFields inEdiFlexFields = releaseTransaction.getEdiFlexFields();

                    //D1 - XML LFD is NOT countable for Free Time
                    Date ediLfd = null;
                    if (inEdiFlexFields != null) {
                        String ediLfdDate = inEdiFlexFields.getUfvFlexDate04()
                        inEdiFlexFields.setUfvFlexDateTime04(ediLfdDate.concat(LFD_TIME))
                        inEdiFlexFields.setUfvFlexDate04(null)
                        logMsg("EDI Flexfield: " + inEdiFlexFields)
                        ediLfd = getDate(ediLfdDate)
                        if (ediLfd == null) {
                            registerError("No LFD found for " + releaseId + ", cannot process EDI.")
                            return
                        }
                        boolean isValidLfd = ediLfd != null ? validateDateInCalendar(ediLfd) : Boolean.FALSE
                        if (!isValidLfd) {
                            registerError("LFD update for " + releaseId + " received with Non-Countable date: " + ediLfd.getDateString() + ", cannot process EDI.")
                            return
                        }
                    }
                    //S1 - EDI Vessel Visit Validation
                    if (vvd == null) {
                        registerError("No matching vessel visit found [Vessel Id: " + ediVesselVisit.getVesselId() + " | Convention: "
                                + ediVesselVisit.getVesselIdConvention() + " | Vessel Name: " + ediVesselVisit.getVesselName() + " | In Operator Vyg: " + ediVesselVisit.getInOperatorVoyageNbr()
                                + " | Out Operator Vyg: " + ediVesselVisit.getOutOperatorVoyageNbr() + "].")
                    } else {
                        if (CarrierVisitPhaseEnum.CLOSED.equals(vvd.getVvdVisitPhase())) {
                            registerError("EDI Vessel Visit " + carrierVisit.getCvId() + " is closed.")
                        }
                    }


                    if (ediUfv == null) {
                        registerError("Requested container " + releaseId + " does not have active visit, cannot process EDI.")
                        inParams.put(EdiConsts.SKIP_POSTER, true)
                        return
                    }
                    if (UfvTransitStateEnum.S40_YARD.equals(ediUfv.getUfvTransitState())) {
                        //V2 - Container Status NOT= IMPORT
                        if (!UnitCategoryEnum.IMPORT.equals(ediUfv.getUfvUnit().getUnitCategory())) {
                            registerError("Active visit of " + releaseId + " found with non-import category, cannot process EDI.")
                            inParams.put(EdiConsts.SKIP_POSTER, true)
                            return
                        }
                        //C1 - B/L-Container (XML VVC + XML Container#) does not exist
                        CarrierVisit ufvIbVessel = ediUfv.getUfvActualIbCv()
                        logMsg("ufvIbVessel: " + ufvIbVessel)
                        if (ufvIbVessel != null && LocTypeEnum.VESSEL.equals(ufvIbVessel.getCvCarrierMode()) && CarrierVisitPhaseEnum.CLOSED.equals(ufvIbVessel.getCvVisitPhase())) {
                            registerWarning("Requested container " + releaseId + " has closed inbound Vessel Visit " + ufvIbVessel.getCvId() + ".")
                        }
                        ShippingLine ediLine = ediVesselVisit != null ? ediVesselVisit.getShippingLine() : null
                        logMsg("ediVesselVisit - Before: " + ediVesselVisit)
                        if (ediLine == null && ediUfv.getUfvUnit() != null && ediUfv.getUfvUnit().getUnitLineOperator() != null) {

                            ediLine = ediVesselVisit.addNewShippingLine()
                            ScopedBizUnit unitLine = ediUfv.getUfvUnit().getUnitLineOperator()
                            ediLine.setShippingLineCode(unitLine.getBzuScac())
                            ediLine.setShippingLineCodeAgency("SCAC")
                            ediVesselVisit.setShippingLine(ediLine)
                            logMsg("ediVesselVisit - After: " + ediVesselVisit)
                        }
                        //V3 - Container Last Due Date is NOT Null
                        if (ediUfv.getUfvPaidThruDay() != null) {
                            registerError("Storage payment found for " + releaseId + ", cannot process EDI.")
                            inParams.put(EdiConsts.SKIP_POSTER, true)
                            return
                        }
                    } else {
                        //V1 - Container is NOT In-Yard
                        registerError(releaseId + " is not in yard, cannot process EDI.")
                        inParams.put(EdiConsts.SKIP_POSTER, true)
                        return
                    }

                } else {
                    // Line fee validation
                    if (ediUfv == null) {
                        LOGGER.error("Requested container " + releaseId + " is 'NOT In-Yard' nor 'Arriving', cannot process EDI.")
                        registerError("Requested container " + releaseId + " is 'NOT In-Yard' nor 'Arriving', cannot process EDI.")
                        inParams.put(EdiConsts.SKIP_POSTER, true)
                        return
                    }
                    if (vvd == null) {
                        registerWarning("No matching vessel visit found [Vessel Id: " + ediVesselVisit.getVesselId() + " | Convention: "
                                + ediVesselVisit.getVesselIdConvention() + " | Vessel Name: " + ediVesselVisit.getVesselName() + " | In Operator Vyg: " + ediVesselVisit.getInOperatorVoyageNbr()
                                + " | Out Operator Vyg: " + ediVesselVisit.getOutOperatorVoyageNbr() + "].")
                    } else {
                        if (CarrierVisitPhaseEnum.CLOSED.equals(vvd.getVvdVisitPhase())) {
                            registerWarning("EDI Vessel Visit " + carrierVisit.getCvId() + " is closed.")
                        } else {
                            CarrierVisit ufvIbCv = ediUfv != null ? ediUfv.getUfvActualIbCv() : null
                            VesselVisitDetails ufvIbVv = ufvIbCv != null ? VesselVisitDetails.resolveVvdFromCv(ufvIbCv) : null
                            if (ufvIbVv != null && !vvd.equals(ufvIbVv)) {
                                registerWarning("EDI Vessel Visit " + carrierVisit.getCvId() + " does not match with IB carrier (" + eqNbr + ") of " + ufvIbCv.getCvId() + ".")
                            }
                        }
                    }

                }

            }
        }
        logMsg("beforeEdiPost - Execution completed.")
    }


    public static boolean validateDateInCalendar(Date inDate) {
        LOGGER.info("Inside validate date: " + inDate)
        ArgoCalendar calendar = ArgoCalendar.findDefaultCalendar(CalendarTypeEnum.STORAGE)
        Facility currentFcy = Facility.findFacility(FCY_ID)
        if (calendar != null && currentFcy != null) {
            ArgoCalendarEventType[] eventTypes = new ArgoCalendarEventType[2]
            eventTypes[0] = ArgoCalendarEventType.findByName("EXEMPT_DAY")
            eventTypes[1] = ArgoCalendarEventType.findByName("GRATIS_DAY")
            int dayCount = ArgoCalendarUtil.getEventFreeDays(inDate, inDate, currentFcy.getTimeZone(), calendar.getArgocalCalendarEvent().toList(), eventTypes)
            if (dayCount > 0) {
                return Boolean.TRUE
            }
        }
        return Boolean.FALSE
    }

    private CarrierVisit resolveCarrierVisit(
            @Nullable EdiVesselVisit inEdiVv,
            @Nullable Complex complex) throws BizViolation {
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
                    LOGGER.info("ITSLineFeeReleasePostInterceptor - Inside - inEdiVv.getInVoyageNbr()?.trim()) != null")
                    try {
                        List<CarrierVisit> carrierVisitList = vvf.findVesselVisitByIdAndVoyage(complex, ContextHelper.threadFacility, vvConvention, vvId, inEdiVv.getInVoyageNbr(), null, true)
                        LOGGER.info("ITSLineFeeReleasePostInterceptor - carrierVisitList: " + carrierVisitList)
                        if (carrierVisitList.size() > 0) {
                            carrierVisit = carrierVisitList.get(0)
                        }
                        LOGGER.info("ITSLineFeeReleasePostInterceptor - vvConvention: " + vvConvention)
                        LOGGER.info("ITSLineFeeReleasePostInterceptor - vvId: " + vvId)
                        LOGGER.info("ITSLineFeeReleasePostInterceptor - inEdiVv.getInVoyageNbr(): " + inEdiVv.getInVoyageNbr())
                        LOGGER.info("ITSLineFeeReleasePostInterceptor - carrierVisit: " + carrierVisit)
                    } catch (BizViolation violation) {
                        LOGGER.info(violation)
                    }
                } else if (null == carrierVisit && (inEdiVv.getInOperatorVoyageNbr()?.trim() != null)) {
                    LOGGER.info("ITSLineFeeReleasePostInterceptor - Inside - inEdiVv.getInOperatorVoyageNbr()?.trim() != null")
                    try {
                        List<CarrierVisit> carrierVisitList = vvf.findVesselVisitByIdAndVoyage(complex, ContextHelper.threadFacility, vvConvention, vvId, inEdiVv.getInVoyageNbr(), null, true)
                        LOGGER.info("ITSLineFeeReleasePostInterceptor - carrierVisitList: " + carrierVisitList)
                        if (carrierVisitList.size() > 0) {
                            carrierVisit = carrierVisitList.get(0)
                        }
                        LOGGER.info("ITSLineFeeReleasePostInterceptor - vvConvention: " + vvConvention)
                        LOGGER.info("ITSLineFeeReleasePostInterceptor - vvId: " + vvId)
                        LOGGER.info("ITSLineFeeReleasePostInterceptor - inEdiVv.getInOperatorVoyageNbr(): " + inEdiVv.getInOperatorVoyageNbr())
                        LOGGER.info("ITSLineFeeReleasePostInterceptor - carrierVisit: " + carrierVisit)
                    } catch (BizViolation violation) {
                        LOGGER.info(violation)
                    }
                }
            }
        }
        return carrierVisit
    }


    private void logMsg(String inMsg) {
        LOGGER.debug("ITSLineFeeReleasePostInterceptor : " + inMsg)
    }


    private UnitFacilityVisit findUnitFacilityVisit(String inUnitId) {
        String[] transitState = new String[3];
        transitState[0] = "S20_INBOUND";
        transitState[1] = "S30_ECIN";
        transitState[2] = "S40_YARD";
        if (inUnitId != null) {
            DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID, inUnitId))
            //.addDqPredicate(PredicateFactory.eq(UnitField.UFV_CATEGORY, inCategory))
                    .addDqPredicate(PredicateFactory.in(UnitField.UFV_TRANSIT_STATE, transitState));
            List<UnitFacilityVisit> ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            if (ufvList.size() > 0) {
                return ufvList.get(0)
            }
        }
        return null;
    }

    private Date getDate(String dt) throws ParseException {
        Calendar cal = Calendar.getInstance();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        cal.setTime(dateFormat.parse(dt));
        return cal.getTime();
    }

    private void registerWarning(String inWarningMessage) {
        RoadBizUtil.messageCollector.appendMessage(MessageLevel.WARNING, AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, inWarningMessage)
    }

    private static final REL_TYPE_UNIT = "UNITRELEASE"
    private static final FCY_ID = "PIERG"
    private static final LFD_TIME = "T23:59:00"
}
