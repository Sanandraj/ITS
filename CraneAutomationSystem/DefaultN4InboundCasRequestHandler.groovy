/*
 * Copyright (c) 2021 Navis LLC. All Rights Reserved.
 *
 */

package CraneAutomationSystem

import com.navis.argo.ArgoField
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoRailManager
import com.navis.argo.business.api.Serviceable
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.VisitDetails
import com.navis.argo.business.model.Yard
import com.navis.argo.rest.beans.Railcars
import com.navis.argo.util.EntityXmlStreamWithSimpleHeader
import com.navis.external.argo.AbstractGroovyWSCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.portal.query.PredicateIntf
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.framework.util.internationalization.UserMessage
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageCollectorFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.util.TransitStateQueryUtil
import com.navis.rail.RailEntity
import com.navis.rail.RailField
import com.navis.rail.business.api.RailManager
import com.navis.rail.business.entity.Railcar
import com.navis.rail.business.entity.RailcarVisit
import com.navis.rail.business.entity.TrainVisitDetails
import com.navis.spatial.business.model.AbstractBin
import com.navis.vessel.VesselEntity
import com.navis.vessel.business.schedule.VesselVisitDetails
import groovy.xml.MarkupBuilder
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException
import javax.xml.bind.Marshaller
import javax.xml.transform.OutputKeys
import javax.xml.transform.Source
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

/**
 * This class contains reference implementation to handle requests coming from the QRCAS system.
 * Supported Request types handled by this class:
 * 1. Carrier Geometry request, (Vessel or Train)
 * 2. Container List request
 *
 * @author <a href="mailto:arvinder.brar@navis.com">Arvinder Brar</a>,2012-09-06
 */
class DefaultN4InboundCasRequestHandler extends AbstractGroovyWSCodeExtension {
    private static final Logger LOGGER = Logger.getLogger(DefaultN4InboundCasRequestHandler.class);
    private final String REQUEST_TYPE_PARAM = "requestType";
    private final String VISIT_ID_PARAM = "visitId";
    private final String ON_BOARD_PARAM = "onBoard";
    private final String CRANE_ID_PARAM = "craneId";
    private final String VISIT_TYPE_PARAM = "visitType";
    private final String RCAR_ID = "railcarId";
    private final String RCAR_VISIT_ID = "railcarVisitId";
    private final String TRAIN_VISIT_ID = "trainVisitId";
    private final String YARD_ORIGIN_ID = "yardOriginId";

    private final String TRACK_NAME_PARAM = "trackName";
    private final String SEQUENCE_ON_TRACK_PARAM = "sequenceOnTrack";
    private final String PLATFORM_SEQUENCE_PARAM = "platformSequence";
    private final String LATITUDE_PARAM = "latitude";
    private final String LONGITUDE_PARAM = "longitude";
    private final String METER_MARK_PARAM = "meterMark";
    private final String TRANSFER_POINT_PARAM = "transferPoint";

    private final String VISIT_TYPE_VESSEL = "VESSEL";
    private final String VISIT_TYPE_TRAIN = "TRAIN";
    private final String VISIT_TYPE_RAIL = "RAIL";
    private final String SHIP_BIN_MODEL_ENTITY_NAME = "ShipBinModel";
    private final String RAIL_BIN_MODEL_ENTITY_NAME = "RailBinModel";
    private final String RAIL_CAR_ENTITY_NAME = "Railcar";
    private final String SHIP_GEOMETRY = "ship-geometry";
    private final String CARRIER_INFO_REQUEST = "carrierGeometryRequest";
    private final String RAIL_SPOTTING_REQUEST = "railcarSpottingRequest";
    private final String UNIT_INFO_REQUEST = "unitInfoRequest";
    private final String CONTAINER_LIST_REQUEST = "containerListRequest";
    private final String SEND_ON_BOARD_UNIT_UPDATES = "sendOnBoardUnitUpdates";
    private final String SEND_CRANE_WORK_LIST_UPDATES = "sendCraneWorkListUpdates";
    private MessageCollector _messageCollector;
    private final String RAIL_MANAGER_ERROR = "Possibly error in a Rail Manager method";

    //CasHelper library name
    public final String CAS_HELPER = "CasHelper"
    //CasHelper library code extension instance
    def _casHelper = null;
    //CasMessageHelper library name
    public final String CAS_MESSAGE_HELPER = "CasMessageHelper"
    //CasMessageHelper library code extension instance
    def _casMessageHelper = null;

