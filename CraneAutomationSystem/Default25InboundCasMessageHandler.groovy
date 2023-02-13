/*
 * Copyright (c) 2013 Navis LLC. All Rights Reserved.
 *
 */

package CraneAutomationSystem

import com.navis.argo.ArgoPropertyKeys
import com.navis.argo.ArgoRefField
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.IEventType
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.*
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.model.VisitDetails
import com.navis.argo.business.reference.Container
import com.navis.argo.business.reference.EqComponent
import com.navis.argo.business.reference.EquipDamageType
import com.navis.argo.business.reference.EquipType
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.snx.IPropertyResolver
import com.navis.external.argo.AbstractGroovyWSCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizViolation
import com.navis.framework.util.CarinaUtils
import com.navis.inventory.InvField
import com.navis.inventory.InventoryField
import com.navis.inventory.InventoryPropertyKeys
import com.navis.inventory.MovesField
import com.navis.inventory.business.api.*
import com.navis.inventory.business.atoms.*
import com.navis.inventory.business.imdg.ObservedPlacard
import com.navis.inventory.business.imdg.Placard
import com.navis.inventory.business.moves.WorkInstruction
import com.navis.inventory.business.units.*
import com.navis.inventory.util.position.IPositionable
import com.navis.inventory.util.position.SavedValuePositionable
import com.navis.spatial.business.api.IBinModel
import com.navis.spatial.business.model.AbstractBin
import com.navis.spatial.business.model.block.BinModelHelper
import com.navis.vessel.business.api.IVesselPositionableFinder
import com.navis.vessel.business.operation.VesselClass
import com.navis.xpscache.business.atoms.EquipBasicLengthEnum
import com.navis.xpscache.constants.BentoMessageAttribute
import com.navis.xpscache.constants.BentoMessageAttributeGetNextLoadPosition
import com.navis.yard.business.atoms.YardBlockTypeEnum
import com.navis.yard.business.model.AbstractYardBlock
import groovy.xml.MarkupBuilder
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * This groovy is used to receive an inbound message from a CAS system.
 * This specific version is for N4 version 2.5 and does not support the unitPositionUpdateMessage request type, only the unitCaptureMessage
 *
 * @author <a href="mailto:peter.kaplan@navis.com">Peter Kaplan</a>, 12/12/12
 *
 * Modified by <a href="mailto:sramsamy@weservetech.com">Ramasamy S</a>, 13/May/2022
 * Adding Loggers
 *
 * Modified by @Author <ahref="mailto:mharikumar@weservetech.com"  >  Harikumar M</a>, 30/Jan/2023
 * This groovy is used to bump the work instruction in case of its not completed properly and moving it to heap area based on the Data value1 in general reference,
 * also updating the truck position in the ufvFlexString04 & WI last position in unitFlexString02.
 *
 */

class Default25InboundCasMessageHandler extends AbstractGroovyWSCodeExtension {

    private static final Logger LOGGER = Logger.getLogger(Default25InboundCasMessageHandler.class);

    protected final String REQUEST_TYPE_PARAM = "requestType"
    protected final String VISIT_ID_PARAM = "visitId"
    protected final String VISIT_TYPE_PARAM = "visitType"
    protected final String UNITS_XML_PARAM = "unitXml"
    protected final String VISIT_TYPE_VESSEL = "VESSEL"
    protected final String VISIT_TYPE_TRAIN = "TRAIN"
    protected final String UNIT_CAPTURE_MESSAGE = "unitCaptureMessage";
    protected final String UNIT_CAPTURE_IMAGE_TYPE = "Image";
    protected final String UNIT_CAPTURE_IDENTIFY_TYPE = "Identify";
    protected final String UNIT_CAPTURE_CREATE_TYPE = "Create";
    protected final String UNIT_CAPTURE_UPDATE_TYPE = "Update";
    protected final String UNIT_ID = "unitId"
    public final String CRANE_ID_PARAM = "craneId"
    protected final String ACTION_PARAM = "action"
    //2.5 specific fields
    protected final String IS_TBD_LOAD = "isTbdLoad"
    protected final String IS_LOAD = "isLoad"
    protected final String IS_DISC = "isDisc"
    protected final String POSITION_PARAM = "position"
    protected final String TIER = "tier"


    // parameter fields
    protected String _requestType
    protected String _visitId
    protected String _visitType
    protected String _craneId
    protected String _unitsXml
    protected CasInUnit25[] _casInUnits
    protected int _unitCount
    protected String _action
    protected enum Status {
        OK, WARNING, ERROR
    }
    protected String _tier

    //The name of the cas helper groovy
    public final String CAS_HELPER = "CasHelper"
    def _casHelper = null
    //CasMessageHelper library name
    public final String CAS_MESSAGE_HELPER = "CasMessageHelper"
    //CasHelper library class
    def _casMessageHelper = null;
    //error code and message which are populated for non-unit specific errors, unit specific arrors are handled at unit level
    private String _errorCode = null;
    private String _errorMessage = null;

    //Datasource for equipment updates for this code extension
    private final DataSourceEnum _dataSourceEnum = DataSourceEnum.USER_LCL

    /**
     * Main entry point method.
     * @param inParameters parameters sent as part of groovy web service
     * @return the string response to the groovy webservice call
     */
    public String execute(Map inParameters) {
        //LOGGER.setLevel(Level.DEBUG);

        //Log the request content
        logMsg("\nRequest: " + getParametersAsString())
        Map<String, String> additionalInfoMap = new HashMap<String, String>();
        //Get the CasMessageHelper library
        initCasMessageHelper();
        //Validate that the unit xml is present and valid
        _unitsXml = _parameterMap.get(UNITS_XML_PARAM)
        if (StringUtils.isBlank(_unitsXml)){
            _errorCode = _casMessageHelper.MISSING_UNIT_XML_CODE
            _errorMessage = _casMessageHelper.MISSING_UNIT_XML_MESSAGE
            registerAndLogError()
            return getXmlErrorContent();
        }
        Node unitsNode = null;
        // Parse the XML message
        try {
            unitsNode = new XmlParser().parseText(_unitsXml);
        } catch (Exception ex) {
            _errorCode = _casMessageHelper.INVALID_UNIT_XML_CODE
            _errorMessage = _casMessageHelper.INVALID_UNIT_XML_MESSAGE
            registerAndLogError()
            ex.printStackTrace();
            return getXmlErrorContent();
        }
        def units = unitsNode.'unit'
        _unitCount = units.size()
        if (!"units".equals(unitsNode.name()) || _unitCount == 0){
            _errorCode = _casMessageHelper.INVALID_UNIT_XML_CODE
            _errorMessage = _casMessageHelper.INVALID_UNIT_XML_MESSAGE
            registerAndLogError()
            return getXmlErrorContent();
        }
        // Get CasHelper library
        _casHelper = getLibrary(CAS_HELPER)

        _action = _parameterMap.get(ACTION_PARAM)
        loadParameters(unitsNode);
        //Validate the required parameters for the message
        if (!validateParameters()) {
            return getXmlErrorContent();
        }

        //----------- Weserve Validation Start --------------------------------------------


        for (int i = 0; i < _casInUnits.length; i++) {
            CasInUnit25 casInUnit = _casInUnits[i]
            String unitPos = casInUnit.getAttribute(POSITION_PARAM)
            String unitId = casInUnit._unitId
            if (unitId != null)
            String trkPos = casInUnit._trkPos
            /**
             *  Modified by WeServe Tech - To Bump the Work Instruction in case its not completed properly with the XML RDT messages.
             */
            // execute only if carrier position is given, for load it will be null
            if (unitPos != null) {
                Unit unit = null
                Equipment equipment = Equipment.findEquipment(unitId)
                if (equipment != null) {
                    UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID)
                    unit = unitFinder.findActiveUnit(ContextHelper.getThreadComplex(), equipment)
                }

                GeneralReference generalReference = GeneralReference.findUniqueEntryById("ITS", "BUMP_HEAP_LOC", "BUMP_TIME_IN_SEC", "BUMP_VALIDATE_TRK_SLOT");
                String isValidateTrkPos = null;
                String waitTime = null
                if (generalReference != null) {
                    isValidateTrkPos = generalReference.getRefValue3();
                    waitTime = generalReference.getRefValue2();
                }
                List<WorkInstruction> wiList = findWorkInstruction(unitPos, unit, generalReference);
                if (wiList != null) {
                    if (wiList.size() >= 1) {
                        for (WorkInstruction workInstruction : wiList) {
                            UnitFacilityVisit wiUfv = workInstruction.getWiUfv()
                            if (unitId != null && wiUfv != null && wiUfv.getUfvUnit() != null
                                    && !unitId.equals(wiUfv.getUfvUnit().getUnitId()) && wiUfv.isTransitStatePriorTo(UfvTransitStateEnum.S40_YARD)) {
                                if (isValidateTrkPos.equals("Y")) {
                                    if (isTwentyLengthCtr(unit) && isTwentyLengthCtr(wiUfv.getUfvUnit())) {
                                        Calendar calendar = Calendar.getInstance()
                                        double difference = 0.0;
                                        difference = (workInstruction.getMvhsTimeFetch() != null && workInstruction.getMvhsTimeFetch().getTime() > 0) ?
                                                differenceInSeconds(workInstruction.getMvhsTimeFetch(), calendar.getTime()) : 0.0;
                                        if ((trkPos != null && trkPos.trim() == wiUfv.getUfvFlexString04())
                                                || (difference > waitTime.toDouble())) {
                                            LOGGER.warn(" inside trkPos not equals update to position");
                                            updateToPosition(workInstruction, generalReference);

                                        } else if ((trkPos == null || StringUtils.isBlank(trkPos) || StringUtils.isBlank(wiUfv.getUfvFlexString04()))
                                                && wiList.size() >= 2) {
                                            LOGGER.warn("\n Trk Pos Empty");
                                            updateToPosition(workInstruction, generalReference);
                                        }

                                    } else {
                                        LOGGER.debug("\n inside 40 ft --  equals update to position");
                                        updateToPosition(workInstruction, generalReference);
                                    }
                                }
                                HibernateApi.getInstance().save(workInstruction)
                            }
                        }

                    }

                    if (unit != null && trkPos != null && unit.getUnitActiveUfv() != null) {
                        LOGGER.debug("\nupdate last known position	  ");
                        unit.getUnitActiveUfv().getUfvLastKnownPosition().setPosSlotOnCarriage(trkPos);
                        unit.getUnitActiveUfv().setFieldValue(InvField.UFV_FLEX_STRING04, trkPos)
                        HibernateApi.getInstance().save(unit)
                    }
                    HibernateApi.getInstance().flush();
                }
            }
        }

