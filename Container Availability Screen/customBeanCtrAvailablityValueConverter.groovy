import com.navis.argo.*
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.IImpediment
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.*
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.business.xps.model.StackStatus
import com.navis.argo.business.xps.util.StackStatusUtils
import com.navis.external.framework.beans.EBean
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.framework.presentation.table.DefaultValueConverter
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.framework.util.ValueHolder
import com.navis.inventory.InvEntity
import com.navis.inventory.InvField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.api.UnitStorageManager
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.business.units.UnitStorageManagerPea
import com.navis.services.business.api.EventManager
import com.navis.services.business.event.Event
import com.navis.services.business.rules.EventType
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

/*
 * @Author <ahref="mailto:mharikumar@weservetech.com"  >  Harikumar M</a>,
 * Date: 03/11/2022
 * Requirements:- To update the container availability details based on the impediments fees/storage charges owing.
 *  @Inclusion Location	: Incorporated as a code extension of the type BEAN_PROTOTYPE --> Paste this code (customBeanCtrAvailablityValueConverter.groovy)
 * *
 */

class customBeanCtrAvailablityValueConverter extends DefaultValueConverter implements EBean {
    @Override
    Object convert(Object inValue, MetafieldId inColumn) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("Inside the convert method no Value Holder :: ");
        return super.convert(inValue, inColumn)
    }

    @Override
    Object convert(Object inValue, MetafieldId inColumn, @Nullable ValueHolder inValueHolder) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("Inside the convert method with Value Holder :: " + inValue + " :: Column :: " + inColumn + " :: Value Holder :: $inValueHolder");
        LOGGER.debug("incolumn" + inColumn)

        if (inColumn.getFieldId().endsWith("Synthetic")) {
            LOGGER.debug("Unit ID :: " + inValueHolder.getFieldValue(UnitField.UFV_UNIT_ID))
            LOGGER.debug("infields" + inValueHolder.getFields())
            Object valueToReturn = null;
            Map invoiceParms = new HashMap<>()
            Map cueNotesParms = new HashMap<>()
            Map<String, String> spotParms = new HashMap<>()
            EventManager eventManager = (EventManager) Roastery.getBean(EventManager.BEAN_ID)

            PersistenceTemplate pt = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext())
            pt.invoke(new CarinaPersistenceCallback() {
                @Override
                protected void doInTransaction() {
                    String unitId = inValueHolder.getFieldValue(UnitField.UFV_UNIT_ID)
                    LOGGER.debug("unitId" + unitId)
                    DomainQuery dq = QueryUtils.createDomainQuery(InvEntity.UNIT_FACILITY_VISIT)
                            .addDqField(UnitField.UFV_UNIT_ID)
                            .addDqField(InvField.UFV_GKEY)
                            .addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S10_ADVISED))
                            .addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_CATEGORY, UnitCategoryEnum.IMPORT))
                            .addDqOrdering(Ordering.desc(InvField.UFV_TIME_OF_LAST_MOVE))
                            .addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID, unitId))
                    UnitFacilityVisit ufv = (UnitFacilityVisit) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq)

                    if (ufv != null) {
                        Unit unit = ufv?.getUfvUnit()
                        VesselVisitDetails vesselVisitDetails = ufv.getUfvActualIbCv() != null ? VesselVisitDetails.resolveVvdFromCv(ufv.getUfvActualIbCv()) : null
                        LOGGER.warn("vesselVisitDetails" + vesselVisitDetails)
                        LOGGER.warn("vesselVisitDetails voy" + vesselVisitDetails?.getCarrierIbVoyNbrOrTrainId())
                        LOGGER.warn("vesselVisitDetails vv name" + vesselVisitDetails?.getCarrierVehicleName())
                        LOGGER.warn("vesselVisitDetails phase" + vesselVisitDetails?.getVvdVisitPhase()?.getKey())
                        LOGGER.warn("vesselVisitDetails ETA" + vesselVisitDetails?.getCvdETA())

                        LOGGER.debug("Inside the persistence template :: UFV :: $ufv")
                        switch (inColumn.getFieldId()) {
                            case "ufvVesselNameSynthetic":
                                valueToReturn = vesselVisitDetails?.getCarrierVehicleName()
                                break;
                            case "ufvVesselStatusSynthetic":
                                valueToReturn = vesselVisitDetails?.getVvdVisitPhase()?.getKey()?.substring(2)
                                break;
                            case "ufvVesselETASynthetic":
                                valueToReturn = vesselVisitDetails?.getCvdETA()
                                break;
                            case "ufvIBVoySynthetic":
                                valueToReturn = vesselVisitDetails?.getCarrierIbVoyNbrOrTrainId();
                                break
                            case "ufvTotalAmountSynthetic":
                                invoiceParms = getInvoiceDetails(ufv, inColumn)
                                if (invoiceParms.get("totalAmount") > 0) {
                                    valueToReturn = invoiceParms.get("totalAmount")
                                }
                                break
                            case "ufvDemurrageSynthetic":
                                if (inColumn.getFieldId().equalsIgnoreCase("ufvDemurrageSynthetic")) {
                                    invoiceParms = getInvoiceDetails(ufv, inColumn)
                                    if (invoiceParms.get("demmurrageCharge") > 0) {
                                        valueToReturn = invoiceParms.get("demmurrageCharge")
                                    }
                                }
                                break
                            case "ufvDemurrageNoteSynthetic":
                                if (ufv?.getUfvPaidThruDay() != null) {
                                    valueToReturn = PAID + ufv?.getUfvPaidThruDay()
                                }
                                break
                            case "ufvExamFeeSynthetic":
                                if (inColumn.getFieldId().equalsIgnoreCase("ufvExamFeeSynthetic")) {
                                    invoiceParms = getInvoiceDetails(ufv, inColumn)
                                    if (invoiceParms.get("examAmount") > 0) {
                                        valueToReturn = invoiceParms.get("examAmount")
                                    }
                                }
                                break
                            case "ufvExamNoteSynthetic":
                                cueNotesParms = getCue(unit)
                                if (cueNotesParms.get("examNoteDate") != null) {
                                    valueToReturn = PAID + cueNotesParms.get("examNoteDate")
                                }
                                break
                            case "ufvDwellFeeSynthetic":
                                if (inColumn.getFieldId().equalsIgnoreCase("ufvDwellFeeSynthetic")) {
                                    invoiceParms = getInvoiceDetails(ufv, inColumn)
                                    if (invoiceParms.get("dwellAmount") > 0) {
                                        valueToReturn = invoiceParms.get("dwellAmount")
                                    }
                                }
                                break
                            case "ufvDwellNoteSynthetic":
                                cueNotesParms = getCue(unit)
                                if (cueNotesParms.get("dwellNoteDate") != null) {
                                    valueToReturn = PAID + cueNotesParms.get("dwellNoteDate")
                                }
                                break
                            case "ufvDischDateSynthetic":
                                if (ufv.isTransitStateBeyond(UfvTransitStateEnum.S30_ECIN)) {

                                    EventType unitDischEvnt = EventType.findEventType(EventEnum.UNIT_DISCH.getKey());
                                    if (unitDischEvnt != null) {
                                        LOGGER.warn("unitDischEvnt" + unitDischEvnt)
                                        Event event = eventManager.getMostRecentEventByType(unitDischEvnt, ufv.getUfvUnit());
                                        LOGGER.warn("event" + event)
                                        if (event != null) {
                                            valueToReturn = event?.getEvntAppliedDate()
                                        }
                                    }
                                }
                                break;

                            case "ufvDeliveredDateSynthetic":
                                if (ufv.isTransitStateBeyond(UfvTransitStateEnum.S50_ECOUT)) {
                                    EventType unitDeliveredEvnt = EventType.findEventType(EventEnum.UNIT_DELIVER.getKey());
                                    LOGGER.warn("unitDeliveredEvnt" + unitDeliveredEvnt)
                                    if (unitDeliveredEvnt != null) {
                                        Event event = eventManager.getMostRecentEventByType(unitDeliveredEvnt, ufv.getUfvUnit());
                                        LOGGER.warn("event" + event)
                                        if (event != null) {
                                            valueToReturn = event?.getEvntAppliedDate()
                                        }
                                    }
                                }

                                break;
                            case "ufvIsDeliverableSynthetic":
                                String yardBlock = ufv?.getUfvLastKnownPosition()?.getBlockName()
                                if (!StringUtils.isEmpty(yardBlock)) {
                                    GeneralReference generalReference = GeneralReference.findUniqueEntryById("ITS", "DELIVERABLE_BLOCK", yardBlock)
                                    if (generalReference != null && generalReference.getRefValue1() != null && generalReference.getRefValue1().equalsIgnoreCase("Y")) {
                                        valueToReturn = true
                                    } else {
                                        valueToReturn = false
                                    }
                                }
                                break; ;
                            case "ufvSpotSynthetic":
                                spotParms = deriveContainerSpot(ufv)
                                valueToReturn = spotParms?.get("CTR_SPOT")
                                break;
                            case "ufvCustomsHoldSynthetic":
                                String customStatus = getImpedimentForUnit(unit, "CUSTOMS")
                                if (customStatus != null) {
                                    valueToReturn = NG + NOT_RELEASED
                                } else {
                                    valueToReturn = OK
                                }
                                break;
                            case "ufvFreightHoldSynthetic":
                                String blfreightStatus = getImpedimentForUnit(unit, "LINE")
                                if (blfreightStatus != null) {
                                    valueToReturn = NG + NOT_RELEASED
                                } else {
                                    valueToReturn = OK
                                }
                                break;
                            case "ufvSpecialHoldSynthetic":
                                String specialStatusNote = ""
                                String specialStatus = getImpedimentForUnit(unit, "SPECIAL")
                                if (LocTypeEnum.TRAIN.equals(unit.getUnitRouting()?.getRtgDeclaredCv()?.getCvCarrierMode())) {
                                    specialStatusNote = TRAIN_LOAD
                                }
                                if (specialStatus) {
                                    valueToReturn = NG + specialStatusNote
                                } else {
                                    valueToReturn = OK
                                }
                                break;
                            case "ufvPierPassHoldSynthetic":
                                String pierpassStatus = getImpedimentForUnit(unit, "PIERPASS")
                                if (pierpassStatus) {
                                    valueToReturn = NG + pierpassStatus
                                } else {
                                    valueToReturn = OK
                                }
                                break;
                            case "ufvLineHoldSynthetic":
                                String lineFeeStatus = getImpedimentForUnit(unit, "LINEFEE")
                                if (lineFeeStatus) {
                                    valueToReturn = NG
                                } else {
                                    valueToReturn = OK
                                }
                                break;
                            case "ufvExamHoldSynthetic":
                                String examStatus = getImpedimentForUnit(unit, "EXAM")
                                if (examStatus) {
                                    valueToReturn = NG
                                } else {
                                    valueToReturn = OK
                                }
                                break;
                            case "ufvContainerAvailabilitySynthetic":
                                String isHoldExisit = getImpedimentForUnit(unit, null)
                                if (isHoldExisit != null) {
                                    valueToReturn = NO
                                    break;
                                }
                                invoiceParms = getInvoiceDetails(ufv, inColumn)
                                if (invoiceParms.get("totalAmount") > 0) {
                                    valueToReturn = NO
                                    break;
                                }
                                spotParms = deriveContainerSpot(ufv)
                                if (spotParms.get("SPOT_STATUS_NOTE") != null) {
                                    valueToReturn = NO
                                    break;
                                }
                                valueToReturn = YES
                                break;
                        }
                        LOGGER.debug("Inside the persistence template :: Value to return  :: $valueToReturn")
                    }
                }
            })
            return valueToReturn
        }

        return super.convert(inValue, inColumn, inValueHolder)
    }

    @Override
    String getDetailedDiagnostics() {
        return "customBeanCtrAvailablityValueConverter"
    }

    private Map<String, String> deriveContainerSpot(UnitFacilityVisit ufv) {
        Map<String, String> spotParams = new HashMap<>()
        String containerSpot = null
        String spotStatusNote = null

        switch (ufv.getUfvTransitState().getKey()) {
            case UfvTransitStateEnum.S20_INBOUND.getKey():
                containerSpot = CTR_SPOT_ARRIVING
                spotStatusNote = CTR_SPOT_ARRIVING
                break;

            case UfvTransitStateEnum.S30_ECIN.getKey():
                containerSpot = NG + CTR_SPOT_SPOTTING
                spotStatusNote = CTR_SPOT_SPOTTING
                break;
            case UfvTransitStateEnum.S40_YARD.getKey():
                if ("Y-PIERG-UTL".equalsIgnoreCase(ufv.getUfvLastKnownPosition()?.getPosName())) {
                    spotStatusNote = UNABLE_TO_LOCATE
                }
                LocPosition locPosition = ufv.getUfvLastKnownPosition()
                if (locPosition != null && LocTypeEnum.YARD.equals(locPosition.getPosLocType()) && locPosition.getPosBin() != null) {
                    StackStatus stackStatus = StackStatus.findStackStatus(locPosition.getPosBin(), ContextHelper.getThreadYard())
                    if (stackStatus != null && stackStatus.getStackstatusStatusChars() != null && "C".equalsIgnoreCase(StackStatusUtils.getProtectedStatus(stackStatus.getStackstatusStatusChars()).toString())) {
                        spotStatusNote = AREA_CLOSED
                    }
                }
                String yardBlock = ufv.getUfvLastKnownPosition()?.getBlockName()
                if (!StringUtils.isEmpty(yardBlock)) {
                    GeneralReference generalReference = GeneralReference.findUniqueEntryById("ITS", "DELIVERABLE_BLOCK", yardBlock)
                    if (generalReference != null && generalReference.getRefValue1() != null && generalReference.getRefValue1().equalsIgnoreCase("N")) {
                        spotStatusNote = UNDELIVERABLE_SPOT
                    }
                }
                if (spotStatusNote != null) {
                    containerSpot = NG + spotStatusNote
                } else {
                    containerSpot = OK + StringUtils.substringAfter(ufv.getUfvLastKnownPosition()?.getPosName(), "Y-PIERG-")
                }
                break;
            case UfvTransitStateEnum.S50_ECOUT.getKey():
                containerSpot = NG + StringUtils.substringAfter(ufv.getUfvLastKnownPosition()?.getPosName(), "Y-PIERG-")
                spotStatusNote = DEPARTING
                break;
            case UfvTransitStateEnum.S60_LOADED.getKey():
            case UfvTransitStateEnum.S70_DEPARTED.getKey():
                containerSpot = NG + CTR_SPOT_DEPARTED
                spotStatusNote = CTR_SPOT_DEPARTED
                break;

        }

        spotParams.put("CTR_SPOT", containerSpot)
        spotParams.put("SPOT_STATUS_NOTE", spotStatusNote)
        return spotParams;
    }


    private Map getInvoiceDetails(UnitFacilityVisit ufv, MetafieldId inColumn) {
        Map responseMap = new LinkedHashMap()
        Double demmurrageCharge = 0.0
        Double examAmount = 0.0
        Double dwellAmount = 0.0
        Double totalAmount = 0.0
        if (inColumn.getFieldId().equalsIgnoreCase("ufvDemurrageSynthetic")
                || inColumn.getFieldId().equalsIgnoreCase("ufvExamFeeSynthetic")
                || inColumn.getFieldId().equalsIgnoreCase("ufvDwellFeeSynthetic")
                || inColumn.getFieldId().equalsIgnoreCase("ufvContainerAvailabilitySynthetic")) {
            UnitStorageManagerPea storageManager = (UnitStorageManagerPea) Roastery.getBean(UnitStorageManager.BEAN_ID);
            EdiInvoice ediInvoice
            try {
                ediInvoice = storageManager.getInvoiceForUnit(ufv, ArgoUtils.timeNow(), IMPORT_PRE_PAY, (String) null, ufv.getUfvUnit().getUnitLineOperator(), (ScopedBizUnit) null, (String) null, ArgoUtils.timeNow(), "INQUIRE");
                LOGGER.warn("ediInvoice" + ediInvoice)

            } catch (BizViolation | BizFailure bv) {
                LOGGER.debug("BizViolation" + bv)
            }


            if (ediInvoice != null) {
                List<InvoiceCharge> chargeList = ediInvoice.getInvoiceChargeList();
                LOGGER.warn("chargeList" + chargeList)
                chargeList.each {
                    charge ->
                        if (ChargeableUnitEventTypeEnum.LINE_STORAGE.getKey().equals(charge.getChargeEventTypeId())) {
                            demmurrageCharge = demmurrageCharge + charge.getTotalCharged()
                        } else {
                            if ("TAILGATE_EXAM_REQUIRED".equals(charge.getChargeEventTypeId())) {
                                examAmount = examAmount + charge.getTotalCharged()
                            } else if ("VACIS_INSPECTION_REQUIRED".equals(charge.getChargeEventTypeId())) {
                                examAmount = examAmount + charge.getTotalCharged()
                            } else if ("UNIT_EXTENDED_DWELL".equals(charge.getChargeEventTypeId())) {
                                dwellAmount = dwellAmount + charge.getTotalCharged()
                            }
                        }
                        LOGGER.warn("charge" + charge)
                }
            }

            if (demmurrageCharge > 0 || examAmount > 0 || dwellAmount > 0) {
                totalAmount = demmurrageCharge + examAmount + dwellAmount
                responseMap.put("totalAmount", totalAmount)
            }

            responseMap.put("demmurrageCharge", demmurrageCharge)
            responseMap.put("examAmount", examAmount)
            responseMap.put("dwellAmount", dwellAmount)

            LOGGER.warn("demmurrageCharge" + demmurrageCharge)
            LOGGER.warn("examAmount" + examAmount)
            LOGGER.warn("dwellAmount" + dwellAmount)
        }
        return responseMap
    }

    private Map getCue(Unit unit) {
        Map notesMap = new LinkedHashMap()
        Date dwellNoteDate = null
        Date examNoteDate = null
        DomainQuery cueDQ = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT)
                .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_UNIT_GKEY, unit.getUnitGkey()))
                .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_STATUS, "INVOICED"))
                .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_EVENT_TYPE, ["UNIT_EXTENDED_DWELL", "TAILGATE_EXAM_REQUIRED", "VACIS_INSPECTION_REQUIRED"]))
                .addDqOrdering(Ordering.asc(ArgoExtractField.BEXU_CHANGED))
        List<ChargeableUnitEvent> cueList = (List<ChargeableUnitEvent>) HibernateApi.getInstance().findEntitiesByDomainQuery(cueDQ)
        if (!CollectionUtils.isEmpty(cueList)) {
            for (ChargeableUnitEvent cue : cueList) {
                if ("UNIT_EXTENDED_DWELL".equals(cue.getBexuEventType())) {
                    dwellNoteDate = cue.getBexuChanged()
                } else if ("TAILGATE_EXAM_REQUIRED".equals(cue.getBexuEventType())) {
                    examNoteDate = cue.getBexuChanged()
                } else if ("VACIS_INSPECTION_REQUIRED".equals(cue.getBexuEventType())) {
                    examNoteDate = cue.getBexuChanged()
                }
                notesMap.put("dwellNoteDate", dwellNoteDate)
                notesMap.put("examNoteDate", examNoteDate)
            }

        }
        return notesMap
    }

    private String getImpedimentForUnit(Unit unit, String flagView) {
        ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
        Collection<IImpediment> impedimentsCollection = (Collection<IImpediment>) servicesManager.getImpedimentsForEntity(unit)
        LOGGER.debug("impedimentsCollection   " + impedimentsCollection.toString())
        String flagType = null
        String flagActive = null
        String[] flags = null
        if (flagView == null) {
            flags = ["CUSTOMS", "LINE", "EXAM", "SPECIAL", "PIERPASS", "LINEFEE"]
        } else {
            flags = [flagView]
        }

        for (IImpediment impediment : impedimentsCollection) {
            if (impediment != null && FlagStatusEnum.ACTIVE.equals(impediment.getStatus())) {
                flagType = impediment.getFlagType()?.getHpvId()
                if (flagType != null && flags.contains(flagType)) {
                    flagActive = impediment.getFlagType()?.getId()
                }
            }
        }
        return flagActive
    }

    private static final String DEPARTING = "DEPARTING"
    private static final String AREA_CLOSED = "AREA CLOSED"
    private static final String UNDELIVERABLE_SPOT = "UNDELIVERABLE SPOT"
    private static final String CTR_SPOT_ARRIVING = "ARRIVING"
    private static final String CTR_SPOT_DEPARTED = "DELIVERED"
    private static final String CTR_SPOT_SPOTTING = "SPOTTING"
    private static final String UNABLE_TO_LOCATE = "UNABLE TO LOCATE"
    private static final String NG = "NG "
    private static final String OK = "OK "
    private static final String YES = "YES "
    private static final String NO = "NO "
    private static final String PAID = "PAID "
    private static final String TRAIN_LOAD = "TRAIN LOAD"
    private static final String NOT_RELEASED = "NOT RELEASED"
    private String IMPORT_PRE_PAY = "IMPORT_PRE_PAY";
    private static final Logger LOGGER = Logger.getLogger(customBeanCtrAvailablityValueConverter.class)
}