    Map _inParameters = null;
    Map _additionalInfoMap = null;
    /**
     * Main entry point method of the code extension.
     * @param inParameters Map<String, Object> parameters sent as part of groovy web service
     * @return the string response to the groovy webservice call
     */

    public String execute(Map inParameters) {
        LOGGER.setLevel(Level.DEBUG);
        _inParameters = inParameters;
        _messageCollector = getMessageCollector();
        String returnXml = null;
        //Log the request content
        logMsg("\nRequest: " + getParametersAsString())
        initCasMessageHelper()

        //Validate that the logged in scope is yard
        final Yard yard = ContextHelper.getThreadYard()
        if (yard == null) {
            registerError(_casMessageHelper.LOGGED_IN_SCOPE_IS_NOT_YARD_CODE + ":" + _casMessageHelper.LOGGED_IN_SCOPE_IS_NOT_YARD_MESSAGE);
            return getXmlErrorContent(_casMessageHelper.LOGGED_IN_SCOPE_IS_NOT_YARD_CODE, _casMessageHelper.LOGGED_IN_SCOPE_IS_NOT_YARD_MESSAGE);
        }

        Map<String, String> additionalInfoMap = new HashMap<String, String>();
        _additionalInfoMap = additionalInfoMap;
        MetafieldId carrierVisitDetailsCarrierVisitId = MetafieldIdFactory.getCompoundMetafieldId(ArgoField.CVD_CV, ArgoField.CV_ID);

        //validate requestType. this just make sure that parameter is not missing.
        String[] validationResult;
        validationResult = validateParameters(REQUEST_TYPE_PARAM);
        if (validationResult[0].equals("error")) {
            return validationResult[1];
        }

        //validate visitType parameter this just make sure that parameter is not missing.
        validationResult = validateParameters(VISIT_TYPE_PARAM);
        if (validationResult[0].equals("error")) {
            return validationResult[1];
        }

        //validate requestType parameter
        String requestType = inParameters.get(REQUEST_TYPE_PARAM);
        if (!isValidRequestType(requestType)) {
            final String inValidReqTypeErrMessage = _casMessageHelper.INVALID_REQUEST_TYPE_MESSAGE + "[" + requestType + "]";
            registerError(_casMessageHelper.INVALID_REQUEST_TYPE_CODE + ":" + inValidReqTypeErrMessage);
            return getXmlErrorContent(_casMessageHelper.INVALID_REQUEST_TYPE_CODE, inValidReqTypeErrMessage);
        }

        String visitId = inParameters.get(VISIT_ID_PARAM);
        String visitType = inParameters.get(VISIT_TYPE_PARAM);

        if (RAIL_SPOTTING_REQUEST.equals(requestType) && VISIT_TYPE_RAIL.equalsIgnoreCase(visitType)) {
            final Long yardGkey = yard.getYrdGkey();
            returnXml = handleRailcarSpottingUpdate(inParameters, yardGkey);
            return returnXml;
        }

        if (CARRIER_INFO_REQUEST.equals(requestType) && VISIT_TYPE_RAIL.equalsIgnoreCase(visitType)) {
            String[] railcarVisitIdList = null;
            String railcarVisitId = inParameters.get(VISIT_ID_PARAM);
            if (railcarVisitId == null) {
                railcarVisitId = inParameters.get(RCAR_VISIT_ID);
            }
            if (railcarVisitId != null) {
                railcarVisitIdList = railcarVisitId.split(",");
            }

            validateRailcarVisits(railcarVisitIdList)
            if (getMessageCollector().hasError()){
                return getXmlErrorContent(_casMessageHelper.BIZ_ERROR_CODE, _casMessageHelper.BIZ_ERROR_MESSAGE)
            }

            final String trainVisitId = inParameters.get(TRAIN_VISIT_ID)
            if (trainVisitId != null) { // it is totally ok for train visit to be null, no error in that case
                if (CarrierVisit.findTrainVisit(ContextHelper.getThreadComplex(), ContextHelper.getThreadFacility(), trainVisitId) == null) {
                    return getXmlErrorContent(_casMessageHelper.NO_CARRIER_VISIT_FOUND_CODE,
                            _casMessageHelper.NO_CARRIER_VISIT_FOUND_MESSAGE + "[visitId=" + trainVisitId + "]")
                }
            }

            MessageCollector mc = MessageCollectorFactory.createMessageCollector()
            final String trackName = inParameters.get(TRACK_NAME_PARAM);
            final String yardOriginId = inParameters.get(YARD_ORIGIN_ID);
            String xmlResponse
            try {
                xmlResponse = getCarrierGeometryXmlResponse(mc, trainVisitId, trackName, railcarVisitIdList, yardOriginId);
            } catch (Throwable ex) {
                final String errMsg = RAIL_MANAGER_ERROR + " " + ex.getCause() + ":" + ex.getMessage()
                registerError(errMsg)
                return getXmlErrorContent(_casMessageHelper.BIZ_ERROR_CODE, errMsg)
            }

            if (mc.hasError()) {
                registerError(RAIL_MANAGER_ERROR)
                return getXmlErrorContent(_casMessageHelper.BIZ_ERROR_CODE, RAIL_MANAGER_ERROR)
            }

            returnXml = getXmlContent(additionalInfoMap, xmlResponse);
            returnXml = prettyFormat(returnXml);
            logMsg("\nResponse: " + returnXml)
            return returnXml;
        }

        final String carrierVisitEntityName;
        if (VISIT_TYPE_VESSEL.equals(visitType)) {
            carrierVisitEntityName = VesselEntity.VESSEL_VISIT_DETAILS;
        } else if (VISIT_TYPE_TRAIN.equals(visitType)) {
            carrierVisitEntityName = RailEntity.TRAIN_VISIT_DETAILS;
        } else {
            registerError(_casMessageHelper.INVALID_VISIT_TYPE_CODE + ":" + _casMessageHelper.INVALID_VISIT_TYPE_MESSAGE);
            return getXmlErrorContent(_casMessageHelper.INVALID_VISIT_TYPE_CODE, _casMessageHelper.INVALID_VISIT_TYPE_MESSAGE);
        }
        DomainQuery dq = QueryUtils.createDomainQuery(carrierVisitEntityName)
                .addDqPredicate(PredicateFactory.eq(carrierVisitDetailsCarrierVisitId, visitId));
        VisitDetails carrierVisitDetails = (VisitDetails) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);