        //----------- WeServe Validation Completed --------------------------------------------

        // Identify the message type and call appropriate handler
        if (UNIT_CAPTURE_MESSAGE.equals(_requestType)) {
            // Validate Capture type
            boolean isValidAction = UNIT_CAPTURE_IMAGE_TYPE.equals(_action) || UNIT_CAPTURE_IDENTIFY_TYPE.equals(_action) ||
                    UNIT_CAPTURE_CREATE_TYPE.equals(_action) || UNIT_CAPTURE_UPDATE_TYPE.equals(_action);
            if (!isValidAction) {
                _errorCode = _casMessageHelper.INVALID_CAPTURE_TYPE_CODE
                _errorMessage = _casMessageHelper.INVALID_CAPTURE_TYPE_MESSAGE
                registerAndLogError()
                return getXmlErrorContent();
            }
            processUnitCapture();
        } else {
            _errorCode = _casMessageHelper.INVALID_25_MESSAGE_REQUEST_TYPE_CODE
            _errorMessage = _casMessageHelper.INVALID_25_MESSAGE_REQUEST_TYPE_MESSAGE
            registerAndLogError()
            return getXmlErrorContent();
        }
        // Create the response message
        final String responseXml = createResponseXml(additionalInfoMap)
        logMsg("\nRequest: " + getParametersAsString() + "\nResponse : " + responseXml)
        return responseXml;
    }

    /**
     * Loads the parameters to fields, so that they are available to all methods
     */
    protected void loadParameters(Node inUnitsNode) {
        _casInUnits = new CasInUnit25[_unitCount]
        _requestType = _parameterMap.get(REQUEST_TYPE_PARAM);
        _visitId = _parameterMap.get(VISIT_ID_PARAM)
        _visitType = _parameterMap.get(VISIT_TYPE_PARAM)
        _craneId = _parameterMap.get(CRANE_ID_PARAM)
        _tier = _parameterMap.get(TIER)
        try {
            if (StringUtils.isBlank(_tier) || Integer.parseInt(_tier) < 0) {
                _tier = 1
            }
        } catch (NumberFormatException e) {
            _tier = 1
        }
        def units = inUnitsNode.'unit'
        int i = 0
        units.each { Node unitNode ->
            _casInUnits[i] = new CasInUnit25(unitNode)
            initializeCasUnit(_casInUnits[i]);
            i++;
        }
    }

    /**
     * A hook for subclasses to add additional parameters/attributes to the attribute map of CasInUnit25. This method is called immediately after the
     * CasInUnit25 is constructed
     * @param inCasInUnit newly created CasInUnit25
     */
    protected void initializeCasUnit(CasInUnit25 inCasInUnit) {
        Node unitNode = inCasInUnit.getUnitNode()
        if (unitNode != null) {
            String positionStr = unitNode."@position"
            inCasInUnit.setAttribute(POSITION_PARAM, positionStr)
        }
    }

    /**
     * Validate the required parameters for the message, for the unit's required parameters message is added to CasInUnit25
     * @return true if all the required parameters for message as a whole are valid, for unit attributes it sets hasError of
     * unit to true and adds a return message for that unit, so that the other valid units are processed.
     */
    protected boolean validateParameters() {
        boolean isValid = true;

        if (StringUtils.isBlank(_requestType)) {
            _errorCode = _casMessageHelper.MISSING_REQUEST_TYPE_CODE
            _errorMessage = _casMessageHelper.MISSING_REQUEST_TYPE_MESSAGE
            registerAndLogError()
            return false;
        }
        if (StringUtils.isBlank(_visitId)) {
            _errorCode = _casMessageHelper.MISSING_VISIT_ID_CODE
            _errorMessage = _casMessageHelper.MISSING_VISIT_ID_MESSAGE
            registerAndLogError()
            return false;
        }
        if (StringUtils.isBlank(_visitType)) {
            _errorCode = _casMessageHelper.MISSING_VISIT_TYPE_CODE
            _errorMessage = _casMessageHelper.MISSING_VISIT_TYPE_MESSAGE
            registerAndLogError()
            return false;
        } else if (!(_visitType.equals(VISIT_TYPE_VESSEL) || _visitType.equals(VISIT_TYPE_TRAIN))) {
            _errorCode = _casMessageHelper.INVALID_VISIT_TYPE_CODE
            _errorMessage = _casMessageHelper.INVALID_VISIT_TYPE_MESSAGE
            registerAndLogError()
            return false;
        }
        //Validate the carrier visit
        CarrierVisit carrierVisit = null
        if (_visitType.equals(VISIT_TYPE_VESSEL)){
            carrierVisit = CarrierVisit.findVesselVisit(ContextHelper.getThreadFacility(), _visitId)
        }else if (_visitType.equals(VISIT_TYPE_TRAIN)){
            carrierVisit = CarrierVisit.findTrainVisit(ContextHelper.getThreadComplex(), ContextHelper.getThreadFacility(), _visitId)
        }
        if (carrierVisit == null ){
            _errorCode = _casMessageHelper.NO_CARRIER_VISIT_FOUND_CODE
            _errorMessage = _casMessageHelper.NO_CARRIER_VISIT_FOUND_MESSAGE + "[visitId=" +  _visitId + "]"
            registerAndLogError()
            return false;
        }
        if (StringUtils.isBlank(_craneId)) {
            _errorCode = _casMessageHelper.MISSING_CRANE_ID_CODE
            _errorMessage = _casMessageHelper.MISSING_CRANE_ID_MESSAGE
            registerAndLogError()
            return false;
        } else {
            try {
                _casHelper.validateCraneId(_craneId);
            } catch (Exception ex) {
                _errorCode = _casMessageHelper.INVALID_CRANE_ID_CODE
                _errorMessage = _casMessageHelper.INVALID_CRANE_ID_MESSAGE + "[craneId=" +  _craneId + "]"
                registerAndLogError()
                return false;
            }
        }

        for (CasInUnit25 casInUnit : _casInUnits) {
            if (StringUtils.isBlank(casInUnit.getCasUnitReference())) {
                logMsg(_casMessageHelper.MISSING_CAS_UNIT_REFERENCE_CODE + ":" + _casMessageHelper.MISSING_CAS_UNIT_REFERENCE_MESSAGE);
                casInUnit.setReturnStatus(Status.ERROR);
                casInUnit.setReturnCode(_casMessageHelper.MISSING_CAS_UNIT_REFERENCE_CODE)
                casInUnit.setReturnMessage(_casMessageHelper.MISSING_CAS_UNIT_REFERENCE_MESSAGE)
            }
        }
        return isValid;
    }

    /**
     * Processes the unit capture message after preliminary validations have been done
     */
    protected void processUnitCapture() {
        boolean tbdLoad = false
        boolean load = false
        boolean discharge = false
        boolean hasError = false;
        int count = 0
        // Validate each unit and determine the type of operation to perform
        for (CasInUnit25 casInUnit : _casInUnits) {
            if (!casInUnit.hasError()) {
                validateUnitForCapture(casInUnit);
            }
            if (!casInUnit.hasError()) {
                handleUnitCaptureAndUpdate(casInUnit)
            }
            if (casInUnit.getAttribute(IS_TBD_LOAD)) {
                tbdLoad = true
            } else if (casInUnit.getAttribute(IS_LOAD)) {
                load = true
            } else if (casInUnit.getAttribute(IS_DISC)) {
                discharge = true
                count++
            }
            if (casInUnit.hasError()) {
                hasError = true;
            }
        }
        if ( _casInUnits.length > 1 && _casInUnits.length != count) {
            discharge = false
        }
        if (hasError) {
            return;
        }
        try {
            if (tbdLoad) {
                loadUnitsForTbd()
            } else if (load) {
                loadUnits()
            } else if (discharge) {
                dischargeUnits()
            }
        } catch (Exception ex) {
            logMsg("Caught exception: " + CarinaUtils.getStackTrace(ex))
            ex.printStackTrace();
            logMsg(ex.getLocalizedMessage());
            // If exception, set error status for all units
            for (CasInUnit25 casInUnit : _casInUnits) {
                casInUnit.setReturnStatus(Status.ERROR)
                casInUnit.setReturnCode(_casMessageHelper.BIZ_ERROR_CODE)
                casInUnit.setReturnMessage(ex.getLocalizedMessage())
            }
        }
    }

    /**
     * Validate a unit
     * @param inCasInUnit
     */
    protected void validateUnitForCapture(CasInUnit25 inCasInUnit) {
        if (!UNIT_CAPTURE_IMAGE_TYPE.equals(_action) && StringUtils.isBlank(inCasInUnit.getUnitId())) {
            logMsg(_casMessageHelper.MISSING_UNIT_ID_CODE + ":" + _casMessageHelper.MISSING_UNIT_ID_MESSAGE);
            inCasInUnit.setReturnStatus(Status.ERROR);
            inCasInUnit.setReturnCode(_casMessageHelper.MISSING_UNIT_ID_CODE)
            inCasInUnit.setReturnMessage(_casMessageHelper.MISSING_UNIT_ID_MESSAGE)
            return
        }
        //Validate unit
        try {
            if (UNIT_CAPTURE_IMAGE_TYPE.equals(_action) || UNIT_CAPTURE_CREATE_TYPE.equals(_action)) {
                return;//nothing to validate
            }
            validateUnitActive(inCasInUnit)
            UnitFacilityVisit unitFacilityVisit = inCasInUnit.getUnitFacilityVisit()
            //Validate that the in case of "Identify" action, unit is valid for load/discharge
            if (UNIT_CAPTURE_IDENTIFY_TYPE.equals(_action)) {
                List<WorkInstruction> currentWIList = unitFacilityVisit.getCurrentWiList();
                if (currentWIList == null || currentWIList.isEmpty()) {
                    try {
                        LocPosition currPos = unitFacilityVisit.getUfvUnit().findCurrentPosition();
                        if (currPos.isVesselPosition()) {
                            inCasInUnit.setAttribute(IS_DISC, true)
                        } else {
                            validateTbdLoadStatus(inCasInUnit)
                            inCasInUnit.setAttribute(IS_TBD_LOAD, true)
                        }
                    } catch (BizViolation tbdBv) {
                        logMsg(_casMessageHelper.BIZ_ERROR_CODE + ":" + tbdBv.getLocalizedMessage());
                        inCasInUnit.setReturnStatus(Status.ERROR);
                        inCasInUnit.setReturnCode(_casMessageHelper.BIZ_ERROR_CODE)
                        inCasInUnit.setReturnMessage(tbdBv.getLocalizedMessage())
                        return;
                    }

                    if (!inCasInUnit.getAttribute(IS_TBD_LOAD)) {
                        inCasInUnit.setReturnStatus(Status.WARNING)

                        if (inCasInUnit.getAttribute(IS_DISC)) {
                            logMsg(_casMessageHelper.NOT_IN_DSCH_PLAN_CODE + ":" + _casMessageHelper.NOT_IN_DSCH_PLAN_MESSAGE);
                            inCasInUnit.setReturnCode(_casMessageHelper.NOT_IN_DSCH_PLAN_CODE)
                            inCasInUnit.setReturnMessage(_casMessageHelper.NOT_IN_DSCH_PLAN_MESSAGE)
                            inCasInUnit.setReturnStatus(Status.ERROR);
                            inCasInUnit.removeAttribute(IS_DISC);
                        } else {
                            logMsg(_casMessageHelper.NOT_VALID_FOR_TBD_LOAD_CODE + ":" + _casMessageHelper.NOT_VALID_FOR_TBD_LOAD_MESSAGE);
                            inCasInUnit.setReturnCode(_casMessageHelper.NOT_VALID_FOR_TBD_LOAD_CODE)
                            inCasInUnit.setReturnMessage(_casMessageHelper.NOT_VALID_FOR_TBD_LOAD_MESSAGE)
                        }
                    }
                } else { // if WI is not empty
                    WorkInstruction targetWI = null;
                    CarrierVisit obCv = unitFacilityVisit.getUfvActualObCv();
                    CarrierVisit ibCv = unitFacilityVisit.getUfvActualIbCv();
                    if (RestowTypeEnum.RESTOW.equals(unitFacilityVisit.getUfvRestowType()) ||
                            UnitCategoryEnum.TRANSSHIP.equals(unitFacilityVisit.getUfvUnit().getUnitCategory()) ||
                            (obCv != null && ibCv != null && LocTypeEnum.VESSEL.equals(obCv.getCvCarrierMode()) &&
                                    LocTypeEnum.VESSEL.equals(ibCv.getCvCarrierMode()))) {
                        for (WorkInstruction currentWi: currentWIList) {
                            if (currentWi.getWiMoveKind().equals(WiMoveKindEnum.VeslDisch)) {
                                targetWI = currentWi;
                                break;
                            }
                        }
                    }
                    if  (targetWI == null) {
                        targetWI = currentWIList.get(0);
                    }
                    LocPosition currentPos = unitFacilityVisit.getUfvUnit().findCurrentPosition();
                    if (targetWI != null) {
                        //2013.03.14 azharad ARGO-45543 Allow Shift On Board move from this Groovy
                        if (currentPos.isVesselPosition() && targetWI.getWiToPosition().isVesselPosition()
                                && currentPos.getPosLocId().equals(targetWI.getWiToPosition().getPosLocId())) {
                            inCasInUnit.setAttribute(IS_LOAD, true)
                        } else if (targetWI.getWiToPosition().isVesselPosition() || targetWI.getWiToPosition().isRailPosition()) {
                            logMsg("Unit is planned for load to [" + targetWI.getWiToPosition() + "]")
                            if (!obCv.getCvId().equals(_visitId)) {
                                final String errorMsg = _casMessageHelper.CARRIER_MISMATCH_LOAD_MESSAGE + "[ActualVisitId="+ _visitId + ",PlannedVisitId=" + obCv.getCarrierVehicleId() + "]"
                                logMsg(_casMessageHelper.CARRIER_MISMATCH_LOAD_CODE + ":" + errorMsg)
                                inCasInUnit.setReturnStatus(Status.ERROR);
                                inCasInUnit.setReturnCode(_casMessageHelper.CARRIER_MISMATCH_LOAD_CODE)
                                inCasInUnit.setReturnMessage(errorMsg)
                            }
                            inCasInUnit.setAttribute(IS_LOAD, true)
                        } else if (currentPos.isCarrierPosition() && targetWI.getWiToPosition().isYardPosition()) {
                            logMsg("Unit is planned for discharge to [" + targetWI.getWiToPosition() + "]")
                            ibCv = InventoryControlUtils.getEffectiveInboundCarrierVisitForDischarge(unitFacilityVisit);
                            if (ibCv == null || !ibCv.getCvId().equals(_visitId)) {
                                final String errorMsg =_casMessageHelper.CARRIER_MISMATCH_DSCH_MESSAGE + "[ActualVisitId="+ _visitId + ",PlannedVisitId=" + ibCv.getCarrierVehicleId() + "]"
                                logMsg(_casMessageHelper.CARRIER_MISMATCH_DSCH_CODE + ":" + errorMsg)
                                inCasInUnit.setReturnStatus(Status.ERROR);
                                inCasInUnit.setReturnCode(_casMessageHelper.CARRIER_MISMATCH_DSCH_CODE)
                                inCasInUnit.setReturnMessage(errorMsg)
                            }
                            inCasInUnit.setAttribute(IS_DISC, true)
                        } else {
                            logMsg(_casMessageHelper.NOT_IN_LOAD_OR_DSCH_PLAN_ERR_CODE + ":" + _casMessageHelper.NOT_IN_LOAD_OR_DSCH_PLAN_ERR_MESSAGE);
                            inCasInUnit.setReturnStatus(Status.ERROR);
                            inCasInUnit.setReturnCode(_casMessageHelper.NOT_IN_LOAD_OR_DSCH_PLAN_ERR_CODE)
                            inCasInUnit.setReturnMessage(_casMessageHelper.NOT_IN_LOAD_OR_DSCH_PLAN_ERR_MESSAGE)
                        }
                    }
                }
            } else if (UNIT_CAPTURE_UPDATE_TYPE.equals(_action)) {
                //Verify that unit reference in ufv and unit reference of the incoming message match
                verifyUnitReferenceAndTransactionReference(inCasInUnit, unitFacilityVisit)
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            logMsg(ex.getLocalizedMessage());
            inCasInUnit.setReturnStatus(Status.ERROR);
            inCasInUnit.setReturnMessage(ex.getLocalizedMessage());
        }
    }

    /**
     * Validate if unit is active and populate the unit id and unitFacilityVisit fields of inCasInUnit.
     *
     * @param inCasInUnit CasInUnit
     * @throws BizViolation trows BizViolation if the validation fails
     */
    protected void validateUnitActive(CasInUnit25 inCasInUnit) throws BizViolation {
        BizViolation bv = null;
        UnitManager unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID);
        UnitFacilityVisit unitFacilityVisit = unitManager.findActiveUfvForUnitDigits(inCasInUnit.getUnitId())
        inCasInUnit.setUnitFacilityVisit(unitFacilityVisit)
        Unit unit = unitFacilityVisit.getUfvUnit()
        inCasInUnit.setUnit(unit)
        ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);
        IEventType eventType = servicesManager.getEventType(EventEnum.UNIT_DERAMP.getKey());

        if (!unit.isActive()) {
            bv = BizViolation.create(InventoryPropertyKeys.UNITS__NOT_ACTIVE, bv, inCasInUnit.getUnitId(), eventType.getId());
        }

        if (!unitFacilityVisit.equals(unit.getUnitActiveUfvNowActive())) {
            bv = BizViolation.create(InventoryPropertyKeys.UNITS_NOT_ACTIVE_IN_FACILITY, bv,
                    inCasInUnit.getUnitId(), unitFacilityVisit.getUfvFacility().getFcyId());
        }
        if (bv != null) {
            throw bv
        }
    }

    /**
     * Validate that the unit can act as a TBD Load
     * @param inCasInUnit
     * @throws BizViolation
     */
    protected void validateTbdLoadStatus(CasInUnit25 inCasInUnit) throws BizViolation {
        if (inCasInUnit.getUnitFacilityVisit().getFinalPlannedPosition().equals(null)) {
            HcTbdUnitManager hcTbdMngr = (HcTbdUnitManager) Roastery.getBean(HcTbdUnitManager.BEAN_ID);
            CarrierVisit cv = CarrierVisit.findVesselVisit(ContextHelper.getThreadFacility(), _visitId);
            if (cv == null) {
                throw BizViolation.create(InventoryPropertyKeys.ERROR_UNIT_IDENTIFICATION_VESSEL_VISIT_DOES_NOT_EXIST, null, _visitId);
            }
            hcTbdMngr.validateTbdUnitForLoad(inCasInUnit.getUnitFacilityVisit(), cv);
        }
    }

    protected void verifyUnitReferenceAndTransactionReference(CasInUnit25 inCasInUnit, UnitFacilityVisit inUnitFacilityVisit) {
        if (!inCasInUnit.getCasUnitReference().equals(inUnitFacilityVisit.getUfvCasUnitReference())) {
            final String unitErrorCode = _casMessageHelper.UNIT_REFERENCE_NOT_SAME_CODE
            final String unitErrorMsg = _casMessageHelper.UNIT_REFERENCE_NOT_SAME_MESSAGE + "[Incoming=" + inCasInUnit.getCasUnitReference() +
                    ",Existing=" + inUnitFacilityVisit.getUfvCasUnitReference() + "]"
            logMsg(unitErrorCode + ":" + unitErrorMsg);
            inCasInUnit.setReturnStatus(Status.ERROR);
            inCasInUnit.setReturnCode(unitErrorCode)
            inCasInUnit.setReturnMessage(unitErrorMsg)
        }
        if (inCasInUnit.getCasTransactionReference() != null &&
                inUnitFacilityVisit.getUfvCasTransactionReference() != null &&
                !inCasInUnit.getCasTransactionReference().equals(inUnitFacilityVisit.getUfvCasTransactionReference())) {
            final String unitErrorCode = _casMessageHelper.TRANSACTION_REFERENCE_NOT_SAME_CODE
            final String unitErrorMsg = _casMessageHelper.TRANSACTION_REFERENCE_NOT_SAME_MESSAGE + "[Incoming=" + inCasInUnit.getCasTransactionReference() +
                    ",Existing=" + inUnitFacilityVisit.getUfvCasTransactionReference() + "]"
            logMsg(unitErrorCode + ":" + unitErrorMsg);
            inCasInUnit.setReturnStatus(Status.ERROR);
            inCasInUnit.setReturnCode(unitErrorCode)
            inCasInUnit.setReturnMessage(unitErrorMsg)
        }
    }

    /**
     * Handles the unit capture message and updates the unit
     * @param inCasInUnit
     */
    protected void handleUnitCaptureAndUpdate(CasInUnit25 inCasInUnit) {
        try {
            if (UNIT_CAPTURE_CREATE_TYPE.equals(_action)) {
                createUnit(inCasInUnit)
            }
            if (UNIT_CAPTURE_IMAGE_TYPE.equals(_action)) {
                handleImageCaptureMessage(inCasInUnit)
            } else {
                // Record cas unit capture event
                String nodeText = "Action: " + _action + "," + inCasInUnit.getUnitNode().toString();
                if (nodeText.length() > 255) {
                    nodeText = nodeText.substring(0, 254)
                }
                recordEvent(inCasInUnit.getUnit(), EventEnum.CAS_UNIT_CAPTURE, null, nodeText)
                updateUnitAttributes(inCasInUnit);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            logMsg(ex.getLocalizedMessage());
            inCasInUnit.setReturnStatus(Status.ERROR);
            inCasInUnit.setReturnMessage(ex.getLocalizedMessage())
        }
    }

    /**
     * Creates a unit corresponding to
     * @param inCasInUnit : CAS unit
     */
    protected void createUnit(CasInUnit25 inCasInUnit) throws BizViolation {
        UnitManager unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID);
        CarrierVisit carrierVisit = CarrierVisit.findVesselVisit(ContextHelper.getThreadFacility(), _visitId);

        Container container = null;
        String isoCode = inCasInUnit.getUnitNode().attributes().get("iso-code")
        if (StringUtils.isBlank(isoCode)) {
            logMsg(_casMessageHelper.MISSING_ISO_CODE_FOR_CREATE_WARNING_CODE + ":" + _casMessageHelper.MISSING_ISO_CODE_FOR_CREATE_WARNING_MESSAGE);
            inCasInUnit.setReturnStatus(Status.WARNING);
            inCasInUnit.setReturnCode(_casMessageHelper.MISSING_ISO_CODE_FOR_CREATE_WARNING_CODE)
            inCasInUnit.setReturnMessage(_casMessageHelper.MISSING_ISO_CODE_FOR_CREATE_WARNING_MESSAGE)
            container = Container.findOrCreateContainer(inCasInUnit.getUnitId(), _dataSourceEnum)
        } else {
            container = Container.findOrCreateContainer(inCasInUnit.getUnitId(), isoCode, _dataSourceEnum)
        }
        UnitFacilityVisit ufv = unitManager.findOrCreateStowplanUnit(container, carrierVisit, carrierVisit.getCarrierOperator(),
                ContextHelper.getThreadFacility())
        RectifyParms parms = new RectifyParms()
        parms.setUfvTransitState(UfvTransitStateEnum.S20_INBOUND)
        parms.setUnitVisitState(UnitVisitStateEnum.ACTIVE)
        ufv.rectify(parms);
        final Unit unit = ufv.getUfvUnit()
        unit.updateCategory(UnitCategoryEnum.IMPORT)
        //Set the newly created unit and ufv on CasInUnit
        inCasInUnit.setUnit(unit)
        inCasInUnit.setUnitFacilityVisit(ufv)
    }

    /**
     * Handle image capture message
     * @param inCasInUnit : CAS unit
     */
    protected void handleImageCaptureMessage(CasInUnit25 inCasInUnit) {
        inCasInUnit.setReturnStatus(Status.OK)
        inCasInUnit.setReturnCode(_casMessageHelper.UNIT_CAPTURE_HANDLED_SUCCESSFULLY_CODE)
        inCasInUnit.setReturnMessage(_casMessageHelper.UNIT_CAPTURE_HANDLED_SUCCESSFULLY_MESSAGE + " [Action=" + _action + "]")
    }

    /**
     * Update the unit with attributes from unit xml
     * @param inCasInUnit
     * @throws BizViolation
     */
    protected void updateUnitAttributes(CasInUnit25 inCasInUnit) throws BizViolation {
        Node unitNode = inCasInUnit.getUnitNode()
        Unit unit = inCasInUnit.getUnit()
        UnitFacilityVisit ufv = inCasInUnit.getUnitFacilityVisit()
        Equipment primaryEquipment = unit.getPrimaryEq();
        //Validate bundled Equipment
        Node bundledEquipmentNode = unitNode."bundled-equipment"[0]
        if (bundledEquipmentNode != null){
            NodeList equipmentNodeList = bundledEquipmentNode."equipment"
            equipmentNodeList.each{Node equipmentNode ->
                Equipment equipment = Equipment.findEquipment(equipmentNode.@'id')
                if (equipment == null){
                    final String errorCode = _casMessageHelper.BUNDLED_EQUIPMENT_NOT_FOUND_CODE
                    final String errorMsg = _casMessageHelper.BUNDLED_EQUIPMENT_NOT_FOUND_MESSAGE + "[id=" + equipmentNode.@'id' + "]"
                    logMsg(errorCode + ":" + errorMsg);
                    inCasInUnit.setReturnStatus(Status.ERROR);
                    inCasInUnit.setReturnCode(errorCode)
                    inCasInUnit.setReturnMessage(errorMsg)
                    return
                }
            }
        }
        // Gross Weight
        if (unitNode.attributes().containsKey("gross-weight")) {
            unit.updateGoodsAndCtrWtKg((Double) getAttributeValue("gross-weight", unitNode.@"gross-weight", Double.class));
        }
        // Yard Measured weight
        if (unitNode.attributes().containsKey("yard-measured-weight")) {
            unit.updateGoodsAndCtrWtKgAdvised((Double) getAttributeValue("yard-measured-weight", unitNode.@"yard-measured-weight", Double.class));
        }
        // ISO code
        if (!UNIT_CAPTURE_CREATE_TYPE.equals(_action)) { //iso-code is handled as part of create
            if (unitNode.attributes().containsKey("iso-code")) {
                primaryEquipment.upgradeEqType(unitNode.@"iso-code", _dataSourceEnum)
            }
        }
        // Height mm
        if (unitNode.attributes().containsKey("height-mm")) {
            primaryEquipment.upgradeEqHeight((Long) getAttributeValue("height-mm", unitNode.@"height-mm", Long.class), _dataSourceEnum)
        }
        // Length mm
        if (unitNode.attributes().containsKey("length-mm")) {
            primaryEquipment.setFieldValue(ArgoRefField.EQ_LENGTH_MM, (Long) getAttributeValue("length-mm", unitNode.@"length-mm", Long.class))
        }
        // Width mm
        if (unitNode.attributes().containsKey("width-mm")) {
            primaryEquipment.setFieldValue(ArgoRefField.EQ_WIDTH_MM, (Long) getAttributeValue("width-mm", unitNode.@"width-mm", Long.class))
        }
        // Tank rail type
        if (unitNode.attributes().containsKey("tank-rail-type")) {
            String tankRails = unitNode.@"tank-rail-type"
            TankRailTypeEnum tankRailTypeEnum = TankRailTypeEnum.getEnum(tankRails)
            if (tankRailTypeEnum == null) {
                throw invalidValueViolation("tank-rail-type", tankRails)
            }
            primaryEquipment.setFieldValue(ArgoRefField.EQ_TANK_RAILS, tankRailTypeEnum)
        }

        //To update Door direction on the UFV...
        //Door direction
        if (unitNode.attributes().containsKey("door-direction")) {
            String doorDirection = unitNode.@"door-direction"
            DoorDirectionEnum doorDirectionEnum = DoorDirectionEnum.getEnum(doorDirection)
            if (doorDirectionEnum == null) {
                throw invalidValueViolation("door-direction", doorDirection)
            }
            ufv.updateCurrentDoorDir(doorDirectionEnum)
        }

        // OOG
        Node oogNode = unitNode.'oog'[0]
        if (oogNode != null) {
            unit.updateOog(extractOog("back-cm", oogNode.@"back-cm"), extractOog("front-cm", oogNode.@"front-cm"), extractOog("left-cm", oogNode.@"left-cm"),
                    extractOog("right-cm", oogNode.@"right-cm"), extractOog("top-cm", oogNode.@"top-cm"))
        }

        if (unitNode.attributes().containsKey("is-sealed")) {
            unit.setFieldValue(InventoryField.UNIT_IS_CTR_SEALED, (Boolean) getAttributeValue("is-sealed", unitNode.@"is-sealed", Boolean.class))
        }
        //Is Bundled
        if (unitNode.attributes().containsKey("is-bundle")) {
            unit.setFieldValue(InventoryField.UNIT_IS_BUNDLE, (Boolean) getAttributeValue("is-bundle", unitNode.@"is-bundle", Boolean.class))
        }
        //Is Placarded
        if (unitNode.attributes().containsKey("is-placarded")) {
            boolean isPlacarded = (Boolean) getAttributeValue("is-placarded", unitNode.@"is-placarded", Boolean.class)
            unit.setFieldValue(UnitField.UNIT_PLACARDED, isPlacarded ? PlacardedEnum.YES : PlacardedEnum.NO)
        }
        // Seals
        Node sealsNode = unitNode.'seals'[0]
        if (sealsNode != null) {
            unit.updateSeals(sealsNode.@"seal-1", sealsNode.@"seal-2", sealsNode.@"seal-3", sealsNode.@"seal-4")
        }
        // Damages
        Node damagesNode = unitNode.'damages'[0]
        if (damagesNode != null) {
            UnitEquipment unitEquipment = unit.getCurrentlyAttachedUe(primaryEquipment)
            if (unitEquipment != null) {
                // Clear all existing damages - they will be completely replaced by this update
                unitEquipment.attachDamages(null);
                EquipClassEnum equipClass = unitEquipment.getUeEquipment().getEqClass();
                NodeList damageNodes = damagesNode.'damage'
                damageNodes.each { Node damageNode ->
                    String component = damageNode.@'component'
                    String severity = damageNode.@'severity'
                    String type = damageNode.@'type'
                    Date reportedDate = (Date) getAttributeValue("reported-date", damageNode.@"reported-date", Date.class)
                    Date repairedDate = (Date) getAttributeValue("repaired-date", damageNode.@"repaired-date", Date.class)

                    EquipDamageType eqdmgtyp = EquipDamageType.findOrCreateEquipDamageType(type, type, equipClass)
                    EqComponent eqcmp = EqComponent.findOrCreateEqComponent(component, component, equipClass)
                    EqDamageSeverityEnum eqdmgSeverity = EqDamageSeverityEnum.getEnum(severity)

                    unitEquipment.addDamageItem(eqdmgtyp, eqcmp, eqdmgSeverity, reportedDate, repairedDate)
                }
            }
        }
        // Flags
        Node flagsNode = unitNode.'flags'[0]
        if (flagsNode != null) {
            ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);
            NodeList holdsNodeList = flagsNode.'hold'
            holdsNodeList.each { Node holdNode ->
                String holdId = holdNode.@'id'
                servicesManager.applyHold(holdId, unit, null, null, "cas");
            }
            NodeList permissionsNode = flagsNode.'permission'
            permissionsNode.each { Node permissionNode ->
                String permissionId = permissionNode.@'id'
                servicesManager.applyPermission(permissionId, unit, null, null, "cas");
            }
        }
        // Observed placards
        Node observedPlacardsNode = unitNode."observed-placards"[0]
        if (observedPlacardsNode != null) {
            NodeList obsPlacardNodeList = observedPlacardsNode."observed-placard"
            obsPlacardNodeList.each { Node obsPlacardNode ->
                String placardText = obsPlacardNode.@'placard'
                Placard placard = Placard.findPlacard(placardText)
                if(placard != null) {
                    ObservedPlacard observedPlacard = ObservedPlacard.createObservedPlacard(unit, placardText)
                    observedPlacard.setFieldValue(InventoryField.OBSPLACARD_REMARK, obsPlacardNode.@'remarks')
                }else{
                    logMsg('Placard '+placardText+' not found')
                }
            }
        }
        //Update Bundled Equipment
        if (bundledEquipmentNode != null) {
            NodeList equipmentNodeList = bundledEquipmentNode."equipment"
            equipmentNodeList.each { Node equipmentNode ->
                Equipment equipment = Equipment.findEquipment(equipmentNode.@'id')
                if (equipment != null) {
                    // Update the attributes
                    // ISO code
                    if (equipmentNode.attributes().containsKey("iso-code")) {
                        equipment.upgradeEqType(equipmentNode.@"iso-code", _dataSourceEnum)
                    }
                    // Height mm
                    if (equipmentNode.attributes().containsKey("height-mm")) {
                        equipment.upgradeEqHeight((Long) getAttributeValue("height-mm", equipmentNode.@"height-mm", Long.class), _dataSourceEnum)
                    }
                    // Length mm
                    if (equipmentNode.attributes().containsKey("length-mm")) {
                        equipment.setFieldValue(ArgoRefField.EQ_LENGTH_MM, (Long) getAttributeValue("length-mm", equipmentNode.@"length-mm", Long.class))
                    }
                    // Width mm
                    if (equipmentNode.attributes().containsKey("width-mm")) {
                        equipment.setFieldValue(ArgoRefField.EQ_WIDTH_MM, (Long) getAttributeValue("width-mm", equipmentNode.@"width-mm", Long.class))
                    }
                    // Attach it to the unit
                    unit.attachPayload(equipment)
                }

            }
        }
        //Set the unit reference and transaction reference
        if (UNIT_CAPTURE_IDENTIFY_TYPE.equals(_action) || UNIT_CAPTURE_CREATE_TYPE.equals(_action)) {
            ufv.setFieldValue(InventoryField.UFV_CAS_UNIT_REFERENCE, inCasInUnit.getCasUnitReference())
            if (!StringUtils.isBlank(inCasInUnit.getCasTransactionReference())) {
                ufv.setFieldValue(InventoryField.UFV_CAS_TRANSACTION_REFERENCE, inCasInUnit.getCasTransactionReference())
            }
        }
    }

    /**
     * Utility to extract OOG long value
     * @param inField
     * @param inValue
     * @return Long
     */
    private Long extractOog(String inField, String inValue) {
        return getAttributeValue(inField, inValue, Long.class) as Long
    }

    /**
     * Translates an attribute value to correct class.
     * @param inName name of the attribute
     * @param inName value of the attribute
     * @param inValueClass The java Class of the value within the Entity to which it belongs
     * @return translated value of the attribute
     * @throws BizViolation if attribute is present and can not be parsed
     */
    protected Object getAttributeValue(String inName, String inValue, Class inValueClass) throws BizViolation {
        IPropertyResolver resolver = ArgoUtils.getPropertyResolver(inValueClass);
        return resolver.resolve(inName, inValue);
    }
    /**
     * Returns exception for invalid value
     * @param inFieldId id of the field
     * @param inValue field value
     * @return biz violation
     */
    public BizViolation invalidValueViolation(String inFieldId, Object inValue) {
        return BizViolation.create(ArgoPropertyKeys.VALIDATION_INVALID_VALUE_FOR_FIELD, null, inFieldId, inValue);
    }

    /**
     * Load TBD units through XPS
     */
    protected void loadUnitsForTbd() {
        UnitManager unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID);
        List<UnitFacilityVisit> ufvList = new ArrayList<UnitFacilityVisit>(_unitCount);
        String[] unitIds = new String[_unitCount]
        String[] locIds = new String[_unitCount]
        Boolean requestPos = false;
        Map<String, Object> resultFromXPS = null;
        UnitFacilityVisit ufv = null;
        CarrierVisit cv = CarrierVisit.findVesselVisit(ContextHelper.getThreadFacility(), _visitId);
        if (cv != null) {
            VisitDetails visitDetails = cv.getCvCvd();
            // Determine if message contains position(s)
            for (int i = 0; i < _casInUnits.length; i++) {
                CasInUnit25 casInUnit = _casInUnits[i];
                ufv = unitManager.findActiveUfvForUnitDigits(casInUnit.getUnitId());
                ufvList.add(ufv);
                unitIds[i] = casInUnit.getUnitId();
                String unitPos = casInUnit.getAttribute(POSITION_PARAM)
                if (unitPos == null || unitPos.isEmpty()) {
                    requestPos = true;
                }
            }
            // Request positions if necessary
            LiftModeEnum liftMode = LiftModeEnum.UNKNOWN;
            if (requestPos) {
                liftMode = computeLiftMode(_casInUnits)
                resultFromXPS = unitManager.requestXPSForLoadPosition(unitIds, _craneId, _visitId, liftMode.getKey());
                int result = (Integer) resultFromXPS.get(BentoMessageAttribute.RESULT);
                // Return errors in position request
                if (result < 0) {
                    String errorMsg = (String) resultFromXPS.get(BentoMessageAttribute.ERROR_MESSAGE);
                    for (CasInUnit25 casInUnit : _casInUnits) {
                        casInUnit.setReturnMessage(errorMsg)
                        casInUnit.setReturnStatus(Status.ERROR)
                    }
                    registerError(errorMsg)
                    return;
                }
                for (int i = 0; i < _casInUnits.length; i++) {
                    String posString = BentoMessageAttributeGetNextLoadPosition.LOAD_POSITION_PREFIX;
                    posString += Integer.toString(i + 1);
                    locIds[i] = resultFromXPS.get(posString);
                    if (locIds[i] == null || StringUtils.isEmpty(locIds[i])) {
                        String errStr = _casMessageHelper.EMPTY_POSITION_FOR_TBD_UNIT_MESSAGE + "[id=" + unitIds[i] + "]"
                        _casInUnits[i].setReturnCode(_casMessageHelper.EMPTY_POSITION_FOR_TBD_UNIT_CODE)
                        _casInUnits[i].setReturnMessage(errStr)
                        _casInUnits[i].setReturnStatus(Status.ERROR)
                        registerError(errStr)
                        return;
                    }
                    _casInUnits[i].setAttribute(POSITION_PARAM, locIds[i])
                }
            } else { // All units submitted with position
                for (int i = 0; i < _casInUnits.length; i++) {
                    CasInUnit25 casInUnit = _casInUnits[i];
                    String unitPos = casInUnit.getAttribute(POSITION_PARAM);
                    locIds[i] = unitPos;
                }
            }
            boolean invokedViaMannedFlow = true;
            LoadDischargeTransactionInfo loadData = new LoadDischargeTransactionInfo(visitDetails, _craneId)
                    .setLiftModeEnum(liftMode)
                    .setInvokedViaMannedFlow(invokedViaMannedFlow);
            int i = 0;
            for(UnitFacilityVisit aUfv : ufvList) {
                loadData.addUnit(new LoadDischargeUnitInfo(aUfv, unitIds[i], locIds[i]))
                i++
            }
            unitManager.loadUnit(loadData);
            for (CasInUnit25 casInUnit : _casInUnits) {
                StringBuilder buffer = new StringBuilder("Unit ")
                buffer.append(casInUnit.getUnitId())
                buffer.append(" loaded to location ")
                buffer.append(casInUnit.getAttribute(POSITION_PARAM))
                casInUnit.setReturnStatus(Status.OK)
                casInUnit.setReturnCode(_casMessageHelper.UNIT_SUCCESSFULLY_PROCESSED_CODE)
                casInUnit.setReturnMessage(buffer.toString())
            }
        }
    }

    private static LiftModeEnum computeLiftMode(CasInUnit25[] unitIds) {
        if (unitIds == null || unitIds.length < 1) {
            return LiftModeEnum.UNKNOWN;
        }
        UnitManager unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID);
        LiftModeEnum liftMode = LiftModeEnum.SINGLE;
        if (unitIds.length == 4) {
            liftMode = LiftModeEnum.QUAD;
        } else if (unitIds.length == 2) {
            UnitFacilityVisit ufv = unitManager.findActiveUfvForUnitDigits(unitIds[0]._unitId);
            Unit unit = ufv.getUfvUnit();
            EquipNominalLengthEnum unitEqNomLen = unit.getPrimaryEq().getEqEquipType().getEqtypNominalLength();
            if (unitEqNomLen == EquipNominalLengthEnum.NOM20) {
                liftMode = LiftModeEnum.TWIN;
            } else {
                liftMode = LiftModeEnum.TANDEM;
            }
        }
        return liftMode;
    }

    /**
     * Load units through XPS
     */
    protected void loadUnits() {
        UnitManager unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID);
        String[] slots = new String[_casInUnits.length]
        List<UnitFacilityVisit> ufvList = new ArrayList<UnitFacilityVisit>(_casInUnits.length)
        CarrierVisit cv = CarrierVisit.findVesselVisit(ContextHelper.getThreadFacility(), _visitId)
        String[] ctrIds = new String[_casInUnits.length]
        for (int i = 0; i < _casInUnits.length; i++) {
            CasInUnit25 casInUnit = _casInUnits[i]
            ufvList.add(casInUnit._unitFacilityVisit)
            ctrIds[i] = casInUnit._unitId
            String stowPos = casInUnit.getAttribute(POSITION_PARAM)
            if (stowPos == null || stowPos.isEmpty()) {
                stowPos = findStowPositionFromPositionElement(casInUnit);
            }
            if (getMessageCollector().hasError()) {
                return;
            }
            if (StringUtils.isEmpty(stowPos)) {
                LocPosition finalStowPos = casInUnit._unitFacilityVisit.getFinalPlannedPosition()
                stowPos = finalStowPos != null ? finalStowPos.getPosSlot() : "";
            }
            slots[i] = stowPos
            UnitFacilityVisit ufv = casInUnit._unitFacilityVisit
            Unit unit = ufv.getUfvUnit()
            IEventType eventType = EventEnum.UNIT_LOAD;
            if (!LocPosition.isApron(stowPos)) {
                ServicesManager srvcMgr = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);
                BizViolation bv = srvcMgr.verifyEventAllowed(eventType, unit);
                if (bv != null) {
                    logMsg(_casMessageHelper.BIZ_ERROR_CODE + ":" + bv.getLocalizedMessage());
                    casInUnit.setReturnStatus(Status.ERROR);
                    casInUnit.setReturnCode(_casMessageHelper.BIZ_ERROR_CODE)
                    casInUnit.setReturnMessage(bv.getLocalizedMessage())
                    return;
                }
            }
        }
        LiftModeEnum liftMode = computeLiftMode(_casInUnits);
        boolean invokedViaMannedFlow = true;
        LoadDischargeTransactionInfo loadData = new LoadDischargeTransactionInfo(cv.getCvCvd(), _craneId)
                .setLiftModeEnum(liftMode)
                .setInvokedViaMannedFlow(invokedViaMannedFlow);
        int i = 0;
        for(UnitFacilityVisit aUfv : ufvList) {
            loadData.addUnit(new LoadDischargeUnitInfo(aUfv, ctrIds[i], slots[i]))
            i++
        }
        unitManager.loadUnit(loadData);
        for (i = 0; i < _casInUnits.length; i++) {
            CasInUnit25 casInUnit = _casInUnits[i]
            StringBuilder buffer = new StringBuilder("Unit ")
            buffer.append(casInUnit.getUnitId())
            buffer.append(" loaded to location ")
            buffer.append(slots[i])
            casInUnit.setReturnStatus(Status.WARNING)
            casInUnit.setReturnCode(_casMessageHelper.UNIT_SUCCESSFULLY_PROCESSED_CODE)
            casInUnit.setReturnMessage(buffer.toString())
        }
    }

    /**
     * Discharge units through XPS
     */
    protected void dischargeUnits() {
        IUnitLoadDischarge unitLoadDischarge = (IUnitLoadDischarge) Roastery.getBean(IUnitLoadDischarge.BEAN_ID);
        UnitManager unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID)

        String[] ctrIds = new String[_casInUnits.length]
        String[] laneIds = new String[_casInUnits.length];
        Boolean[] replanCtrs = new Boolean[_casInUnits.length]
        CarrierVisit cv = CarrierVisit.findVesselVisit(ContextHelper.getThreadFacility(), _visitId)
        //LiftModeEnum liftMode = computeLiftMode(_casInUnits);
        //LoadDischargeTransactionInfo dischargeData = new LoadDischargeTransactionInfo(cv.getCvCvd(), _craneId)
        //      .setLiftModeEnum(liftMode)
        //    .setInvokedViaMannedFlow(true);
        for (int i = 0; i < _casInUnits.length; i++) {
            CasInUnit25 casInUnit = _casInUnits[i]
            ctrIds[i] = casInUnit._unitId
            laneIds[i] = casInUnit._laneId
            replanCtrs[i] = Boolean.FALSE
            String stowPos = casInUnit.getAttribute(POSITION_PARAM)
            if (StringUtils.isEmpty(stowPos)) {
                LocPosition finalStowPos = casInUnit._unitFacilityVisit.getFinalPlannedPosition()
                stowPos = finalStowPos != null ? finalStowPos.getPosSlot() : "";
            }
            if (StringUtils.isEmpty(laneIds[i])) {
                laneIds[i] = stowPos == null ? "" : stowPos
            }
            /*if (dischargeData != null) {
              dischargeData.addUnit(new LoadDischargeUnitInfo(casInUnit._unitId, laneIds[i]).setTier(_tier))
            }*/
        }

        //LOGGER.debug("dischargeData"+dischargeData)
        LiftModeEnum liftMode = computeLiftMode(_casInUnits)
        //unitLoadDischarge.dischargeUnit(_craneId, cv.getCvCvd(), ctrIds, replanCtrs, laneIds, _tier, null, liftMode)
        unitManager.dischargeUnit(_craneId, cv.getCvCvd(), ctrIds, replanCtrs, laneIds, _tier, null, liftMode,false)
        //unitLoadDischarge.dischargeUnit(dischargeData);
        for (int i = 0; i < _casInUnits.length; i++) {
            CasInUnit25 casInUnit = _casInUnits[i]
            StringBuilder buffer = new StringBuilder("Unit ")
            buffer.append(casInUnit.getUnitId())
            buffer.append(" discharged to location ")
            buffer.append(laneIds[i])
            casInUnit.setReturnStatus(Status.WARNING)
            casInUnit.setReturnCode(_casMessageHelper.UNIT_SUCCESSFULLY_PROCESSED_CODE)
            casInUnit.setReturnMessage(buffer.toString())
        }
    }

    private String findStowPositionFromPositionElement(CasInUnit25 inCasInUnit) {
        String stowPos = "";
        Node unitNode = inCasInUnit.getUnitNode();
        NodeList positionNodeList = unitNode."position";
        if (!positionNodeList.isEmpty()) {
            Node positionNode = positionNodeList.get(0);
            if (positionNode != null) {
                String block = getBlock(inCasInUnit, positionNode);
                if (block != null) {
                    String column = getColumn(inCasInUnit, positionNode);
                    if (column != null) {
                        String row = getRow(inCasInUnit, positionNode);
                        if (row != null) {
                            stowPos = row.concat(column);
                            String tier = positionNode."@tier";
                            if (stowPos != null) {
                                if (tier != null && tier.length() > 0) {
                                    stowPos = getPositionWithTier(stowPos, tier);
                                } else {
                                    Long stackGkey = findStackGkey(block, row, column);
                                    if (stackGkey == null) {
                                        logError(inCasInUnit, _casMessageHelper.INVALID_POSITION, _casMessageHelper.INVALID_POSITION_MESSAGE);
                                        return null;
                                    }
                                    stowPos = getPositionWithNoTier(inCasInUnit, stowPos, stackGkey);
                                }
                            }
                        }
                    }
                }
            }
        }
        return stowPos;
    }

    private String getBlock(CasInUnit25 inCasInUnit, Node inPositionNode) {
        String block = inPositionNode."@block";
        if (block == null || block.isEmpty()) {
            logMsg("Block attribute of Position element is either missing or is empty");
            logError(inCasInUnit, _casMessageHelper.POSITION_NULL_BLOCK, _casMessageHelper.POSITION_NULL_BLOCK_MESSAGE);
            return;
        }
        return block;
    }

    private String getColumn(CasInUnit25 inCasInUnit, Node inPositionNode) {
        String column = inPositionNode."@column";
        if (column == null || column.isEmpty()) {
            logMsg("Column attribute of Position element is either missing or is empty");
            logError(inCasInUnit, _casMessageHelper.POSITION_NULL_COLUMN, _casMessageHelper.POSITION_NULL_COLUMN_MESSAGE);
            return;
        }
        return column;
    }

    private String getRow(CasInUnit25 inCasInUnit, Node inPositionNode) {
        String row = inPositionNode."@row";
        if (row == null || row.isEmpty()) {
            logMsg("Row attribute of Position element is either missing or is empty");
            logError(inCasInUnit, _casMessageHelper.POSITION_NULL_ROW, _casMessageHelper.POSITION_NULL_ROW_MESSAGE);
            return;
        }
        return row;
    }

    private String getPositionWithTier(String inStowPos, String inTier) {
        if (inStowPos == null) {
            return null;
        }
        return inStowPos.concat(inTier);
    }

    private logError(CasInUnit25 inCasInUnit, String inReturnCode, String inReturnMessage) {
        inCasInUnit.setReturnStatus(Status.ERROR);
        inCasInUnit.setReturnCode(inReturnCode);
        inCasInUnit.setReturnMessage(inReturnMessage);
        registerError(inReturnCode);
    }

    private String getPositionWithNoTier(CasInUnit25 inCasInUnit, String inStowPos, Long inStackGkey) {
        if (inStackGkey == null) {
            return null;
        }
        String stowPos = inStowPos;
        IVesselPositionableFinder posFinder = (IVesselPositionableFinder) Roastery.getBean(IVesselPositionableFinder.BEAN_ID);
        AbstractBin bin = (AbstractBin) HibernateApi.getInstance().get(AbstractBin.class, inStackGkey);
        if (bin == null) {
            logError(inCasInUnit, _casMessageHelper.POSITION_BIN_NULL, _casMessageHelper.POSITION_BIN_NULL_MESSAGE);
            return null;
        }
        CarrierVisit carrierVisit = CarrierVisit.findVesselVisit(ContextHelper.getThreadFacility(), _visitId);
        if (carrierVisit != null) {
            IPositionable pos = getWorkInstructionFromCasUnit(inCasInUnit);
            if (pos != null) {
                String tier = posFinder.getNextStepLocationToLoad(bin, carrierVisit, pos);
                if (tier != null && tier.length() > 0) {
                    stowPos = inStowPos.concat(tier);
                }
            }
        }
        return stowPos;
    }

    private IPositionable getWorkInstructionFromCasUnit(CasInUnit25 inCasInUnit) {
        WorkInstruction wi = null;
        IPositionable pos = null;
        if (inCasInUnit != null) {
            UnitFacilityVisit unitFacilityVisit = inCasInUnit.getUnitFacilityVisit();
            if (unitFacilityVisit == null) {
                logMsg("UnitFacilityVisit is null");
                return null;
            }
            wi = unitFacilityVisit.getNextWorkInstruction();
            if (wi == null) {
                logMsg("WorkInstruction for the Unit UFV is null");
                return null;
            }
            pos = new SavedValuePositionable(IPositionable.PositionType.WORK_INSTRUCTION, wi.getPrimaryKey(), null,
                    wi.getValueObject());
        }
        return pos;
    }

    private Long findStackGkey(String inBlock, String inRow, String inColumn) {
        Long binAbnGkey = null;
        CarrierVisit cv = CarrierVisit.findVesselVisit(ContextHelper.getThreadFacility(), _visitId);
        if (cv != null) {
            VesselClass vesClass = (VesselClass) cv.getCarrierVesselClass();
            if (vesClass != null) {
                AbstractBin vesclassBinModel = vesClass.getVesclassBinModel();
                if (vesclassBinModel == null) {
                    logMsg("Vessel Bin Class Model is null for vessel class[" + vesClass.getVesclassId() + "]");
                    return null;
                }
                final String binName = BinModelHelper.computeModelInternalBinName(inBlock, inRow, inColumn);
                if (binName != null) {
                    AbstractBin bin = vesclassBinModel.findDescendantBinFromInternalSlotString(binName, null);
                    if (bin != null) {
                        binAbnGkey = bin.getAbnGkey();
                    } else {
                        logMsg("could not find bin for binName [" + binName + "]");
                        return null;
                    }
                }
            }
        }
        return binAbnGkey;
    }

    /**
     * Create response message to CAS request
     * @param inAdditionalInfo
     * @return String
     */
    protected String createResponseXml(Map<String, String> inAdditionalInfo) {
        def writer = new StringWriter();
        def xml = new MarkupBuilder(writer);
        xml.payload() {
            parameters() {
                if (_parameterMap != null && !_parameterMap.isEmpty()) {
                    _parameterMap.keySet().each {
                        parameter(id: it, value: _parameterMap.get(it));
                    }
                }
            }
            if (inAdditionalInfo != null && !inAdditionalInfo.isEmpty()) {
                "additional-info"() {
                    inAdditionalInfo.keySet().each {
                        field(id: it, value: inAdditionalInfo.get(it));
                    }
                }
            }
            "units-response"() {
                for (CasInUnit25 casInUnit : _casInUnits) {
                    "unit-response"("cas-unit-reference": casInUnit.getCasUnitReference(), "cas-transaction-reference": casInUnit.getCasTransactionReference(),
                            id: casInUnit.getUnitId(), status: casInUnit.getStatusAsString()) {
                        message(code:casInUnit.getReturnCode(), text: casInUnit.getReturnMessage())
                    }
                }
            }
        }
        String out = writer.toString();
        return out;
    }

    private void initCasMessageHelper() {
        //Get the CasMessageHelper instance
        if (_casMessageHelper == null) {
            _casMessageHelper = getLibrary(CAS_MESSAGE_HELPER);
        }
    }

    protected void registerAndLogError() {
        registerError(_errorCode + ":" + _errorMessage);
        logMsg(_errorCode + ":" + _errorMessage);
    }

    /**
     * Creates xml to be returned in case of errors
     * @param inErrorCode error code
     * @param inErrorMessage error message
     * @return
     */
    private String getXmlErrorContent(){
        def writer = new StringWriter();
        def xml = new MarkupBuilder(writer);
        xml.payload() {
            parameters(){
                if (_parameterMap != null && !_parameterMap.isEmpty()) {
                    _parameterMap.keySet().each {
                        parameter(id:it, value:_parameterMap.get(it));
                    }
                }
            }
            error(code:_errorCode, message:_errorMessage) {
            }
        }
        String out = writer.toString();
        logMsg("\nRequest: " + getParametersAsString() + "\nResponse : " + out)
        return out;
    }

    /**
     * This class encapsulates the unit information coming from the CAS
     * It also holds any error information added during validation and unit facility visit information if validation is successful
     */
    protected class CasInUnit25 {

        CasInUnit25(Node inUnitNode) {
            _unitNode = inUnitNode
            _casUnitReference = _unitNode."@cas-unit-reference"
            _casTransactionReference = _unitNode."@cas-transaction-reference"
            _unitId = _unitNode."@id"
            _returnStatus = Status.WARNING
            _returnCode = _casMessageHelper.UNIT_NOT_PROCESSED_CODE
            _returnMessage = _casMessageHelper.UNIT_NOT_PROCESSED_MESSAGE
            _laneId = _unitNode."@lane"
            _trkPos = _unitNode."@trkPos"
        }

        private String _laneId;
        private String _casUnitReference
        private String _casTransactionReference
        private String _unitId
        private UnitFacilityVisit _unitFacilityVisit
        private Unit _unit
        //Message which will be returned in the response for this unit
        private String _returnMessage
        private String _returnCode
        private Status _returnStatus
        private Node _unitNode
        private String _trkPos
        //A generic fields for any additional attributes which may be used by subclasses
        protected Map<String, Object> _attributeMap = new HashMap<String, Object>();

        void setAttribute(String inString, Object inObject) {
            _attributeMap.put(inString, inObject)
        }

        Object getAttribute(String inString) {
            if (_attributeMap.get(inString) != null) {
                return _attributeMap.get(inString);
            } else {
                return null;
            }
        }

        Object removeAttribute(String inString) {
            return _attributeMap.remove(inString)
        }

        Unit getUnit() {
            return _unit
        }

        void setUnit(Unit inUnit) {
            _unit = inUnit
        }

        Node getUnitNode() {
            return _unitNode
        }

        String getCasUnitReference() {
            return _casUnitReference
        }

        String getCasTransactionReference() {
            return _casTransactionReference
        }

        String getUnitId() {
            return _unitId
        }

        UnitFacilityVisit getUnitFacilityVisit() {
            return _unitFacilityVisit
        }

        void setUnitFacilityVisit(UnitFacilityVisit inUnitFacilityVisit) {
            _unitFacilityVisit = inUnitFacilityVisit
        }

        boolean hasError() {
            return Status.ERROR == _returnStatus
        }

        boolean hasWarning() {
            return Status.WARNING == _returnStatus
        }

        boolean isOK() {
            return Status.OK == _returnStatus
        }

        public String getStatusAsString() {
            switch (_returnStatus) {
                case Status.OK: return "OK"
                case Status.ERROR: return "ERROR"
                case Status.WARNING: return "WARNING"
            }
            return "";
        }

        String getReturnMessage() {
            return _returnMessage
        }

        void setReturnMessage(String inReturnMessage) {
            _returnMessage = inReturnMessage
        }

        Status getReturnStatus() {
            return _returnStatus
        }

        void setReturnStatus(Status inStatus) {
            _returnStatus = inStatus
        }

        String getReturnCode() {
            return _returnCode
        }

        void setReturnCode(String inReturnCode) {
            _returnCode = inReturnCode
        }
    }


    String getTrkPos() {
        return _trkPos
    }

    void setTrkPos(String trkPos) {
        _trkPos = trkPos
    }

    private void logMsg(Object inMsg) {
        LOGGER.debug(inMsg);
    }

    private List<WorkInstruction> findWorkInstruction(String carryCheId, Unit unit, GeneralReference generalReference) {
        DomainQuery dq = QueryUtils.createDomainQuery("WorkInstruction")
        dq.addDqPredicate(PredicateFactory.eq(MovesField.MVHS_CARRY_CHE_ID, carryCheId))
        dq.addDqPredicate(PredicateFactory.eq(MovesField.WI_MOVE_STAGE, WiMoveStageEnum.CARRY_UNDERWAY))
        dq.addDqPredicate(PredicateFactory.eq(MovesField.WI_MOVE_KIND, WiMoveKindEnum.VeslDisch))
        LOGGER.debug("dq " + dq)
        return (List) HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
    }

    /**
     * Move to Yard heap position specified in General Reference.
     * @param inWorkInstruction
     * @param generalReference
     */
    private void updateToPosition(WorkInstruction inWorkInstruction, GeneralReference generalReference) {

        //getWiUfv()
        if (inWorkInstruction == null) {
            return;
        }

        UnitFacilityVisit inUfv = inWorkInstruction.getWiUfv();

        String heapBlock = "MARUTL";
        if (generalReference != null && generalReference.getRefValue1() != null) {
            heapBlock = generalReference.getRefValue1();
        }
        if (inUfv != null) {
            try {

                LocPosition locPosition = null;

                LocPosition wiPostion = inWorkInstruction.getWiPosition();
                AbstractBin wiPosBin = wiPostion != null ? wiPostion.getPosBin() : null;
                AbstractBin blockBin = (wiPosBin != null) ? wiPosBin.findAncestorBinAtLevel(IBinModel.BIN_LEVEL_BLOCK) : null;
                AbstractYardBlock ayBlock = (blockBin != null) ? (AbstractYardBlock) HibernateApi.getInstance().downcast(blockBin, AbstractYardBlock.class) : null;
                EquipBasicLengthEnum equipBasicLengthEnum = inUfv.getUfvUnit() != null ? inUfv.getUfvUnit().getBasicLength() : null;
                if ((wiPostion != null && wiPostion.isWheeled()) || (ayBlock != null && YardBlockTypeEnum.HEAP.equals(ayBlock.getAyblkBlockType()))) {
                    String locPosId = wiPostion != null ? wiPostion.getPosLocId() : null;
                    String blockName = wiPostion != null ? wiPostion.getBlockName() : null;
                    locPosition = LocPosition.resolvePosition(ContextHelper.getThreadFacility(), LocTypeEnum.YARD, locPosId, blockName, null, equipBasicLengthEnum)

                } else {
                    locPosition = LocPosition.createYardPosition(ContextHelper.getThreadYard(), heapBlock, null, equipBasicLengthEnum, true)

                }
                try {
                    inUfv.move(locPosition, null);
                } catch (Exception ext) {
                    //LOGGER.debug("Exception while move performed ::" + ext);
                }
                if (inUfv.getUfvUnit() != null) {
                    inUfv.getUfvUnit().recordUnitEvent(EventEnum.UNIT_YARD_MOVE, null, "Moving to Yard");
                    inUfv.setUfvTransitState(UfvTransitStateEnum.S40_YARD)
                    if (inWorkInstruction.getWiPosition() != null) {
                        inUfv.getUfvUnit().setUnitFlexString02(inWorkInstruction.getWiPosition().getPosName());
                        inUfv.setFieldValue(InvField.UFV_FLEX_STRING04, null)


                    }

                }
                HibernateApi.getInstance().save(inUfv);
                HibernateApi.getInstance().save(inUfv.getUfvUnit());
                HibernateApi.getInstance().flush();
            } catch (Exception ex) {
                LOGGER.debug("Exception Occured while updating the to position ::" + ex);
            }
        }
        LOGGER.debug("updateToPosition completed :");
    }

    private boolean isTwentyLengthCtr(Unit inUnit) {
        EquipType equipType = inUnit.getPrimaryEq() != null ? inUnit.getPrimaryEq().getEqEquipType() : null;
        return equipType != null && EquipBasicLengthEnum.BASIC20.equals(equipType.getEqtypBasicLength());
    }

    public static double differenceInSeconds(Date inStartDate, Date inEndDate) {
        double hours = (double) (inEndDate.getTime() - inStartDate.getTime()) * 1.0D / 1000.0D;
        return hours;
    }
}