        if (carrierVisitDetails == null && !carrierVisitEntityName.equals(RAIL_CAR_ENTITY_NAME)) {
            registerError(
                    _casMessageHelper.NO_CARRIER_VISIT_FOUND_CODE + ":" + _casMessageHelper.NO_CARRIER_VISIT_FOUND_MESSAGE + "[visitId=" + visitId + "]");
            return getXmlErrorContent(_casMessageHelper.NO_CARRIER_VISIT_FOUND_CODE,
                    _casMessageHelper.NO_CARRIER_VISIT_FOUND_MESSAGE + "[visitId=" + visitId + "]");
        }

        boolean isVisitTypeVessel = VISIT_TYPE_VESSEL.equals(visitType);
        CarrierVisit cv = carrierVisitDetails.getCvdCv();
        Serviceable cvd = isVisitTypeVessel ? VesselVisitDetails.resolveVvdFromCv(cv) : TrainVisitDetails.resolveTvdFromCv(cv);
        if (CARRIER_INFO_REQUEST.equals(requestType)) {
            AbstractBin binModel = (AbstractBin) carrierVisitDetails.getCarrierBinModel();
            if (binModel == null) {
                registerError(
                        _casMessageHelper.CARRIER_GEOMETRY_NOT_FOUND_CODE + ":" + _casMessageHelper.CARRIER_GEOMETRY_NOT_FOUND_MESSAGE + "[visitId=" + visitId +
                                "]");
                return getXmlErrorContent(_casMessageHelper.CARRIER_GEOMETRY_NOT_FOUND_CODE,
                        _casMessageHelper.CARRIER_GEOMETRY_NOT_FOUND_MESSAGE + "[visitId=" + visitId + "]");
            }
            Serializable[] primaryKeys = new Serializable[1];
            primaryKeys[0] = binModel.getAbnGkey();
            String binModelEntityName = (isVisitTypeVessel) ? SHIP_BIN_MODEL_ENTITY_NAME : RAIL_BIN_MODEL_ENTITY_NAME;
            InputStream entityXmlStream = new EntityXmlStreamWithSimpleHeader(binModelEntityName, primaryKeys, SHIP_GEOMETRY, null);
            String shipVisitContent = "\n" + entityXmlStream.getText();
            //An example of adding additional information
            /* additionalInfoMap.put("aField", "aFieldValue");*/
            returnXml = getXmlContent(additionalInfoMap, shipVisitContent);
            if (!_messageCollector.hasError()) {
                if (isVisitTypeVessel) {
                    recordEvent(cvd, EventEnum.CAS_VV_REQUEST_GEOMETRY, null, null);
                } else {
                    recordEvent(cvd, EventEnum.CAS_RV_REQUEST_GEOMETRY, null, null);
                }
            }
        } else if (CONTAINER_LIST_REQUEST.equals(requestType)) {
            //Get the CasHelper instance
            _casHelper = getLibrary(CAS_HELPER);

            //Check the 'onBoard' and craneId parameters
            Boolean sendOnBoard = "Y".equalsIgnoreCase(inParameters.get(ON_BOARD_PARAM));
            String craneIds = inParameters.get(CRANE_ID_PARAM);

            if (inParameters.get(SEND_ON_BOARD_UNIT_UPDATES) != null) {
                final Boolean sendOnBoardUnitUpdates = "Y".equalsIgnoreCase(inParameters.get(SEND_ON_BOARD_UNIT_UPDATES));
                cv.setFieldValue(ArgoField.CV_SEND_ON_BOARD_UNIT_UPDATES, sendOnBoardUnitUpdates);
                boolean updatedSendOnBoardUnitUpdates = cv.getCvSendOnBoardUnitUpdates();
                HibernateApi.getInstance().saveOrUpdate(cv);
                HibernateApi.getInstance().flush();
                logMsg("CV_SEND_CRANE_WORK_LIST_UPDATES VALUE " + updatedSendOnBoardUnitUpdates);
            }

            if (inParameters.get(SEND_CRANE_WORK_LIST_UPDATES) != null) {
                final Boolean sendCraneWorkListUpdates = "Y".equalsIgnoreCase(inParameters.get(SEND_CRANE_WORK_LIST_UPDATES));
                cv.setFieldValue(ArgoField.CV_SEND_CRANE_WORK_LIST_UPDATES, sendCraneWorkListUpdates);
                boolean updatedSendCraneWorkListUpdates = cv.getFieldValue(ArgoField.CV_SEND_CRANE_WORK_LIST_UPDATES);
                HibernateApi.getInstance().saveOrUpdate(cv);
                HibernateApi.getInstance().flush();
                logMsg("CV_SEND_CRANE_WORK_LIST_UPDATES VALUE " + updatedSendCraneWorkListUpdates);
            }

            Map<String, String> warningMap = new HashMap<String, String>();
            if (!sendOnBoard && StringUtils.isBlank(craneIds)) {
                registerWarning(
                        _casMessageHelper.CONTAINER_LIST_REQUEST_MISSING_PARAM_CODE + ":" + _casMessageHelper.CONTAINER_LIST_REQUEST_MISSING_PARAM_MESSAGE);
                warningMap.put(_casMessageHelper.CONTAINER_LIST_REQUEST_MISSING_PARAM_CODE, _casMessageHelper.CONTAINER_LIST_REQUEST_MISSING_PARAM_MESSAGE)
            }
            returnXml = createContainerListXmlContent(additionalInfoMap, sendOnBoard, craneIds, carrierVisitDetails, warningMap);
            if (!_messageCollector.hasError()) {
                if (isVisitTypeVessel) {
                    recordEvent(cvd, EventEnum.CAS_VV_REQUEST_CONTAINER_LIST, null, null);
                } else {
                    recordEvent(cvd, EventEnum.CAS_RV_REQUEST_CONTAINER_LIST, null, null);
                }
            }
        } else {
            registerError(_casMessageHelper.INVALID_REQUEST_TYPE_CODE + ":" + _casMessageHelper.INVALID_REQUEST_TYPE_MESSAGE);
            return getXmlErrorContent(_casMessageHelper.INVALID_REQUEST_TYPE_CODE, _casMessageHelper.INVALID_REQUEST_TYPE_MESSAGE);
        }
        if (!_messageCollector.hasError()) {
            logMsg("\nRequest: " + getParametersAsString() + "\nResponse : Successfully processed response")
            return returnXml;
        } else {
            logMsg("\nRequest: " + getParametersAsString() + "\nResponse : Error while processing request")
            return getXmlErrorContent(_casMessageHelper.BIZ_ERROR_CODE, _casMessageHelper.BIZ_ERROR_MESSAGE);
        }
    }

    private boolean isValidRequestType(String inRequestType) {
        final String[] validRequestTypes = [CARRIER_INFO_REQUEST, UNIT_INFO_REQUEST, CONTAINER_LIST_REQUEST, RAIL_SPOTTING_REQUEST];
        for (String validRequestType : validRequestTypes) {
            if (validRequestType.equals(inRequestType)) {
                return true;
            }
        }
        return false;
    }

    private void validateRailcarVisits(String[] inRailcarVisitIds) {
        for (String railcarVisitId : inRailcarVisitIds) {
            if (!(doesActiveRailCarVisitExist(railcarVisitId))) {
                final String key = _casMessageHelper.NO_CARRIER_VISIT_FOUND_CODE
                final String errorMessage = _casMessageHelper.NO_CARRIER_VISIT_FOUND_MESSAGE + "[" + railcarVisitId + "]"
                registerError(key + " : " + errorMessage)
            }
        }
    }

    private boolean doesActiveRailCarVisitExist(final String inRcarVisitId) {
        DomainQuery dq = QueryUtils.createDomainQuery(RailEntity.RAILCAR_VISIT)
                .addDqPredicate(PredicateFactory.eq(RailField.RCARV_ID, inRcarVisitId))
                .addDqPredicate(PredicateFactory.eq(RailField.RCARV_IS_ACTIVE, Boolean.TRUE));
        RailcarVisit rcarVisit = (RailcarVisit) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
        return (rcarVisit != null);
    }

    private String handleRailcarSpottingUpdate(final Map inParameters, final Long inYardGkey) {

        //Getting and Validating Required Parameters
        final String railcarId = inParameters.get(RCAR_ID);
        if (StringUtils.isEmpty(railcarId) || String.valueOf(null).equals(railcarId)) {
            return getXmlErrorContent(_casMessageHelper.MISSING_RAILCAR_ID_CODE, _casMessageHelper.MISSING_RAILCAR_ID_MESSAGE);
        }

        final Railcar railcar = Railcar.findRailcar(railcarId)
        if (railcar == null) {
            return getXmlErrorContent(_casMessageHelper.NO_RAILCAR_FOUND_CODE, _casMessageHelper.NO_RAILCAR_FOUND_MESSAGE)
        }

        final RailcarVisit railcarVisit = RailcarVisit.findActiveRailCarVisit(railcar)
        if (railcarVisit == null) {
            return getXmlErrorContent(_casMessageHelper.NO_CARRIER_VISIT_FOUND_CODE, _casMessageHelper.NO_CARRIER_VISIT_FOUND_MESSAGE)
        }

        final String trackName = inParameters.get(TRACK_NAME_PARAM);
        if (StringUtils.isEmpty(trackName) || String.valueOf(null).equals(trackName)) {
            return getXmlErrorContent(_casMessageHelper.MISSING_OR_INVALID_TRACK_NAME_CODE,
                    _casMessageHelper.MISSING_OR_INVALID_TRACK_NAME_MESSAGE);
        }

        final Integer sequenceOnTrack = getInteger(inParameters.get(SEQUENCE_ON_TRACK_PARAM))
        if (sequenceOnTrack == null) {
            return getXmlErrorContent(_casMessageHelper.MISSING_OR_INVALID_ON_TRACK_SEQUENCE_CODE,
                    _casMessageHelper.MISSING_OR_INVALID_ON_TRACK_SEQUENCE_MESSAGE);
        }

        final Integer platformSequence = getInteger(inParameters.get(PLATFORM_SEQUENCE_PARAM))
        if (platformSequence == null) {
            return getXmlErrorContent(_casMessageHelper.INVALID_PLATFORM_SEQUENCE_CODE, _casMessageHelper.INVALID_PLATFORM_SEQUENCE_MESSAGE);
        }

        //Get Optional Parameters but at least one of them are required.
        final Double latitude = getDouble(inParameters.get(LATITUDE_PARAM));
        final Double longitude = getDouble(inParameters.get(LONGITUDE_PARAM));
        final Double meterMark = getDouble(inParameters.get(METER_MARK_PARAM));
        final String transferPoint = inParameters.get(TRANSFER_POINT_PARAM);

        final boolean isMissingData =  isMissingDataToSpotRailcar(latitude, longitude, meterMark, transferPoint);
        if (isMissingData) {
            return getXmlErrorContent(_casMessageHelper.SPOTTING_REQUEST_MISSING_DATA_CODE, _casMessageHelper.SPOTTING_REQUEST_MISSING_DATA_MESSAGE);
        }

        MessageCollector mc = MessageCollectorFactory.createMessageCollector();
        String geometryInfoXml;

        try {
            logMsg("calling RailManager::spotRailcar()")
            RailManager rm = (RailManager) Roastery.getBean(RailManager.BEAN_ID)
            rm.spotRailcar(railcarId, trackName, sequenceOnTrack, platformSequence, latitude, longitude, meterMark, transferPoint)
            final String yardOriginId = inParameters.get(YARD_ORIGIN_ID);
            geometryInfoXml = getCarrierGeometryXmlResponse(mc, null, trackName, null, yardOriginId);
        } catch (BizViolation bv) {
            final String errMsg = RAIL_MANAGER_ERROR + ": " + bv.getLocalizedMessage()
            registerError(errMsg)
            return getXmlErrorContent(_casMessageHelper.BIZ_ERROR_CODE, errMsg)
        } catch (Throwable ex) {// We have this block as we are not going through framework machinery which catches all exceptions for us
            // here we have to catch exceptions other than BizViolation and BizFailure as well
            final String errMsg = RAIL_MANAGER_ERROR + " " + ex.getCause() + ":" + ex.getMessage()
            registerError(errMsg)
            return getXmlErrorContent(_casMessageHelper.BIZ_ERROR_CODE, errMsg)
        }

        if (mc.hasError()) {
            String errMsg = RAIL_MANAGER_ERROR
            List<UserMessage> messages = mc.getMessages(MessageLevel.SEVERE)
            for (UserMessage inUserMessage : messages) {
                if (inUserMessage instanceof Throwable){
                    errMsg += ", " + inUserMessage.getLocalizedMessage()
                }
            }
            registerError(errMsg)
            return getXmlErrorContent(_casMessageHelper.BIZ_ERROR_CODE, errMsg)
        }

        geometryInfoXml = geometryInfoXml.replaceAll("\\<\\?xml(.+?)\\?\\>", "").trim();
        String response = getXmlContent(_additionalInfoMap, geometryInfoXml);
        response = prettyFormat(response);
        return response;
    }

    /**
     * returns CarrierGeometryXmlResponse
     * @param inMc MessageCollector this will contain error messages if any
     * @param inTrainVisitId train's visit id
     * @param inTrackName Rail TrackName
     * @param inRailcarVisitIds railcar visit ids
     * @param inYardOriginId YardOriginId optional parameter
     * @return geometry xml
     */
    protected String getCarrierGeometryXmlResponse(@NotNull final MessageCollector inMc, @Nullable final String inTrainVisitId,
                                                   @Nullable final String inTrackName, @Nullable final String[] inRailcarVisitIds,
                                                   @Nullable final String inYardOriginId) {
        Railcars rcars;
        String xmlStr = "";

        ArgoRailManager argoRailManager = (ArgoRailManager) Roastery.getBean(ArgoRailManager.BEAN_ID)

        logMsg("calling ArgoRailManager::getRailcarPlatformSlotsGeometry()")
        if (inRailcarVisitIds == null) {
            rcars = argoRailManager.getRailcarPlatformSlotsGeometry(inTrainVisitId, inTrackName, null, inYardOriginId);
            xmlStr += getXmlStrForCombination(inMc, rcars)
        } else {
            for (String visitId : inRailcarVisitIds) {
                rcars = argoRailManager.getRailcarPlatformSlotsGeometry(inTrainVisitId, inTrackName, visitId, inYardOriginId)
                xmlStr += getXmlStrForCombination(inMc, rcars)
            }
        }

        return xmlStr;
    }
    /**
     * get xmlString for single combination
     */
    private String getXmlStrForCombination(MessageCollector inMc, Railcars inRcars) {
        String retVal = "";
        try {
            retVal = getXmlStringForCarrierGeometryRequest(inRcars);
        } catch (JAXBException jbe) {
            UserMessage userMessage = BizFailure.create(jbe.toString());
            inMc.appendMessage(userMessage);
        }
        return retVal;
    }
    /**
     * get xml for railcar geometry request
     */
    private String getXmlStringForCarrierGeometryRequest(Railcars inRcars) throws JAXBException {
        // serialize to xml
        StringWriter writer = new StringWriter();
        JAXBContext context = JAXBContext.newInstance(Railcars.class);
        Marshaller m = context.createMarshaller();
        m.marshal(inRcars, writer);
        final String retValue = writer.toString();
        return retValue;
    }
/**
 *  in order to spot a railcar at least one of the following is needed:
 *  1. inLongitude and inLatitude
 *  2. meterMark
 *  3. TransferPoint
 * @param inLatitude latitude
 * @param inLongitude longitude
 * @param inMeterMark meterMark
 * @param inTransferPoint TP
 * @return if required data is missing.
 */
    private boolean isMissingDataToSpotRailcar(@Nullable final Double inLatitude, @Nullable final Double inLongitude,
                                               @Nullable final Double inMeterMark, @Nullable final String inTransferPoint) {
        int nullCount = 0;
        if (inLatitude == null || inLongitude == null) {
            nullCount++;
        }
        if (inMeterMark == null) {
            nullCount++;
        }
        if (inTransferPoint == null) {
            nullCount++;
        }
        if (nullCount < 3) {
            return false;
        }
        return true;
    }

    /**
     * Validate parameters here..
     * if validation fails: result[0] = error and result[1] = getXmlErrorContent
     * if validation passes: result[0] and result[1] = success
     * @param paramType
     *
     */
    private String[] validateParameters(@NotNull final String paramType) {
        final int statusIdx = 0, errorContentIdx = 1;
        String[] result = new String[2];

        final String parameter = _inParameters.get(paramType);
        final String errorCode;
        final String errorMsge;

        if (paramType.equals(VISIT_ID_PARAM)) {
            errorCode = _casMessageHelper.MISSING_VISIT_ID_CODE;
            errorMsge = _casMessageHelper.MISSING_VISIT_ID_MESSAGE;
        } else if (paramType.equals(VISIT_TYPE_PARAM)) {
            errorCode = _casMessageHelper.MISSING_VISIT_TYPE_CODE;
            errorMsge = _casMessageHelper.MISSING_VISIT_TYPE_MESSAGE;
        } else if(paramType.equals(REQUEST_TYPE_PARAM)) {
            errorCode = _casMessageHelper.MISSING_REQUEST_TYPE_CODE;
            errorMsge = _casMessageHelper.MISSING_REQUEST_TYPE_MESSAGE;
        } else {//If more parameters needs validation...
            errorCode = errorMsge = "";
        }

        if (StringUtils.isBlank(parameter)) {
            registerError(errorCode + " : " + errorMsge);
            result[statusIdx] = "error";
            result[errorContentIdx] = getXmlErrorContent(errorCode, errorMsge);
        } else {
            result[statusIdx] = "success";
            result[errorContentIdx] = "success";
        }
        return result;
    }
/**
 * Creates container list xml content
 * @param inAdditionalInfo additional info map sent as part of xml payload
 * @param inSendOnBoard true, if on board units sent to be the part of xml
 * @param inCraneIds crane ids for which the work lists need to be included as part of xml
 * @param inCarrierVisitDetails carrier visit details
 * @param inWarningMap warnings if any
 * @return xml content
 */
    private String createContainerListXmlContent(Map<String, String> inAdditionalInfo, Boolean inSendOnBoard,
                                                 String inCraneIds, VisitDetails inCarrierVisitDetails, Map<String, String> inWarningMap) {

        CarrierVisit carrierVisit = inCarrierVisitDetails.getCvdCv();
        List<PredicateIntf> unitOnBoardPredicateList = new ArrayList<PredicateIntf>();
        if (LocTypeEnum.VESSEL.equals(carrierVisit.getCvCarrierMode())) {
            unitOnBoardPredicateList.add(PredicateFactory.eq(UnitField.UFV_POS_LOC_TYPE, LocTypeEnum.VESSEL));
            unitOnBoardPredicateList.add(PredicateFactory.eq(UnitField.UFV_POS_LOC_GKEY, carrierVisit.getCvGkey()));
        } else if (LocTypeEnum.TRAIN.equals(carrierVisit.getCvCarrierMode())) {
            unitOnBoardPredicateList.add(PredicateFactory.disjunction()
                    .add(PredicateFactory.conjunction()
            /*  defines a unit on its inbound train */
                    .add(TransitStateQueryUtil.ADVISE_OR_INBOUND_UFV_PREDICATE)
                    .add(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_CV, carrierVisit.getCvGkey())))
                    .add(PredicateFactory.conjunction()
            /*  defines a unit on its outbound train */
                    .add(TransitStateQueryUtil.LOADED_OR_DEPARTED_UFV_PREDICATE)
                    .add(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_CV, carrierVisit.getCvGkey()))));
        }

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
            if (inWarningMap != null && !inWarningMap.isEmpty()) {
                warnings() {
                    inWarningMap.keySet().each {
                        warning(code: it, message: inWarningMap.get(it));
                    }
                }
            }
            if (inSendOnBoard) {
                getMkp().yieldUnescaped("\n" + _casHelper.createOnBoardUnitsXml(unitOnBoardPredicateList) + "\n");
            }
            if (!StringUtils.isBlank(inCraneIds)) {
                boolean allCranes = "ALL".equalsIgnoreCase(inCraneIds);
                String craneIdsForQuery = allCranes ? null : inCraneIds;
                getMkp().yieldUnescaped("\n" + _casHelper.createCraneWorkListXmlContent(craneIdsForQuery, inCarrierVisitDetails, false) + "\n");
            }
        }
        String out = writer.toString();
        return out;
    }

/**
 * Creates payload xml to be returned in response to the request from CAS
 * @param inAdditionalInfo
 * @param inXml
 * @return
 */
    private String getXmlContent(Map<String, String> inAdditionalInfo, String inXml) {
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
            getMkp().yieldUnescaped(inXml);
        }
        String out = writer.toString();
        return out;
    }
/**
 * returns xmlContent without the input parameters.
 * @param inAdditionalInfo
 * @param inXml
 * @return
 */
    private String getXmlContentWithoutInputParams(Map<String, String> inAdditionalInfo, String inXml) {
        def writer = new StringWriter();
        def xml = new MarkupBuilder(writer);
        xml.payload() {
            if (inAdditionalInfo != null && !inAdditionalInfo.isEmpty()) {
                "additional-info"() {
                    inAdditionalInfo.keySet().each {
                        field(id: it, value: inAdditionalInfo.get(it));
                    }
                }
            }
            getMkp().yieldUnescaped(inXml);
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
    /**
     * Creates xml to be returned in case of errors
     * @param inErrorCode error code
     * @param inErrorMessage error message
     * @return
     */
    private String getXmlErrorContent(String inErrorCode, String inErrorMessage) {
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
            error(code: inErrorCode, message: inErrorMessage) {
            }
        }
        String out = writer.toString();
        return out;
    }

    @Nullable
    public Integer getInteger(final String inIntStr) {
        if (isInteger(inIntStr)) {
            return (new Integer(inIntStr));
        }
        return null;
    }

    @Nullable
    public Double getDouble(final String inDoubleStr) {
        if (isDouble(inDoubleStr)) {
            return (new Double(inDoubleStr));
        }
        return null;
    }

    public static boolean isInteger(String inIntStr) {
        try {
            Integer.parseInt(inIntStr);
        } catch (NumberFormatException e) {
            return false;
        } catch (NullPointerException e) {
            return false;
        }
        return true;
    }

    public static boolean isDouble(String str) {
        try {
            double d = Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
            return false;
        } catch (NullPointerException e) {
            return false;
        }
        return true;
    }

    private static String prettyFormat(String input) {
        input = input.replaceAll("\\<\\?xml(.+?)\\?\\>", "").trim();
        try {
            Source xmlInput = new StreamSource(new StringReader(input));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(xmlInput, xmlOutput);
            String returnValue = xmlOutput.getWriter().
                    toString().replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?><", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<");
            return returnValue;
        } catch (Exception e) {
            System.out.println("Error reformatting xml-String: " + e.toString());
            return input; //
        }
    }

    private void logMsg(Object inMsg) {
        LOGGER.debug(inMsg);
    }

}
