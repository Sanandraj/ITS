/*
 * Copyright (c) 2021 Navis LLC. All Rights Reserved.
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
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.model.Yard
import com.navis.argo.business.reference.*
import com.navis.argo.business.snx.IPropertyResolver
import com.navis.argo.business.xps.model.Che
import com.navis.argo.business.xps.model.WorkAssignment
import com.navis.control.business.api.IControlFinder
import com.navis.control.business.api.IControlManager
import com.navis.control.eci.CommonEciPositionAdapter
import com.navis.control.eci.api.*
import com.navis.crane.CraneErrorPropertyKeys
import com.navis.external.argo.AbstractGroovyWSCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.Entity
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.context.PortalApplicationContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.util.BizViolation
import com.navis.framework.util.CarinaUtils
import com.navis.framework.util.LogUtils
import com.navis.framework.util.message.MessageCollector
import com.navis.inventory.InventoryField
import com.navis.inventory.InventoryPropertyKeys
import com.navis.inventory.business.api.*
import com.navis.inventory.business.atoms.*
import com.navis.inventory.business.imdg.ObservedPlacard
import com.navis.inventory.business.imdg.Placard
import com.navis.inventory.business.moves.WorkInstruction
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.rail.business.entity.RailcarPlatformUfvUpdater
import com.navis.services.business.rules.EventType
import com.navis.yard.business.model.BerthMarker
import com.navis.yard.business.model.YardBinModel
import groovy.xml.MarkupBuilder
import junit.framework.Assert
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.w3c.dom.Document
import org.w3c.dom.NamedNodeMap
import org.xml.sax.InputSource

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

import static com.navis.external.control.TransferAllowedValidatorPropertyMappings.OcrEvent
import static com.navis.external.control.TransferAllowedValidatorPropertyMappings.OcrEvent.UNIT_CAPTURE_IDENTIFY
import static com.navis.external.control.TransferAllowedValidatorPropertyMappings.OcrEvent.UNIT_CAPTURE_IMAGE

/**
 * This code extension is handler for inbound messages of all types from CAS system.
 * It handles the following messages:
 * 1. Unit Capture
 * 2. Unit Position Update
 *
 * @author <a href="mailto:arvinder.brar@navis.com">Arvinder Brar</a>, 9/13/12
 *
 * Modified by <a href="mailto:sramsamy@weservetech.com">Ramasamy S</a>, 13/May/2022
 * Adding Loggers
 *
 */
class DefaultN4InboundCasMessageHandler_NotInUse extends AbstractGroovyWSCodeExtension {

  protected final String REQUEST_TYPE_PARAM = "requestType"
  protected final String VISIT_ID_PARAM = "visitId"
  protected final String VISIT_TYPE_PARAM = "visitType"
  protected final String VISIT_TYPE_VESSEL = "VESSEL"
  protected final String VISIT_TYPE_TRAIN = "TRAIN"
  protected final String VISIT_TYPE_RAIL = "RAIL"
  protected final String SEND_READY_TO_TRANSFER_MESSAGE_PARAM = "sendReadyToTransferMessage"
  protected final String UNITS_XML_PARAM = "unitXml"
  protected final String UNIT_CAPTURE_MESSAGE = "unitCaptureMessage"
  protected final String ACTION_PARAM = "action"
  protected final String UNIT_CAPTURE_IMAGE_TYPE = "Image"
  protected final String UNIT_CAPTURE_IDENTIFY_TYPE = "Identify"
  protected final String UNIT_CAPTURE_CREATE_TYPE = "Create"
  protected final String UNIT_CAPTURE_UPDATE_TYPE = "Update"
  protected final String UNIT_CAPTURE_CANNOT_IDENTIFY = "CannotIdentify"
  protected final String UFV_RAIL_CONE_STATUS_UPDATE_FOR_UNITS = "railConeStatusUpdate";
  protected final String UFV_RAIL_CONE_STATUS_UPDATE_FOR_PLATFORM = "platformRailConeStatusUpdate";
  public final String CONTAINER_CANNOT_BE_IDENTIFIED_EVENT_MESSAGE = "Container could not be identified"
  public final String UNIT_ID = "unitId"
  public final String CRANE_ID_PARAM = "craneId"
  //specific fields 3.0 and later releases
  protected final String UNIT_POSITION_UPDATE_LIFT = "Lift"
  protected final String UNIT_POSITION_UPDATE_SET = "Set"
  protected final String UNIT_POSITION_UPDATE = "unitPositionUpdateMessage"
  protected final String READY_TO_TRANSFER_MESSAGE = "readyToTransferMessage"
  private final String READY_TO_TRANSFER = "readyToTransfer"

  private final String CRANE_TECHNICAL_STATUS_UPDATE = "craneTechnicalStatusUpdate";
  private final String TECHNICAL_STATUS = "technicalStatus"
  private final String TECH_STATUS_GREEN = "GREEN"
  private final String TECH_STATUS_YELLOW = "YELLOW"
  private final String TECH_STATUS_ORANGE = "ORANGE";
  private final String TECH_STATUS_RED = "RED"

  private final String MAIN_TROLLEY_LOC_TYPE = "S"
  private final String PORTAL_TROLLEY_LOC_TYPE = "O"
  private final String CRANE_POSITION_UPDATE = "cranePositionUpdate";

  // parameter fields
  protected String _visitId
  protected String _visitType
  protected String _craneId
  protected String _unitsXml
  protected CasInUnit[] _casInUnits
  protected int _unitCount
  protected String _action
  protected boolean _sendReadyToTransferMessage = true

  protected enum Status {
    SUCCESS, ERROR, WARNING
  }
  //error code and message which are populated for non-unit specific errors, unit specific arrors are handled at unit level
  private String _errorCode = null
  private String _errorMessage = null

  protected CarrierVisit _carrierVisit
  //CasHelper library name
  public final String CAS_HELPER = "CasHelper"
  //CasHelper library class
  def _casHelper = null

  //CasMessageHelper library name
  public final String CAS_MESSAGE_HELPER = "CasMessageHelper"
  //CasHelper library class

  def _casMessageHelper = null

  //for global access of additionalInfoMap
  Map<String, String> _additionalInfoMap = null;

  //Datasource for equipment updates for this code extension
  private final DataSourceEnum _dataSourceEnum = DataSourceEnum.USER_LCL
  private final String LOAD_WARNING = " is not planned for loading on ";
  private final String DISCH_WARNING = " is not planned for discharge from ";

  /**
   * Main entry point method.
   * @param inParameters parameters sent as part of groovy web service
   * @return the string response to the groovy webservice call
   */
  public String execute(Map inParameters) {

    //Log the request content 20141119
    logMsg("\nRequest: " + getParametersAsString())
    Map<String, String> additionalInfoMap = new HashMap<String, String>()
    _additionalInfoMap = additionalInfoMap;
    initCasMessageHelper()

// Validate that the logged in scope is yard
    Yard yard = ContextHelper.getThreadYard()
    if (yard == null) {
      setErrorStrings(_casMessageHelper.LOGGED_IN_SCOPE_IS_NOT_YARD_CODE,
          _casMessageHelper.LOGGED_IN_SCOPE_IS_NOT_YARD_MESSAGE)
      return getXmlErrorContent()
    }

    initCasHelper()

    final String messageType = _parameterMap.get(REQUEST_TYPE_PARAM)

    _parameterMapForResponse = new HashMap(_parameterMap);
    if (isUnitMessage(messageType)) {
      Node unitsNode = getUnitsNode()

      if (unitsNode == null) {
        return getXmlErrorContent()
      }

      loadParametersForUnitMessages(unitsNode)
      //Validate the required parameters for the message
      if (!parametersAreValidForUnitMessages(messageType)) {
        return getXmlErrorContent()
      }
    }

    String responseXml
    if (UFV_RAIL_CONE_STATUS_UPDATE_FOR_UNITS.equals(messageType)) {
      Node unitsNode = getUnitsNode();
      if (unitsNode == null) {
        return getXmlErrorContent()
      }
      loadParametersForUnitMessages(unitsNode);
      processUfvRailConeStatusUpdate(unitsNode);
      responseXml = createResponseXmlForUnitMessages(additionalInfoMap);
      return responseXml;
    } else if (UFV_RAIL_CONE_STATUS_UPDATE_FOR_PLATFORM.equals(messageType)) {
      _visitType = _parameterMap.get(VISIT_TYPE_PARAM);
      if (!VISIT_TYPE_RAIL.equals(_visitType)) {
        setErrorStrings(_casMessageHelper.INVALID_VISIT_TYPE_CODE, _casMessageHelper.INVALID_VISIT_TYPE_MESSAGE);
        return getXmlErrorContent();
      }
      final String railcars = _parameterMap.get("railcars");
      ArrayList<String> updateResult = updateRailConeStatusByPlatform(railcars);
      responseXml = createResponseXMLForUfvConeStatusUpdate(updateResult);
      logMsg("response: " + responseXml);
    } else if (UNIT_CAPTURE_MESSAGE.equals(messageType)) {
      // Validate Capture type
      if (!isValidCaptureAction()) {
        setErrorStrings(_casMessageHelper.INVALID_CAPTURE_TYPE_CODE,
            _casMessageHelper.INVALID_CAPTURE_TYPE_MESSAGE)
        return getXmlErrorContent()
      }
      processUnitCapture()
      if (UNIT_CAPTURE_IMAGE_TYPE.equals(_action) || UNIT_CAPTURE_IDENTIFY_TYPE.equals(_action)) {
        if (_sendReadyToTransferMessage) {
          Boolean readyToTransfer = areUnitsReadyToTransfer(_action)
          additionalInfoMap.put(READY_TO_TRANSFER, _casHelper.translateBooleanToYN(readyToTransfer))
          sendReadyToTransferMessage(readyToTransfer)
        } else {
          logMsg("Skipping sending ready to transfer message as the request has explicitly prohibited it")
        }
      }
      responseXml = createResponseXmlForUnitMessages(additionalInfoMap)
    } else if (UNIT_POSITION_UPDATE.equals(messageType)) {
      // Validate position type
      boolean isValidAction = UNIT_POSITION_UPDATE_LIFT.equals(_action) || UNIT_POSITION_UPDATE_SET.equals(_action)
      if (!isValidAction) {
        setErrorStrings(_casMessageHelper.INVALID_UNIT_POSITION_TYPE_CODE,
            _casMessageHelper.INVALID_UNIT_POSITION_TYPE_MESSAGE)
        return getXmlErrorContent()
      }
      processUnitPositionUpdate()
      responseXml = createResponseXmlForUnitMessages(additionalInfoMap)
    } else if (CRANE_TECHNICAL_STATUS_UPDATE.equals(messageType)) {
      /*20141119*/
      final Status updatePerformed = processCraneTechnicalStatusUpdate(additionalInfoMap)

      if (Status.SUCCESS == updatePerformed) {
        responseXml = createResponseXml(additionalInfoMap)
      } else if (Status.ERROR == updatePerformed) {
        return getXmlErrorContent()
      } else {
      }
    } else if (CRANE_POSITION_UPDATE.equals(messageType)) {
      final Status status = processCranePositionUpdate();

      if (Status.SUCCESS == status) {
        responseXml = createResponseXml(additionalInfoMap);
        return responseXml
      } else {
        return getXmlErrorContent();
      }
    } else {
      setErrorStrings(_casMessageHelper.INVALID_MESSAGE_REQUEST_TYPE_CODE,
          messageType + " - " + _casMessageHelper.INVALID_MESSAGE_REQUEST_TYPE_MESSAGE);
      return getXmlErrorContent();
    }

    //An example of adding additional information
    /* additionalInfoMap.put("aField", "aFieldValue") */
    logMsg("\nRequest: " + getParametersAsString() + "\nResponse : " + responseXml)
    return responseXml;
  }

  /*
* Process crane's position update
* 1. Bollard offset validation
* 2. Bollard and cranes exist validation
* 3. Update crane's possition
*/

  protected Status processCranePositionUpdate() {
    final String bollardName = _parameterMap.get("bollardName");
    final String bollardOffset = _parameterMap.get("bollardOffsetCm");
    final String craneId = _parameterMap.get("craneId");
    final int intBollardOffsetCm;
    final Integer integerBollardOffsetCm;
    Status retVal = Status.SUCCESS;
    final YardBinModel model;
    final BerthMarker bollard;

    try {//bollard offset validation
      intBollardOffsetCm = Integer.parseInt(bollardOffset);
      integerBollardOffsetCm = new Integer(intBollardOffsetCm);
    } catch (NumberFormatException nfe) {
      setErrorStrings(_casMessageHelper.INVALID_OFFSET_VALUE_CODE,
          _casMessageHelper.INVALID_OFFSET_VALUE_MESSAGE +
              "  Bollard Offset: " + bollardOffset + ", " + nfe.toString());
      retVal = Status.ERROR;
      return retVal;
    }

    try {//yardBinModel validation
      model = YardBinModel.downcast(ContextHelper.getThreadYard().getYrdBinModel());
    } catch (BizViolation bv) {
      logMsg(bv.toString())
      setErrorStrings(_casMessageHelper.BIZ_ERROR_CODE,
          _casMessageHelper.BIZ_ERROR_MESSAGE + "\n" + bv.toString());
      retVal = Status.ERROR;
      return retVal;
    }

    bollard = BerthMarker.findBollardInYardModelFromName(model, bollardName);
    if (bollard == null) {//bollard validation
      setErrorStrings(_casMessageHelper.INVALID_BOLLARD_NAME_CODE,
          _casMessageHelper.INVALID_BOLLARD_NAME_MESSAGE + " bollard name: " + bollardName);
      retVal = Status.ERROR;
      return retVal;
    }

    Assert.assertTrue(retVal == Status.SUCCESS);
    try {//crane position is updated here
      Che crane = _casHelper.validateCraneId(craneId);
      Yard yard = ContextHelper.getThreadYard();
      LocPosition locPosition = _casHelper.getLocPositionForQcPositionUpdate(craneId,
          bollardName, integerBollardOffsetCm, yard, crane);

      crane.setCheLastKnownLocPos(locPosition);
      HibernateApi.getInstance().saveOrUpdate(crane);
    } catch (BizViolation bv) {
      setErrorStrings(_casMessageHelper.INVALID_CRANE_ID_CODE,
          _casMessageHelper.INVALID_CRANE_ID_MESSAGE + " [craneId = " + craneId + "]. EXCEPTION: " + bv.toString());
      retVal = Status.ERROR;
      return retVal;
    }
    return retVal;
  }

  private Node getUnitsNode() {
    Node unitsNode = null

    //Validate that the unit xml is present and valid
    _unitsXml = _parameterMap.get(UNITS_XML_PARAM)
    if (StringUtils.isBlank(_unitsXml)) {
      setErrorStrings(_casMessageHelper.MISSING_UNIT_XML_CODE,
          _casMessageHelper.MISSING_UNIT_XML_MESSAGE)
      return null
    }

    // Parse the XML message
    try {
      unitsNode = new XmlParser().parseText(_unitsXml)
    } catch (Exception ex) {
      setErrorStrings(_casMessageHelper.INVALID_UNIT_XML_CODE,
          _casMessageHelper.INVALID_UNIT_XML_MESSAGE)
      ex.printStackTrace()
      return null
    }

    def units = unitsNode.'unit'
    _unitCount = units.size()
    if (!"units".equals(unitsNode.name()) || _unitCount == 0) {
      setErrorStrings(_casMessageHelper.INVALID_UNIT_XML_CODE,
          _casMessageHelper.INVALID_UNIT_XML_MESSAGE)
      return null
    }

    return unitsNode
  }

  private boolean isUnitMessage(final String inMessageType) {
    return (UNIT_CAPTURE_MESSAGE.equals(inMessageType)
        || UNIT_POSITION_UPDATE.equals(inMessageType))
  }

  private setErrorStrings(final String inErrorCode, final String inErrorMessage) {
    _errorCode = inErrorCode
    _errorMessage = inErrorMessage
  }

  /**
   * Loads the parameters to fields, so that they are available to all methods
   */
  protected void  loadParametersForUnitMessages(Node inUnitsNode) {
    _casInUnits = new CasInUnit[_unitCount]
    _visitId = _parameterMap.get(VISIT_ID_PARAM)
    _visitType = _parameterMap.get(VISIT_TYPE_PARAM)
    _craneId = _parameterMap.get(CRANE_ID_PARAM)
    _action = _parameterMap.get(ACTION_PARAM)

    String sendReadyToTransferMessage = _parameterMap.get(SEND_READY_TO_TRANSFER_MESSAGE_PARAM)
    if (!StringUtils.isBlank(sendReadyToTransferMessage) && "N".equalsIgnoreCase(sendReadyToTransferMessage)) {
      _sendReadyToTransferMessage = false
    }
    def units = inUnitsNode.'unit'
    int i = 0
    units.each { Node unitNode ->
      _casInUnits[i] = new CasInUnit(unitNode)
      initializeCasUnit(_casInUnits[i])
      i++
    }

    adjustCarrierVisit()
  }
  /**
   * Processes the unit capture message after preliminary validations have been done
   */
  protected void processUnitCapture() {
    for (CasInUnit casInUnit : _casInUnits) {
      if (!casInUnit.hasError()) {
        validateUnitForCapture(casInUnit)
      }
      if (!casInUnit.hasError()) {
        handleUnitCaptureAndUpdate(casInUnit)
      }
      if (!casInUnit.hasError() && !(Status.WARNING == casInUnit.getReturnStatus())) {
        casInUnit.setReturnStatus(Status.SUCCESS)
        casInUnit.setReturnCode(_casMessageHelper.UNIT_CAPTURE_HANDLED_SUCCESSFULLY_CODE)
        casInUnit.setReturnMessage(_casMessageHelper.UNIT_CAPTURE_HANDLED_SUCCESSFULLY_MESSAGE + " [Action=" + _action + "]")
      }
    }
  }

  protected ArrayList<String> updateRailConeStatusByPlatform(final String railcarsXML) {
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document doc = dBuilder.parse(new InputSource(new StringReader(railcarsXML)));
    org.w3c.dom.NodeList railcarNodeList = doc.getElementsByTagName("railcar");
    ArrayList<String> results = new ArrayList<>();
    /*
    * for each railcar node in node-list do:
    * 1. extract variables railcarId, updateType, sequence/label.
    * 2. build a map of key(seq/label) and values(cone-status).
    * 3. build a RailcarPlatformUfvUpdater Object from collected above data.
    * 4. update cone-status and move on to the next railcar.
    */
    for (int idx = 0; idx < railcarNodeList.getLength(); idx++) {

      final org.w3c.dom.Node railcarNode = railcarNodeList.item(idx);
      NamedNodeMap namedNodeMap = railcarNode.getAttributes();

      //get railcarId, and Maps required for RailcarPlatformUfvUpdater.
      final String railcarId = getTextFromNodeNodeMap(namedNodeMap, "id");

      final org.w3c.dom.NodeList platformNodeList = railcarNode.getChildNodes();
      final Map labelConStatusMap = getLabelConeStatusMap(platformNodeList);
      final Map sequenceConeStatusMap = getSequenceConeStatusMap(platformNodeList);

      RailcarPlatformUfvUpdater updater = new RailcarPlatformUfvUpdater(railcarId, labelConStatusMap, sequenceConeStatusMap);
      ArrayList<String> result = updater.updateUfvRailConeStatus();
      if (result != null && !result.isEmpty()) {
        results.addAll(result);
      }
    }
    return results;
  }

  /**
   * get a map where key=plfLabel and value=cone-status
   * @param inNodeList
   * @return map
   */
  HashMap<String, String> getLabelConeStatusMap(@NotNull final org.w3c.dom.NodeList inNodeList) {
    HashMap<String, String> retVal = new HashMap<String, String>();
    for (int idx = 0; idx < inNodeList.getLength(); idx++) {
      final NamedNodeMap platformAttributes = inNodeList.item(idx).getAttributes();
      final String label = getTextFromNodeNodeMap(platformAttributes, "label");
      final String coneStatus = getTextFromNodeNodeMap(platformAttributes, "cone-status");
      if (StringUtils.isNotEmpty(label) && StringUtils.isNotEmpty(coneStatus)) {
        RailConeStatusEnum rConestatusEnum = RailConeStatusEnum.getEnum(coneStatus);
        if (rConestatusEnum != null) {
          retVal.put(label, coneStatus);
        } else {
          final String alternateSpelling = getValidConeStatusFromStr(coneStatus);
          retVal.put(label, alternateSpelling);
        }
      }
    }
    return retVal;
  }

  /*
   * get a map where key=plfSequence and value=cone-status
   * @param inNodeList
   * @return map
   */

  HashMap<String, String> getSequenceConeStatusMap(org.w3c.dom.NodeList inNodeList) {
    HashMap<String, String> retVal = new HashMap<String, String>();
    for (int idx = 0; idx < inNodeList.getLength(); idx++) {
      final NamedNodeMap platformAttributes = inNodeList.item(idx).getAttributes();
      final String label = getTextFromNodeNodeMap(platformAttributes, "label");
      final String sequence = getTextFromNodeNodeMap(platformAttributes, "sequence");
      final String coneStatus = getTextFromNodeNodeMap(platformAttributes, "cone-status");
      if (StringUtils.isEmpty(label)) {
        if (StringUtils.isNotEmpty(sequence) && StringUtils.isNotEmpty(coneStatus)) {
          RailConeStatusEnum rConestatusEnum = RailConeStatusEnum.getEnum(coneStatus);
          if (rConestatusEnum != null) {
            retVal.put(sequence, coneStatus);
          } else {
            final String alternateSpelling = getValidConeStatusFromStr(coneStatus);
            retVal.put(sequence, alternateSpelling);
          }
        }
      }
    }
    return retVal;
  }
  /**
   * the followings are valid ufv rail-cone-status: UNKNOWN, NO_CONE, UNLOCKED_CONE, LOCKED_CONE
   * additional Requirement was added to support alternate spelling
   * @param inConeStatus get coneStatus
   * @return
   */
  private String getValidConeStatusFromStr(final String inConeStatus) {
    if ("Unknown".equalsIgnoreCase(inConeStatus)) {
      return RailConeStatusEnum.UNKNOWN.getName();
    }
    if ("Locked".equalsIgnoreCase(inConeStatus)) {
      return RailConeStatusEnum.LOCKED_CONES.getName();
    }
    if ("Unlocked".equalsIgnoreCase(inConeStatus)) {
      return RailConeStatusEnum.UNLOCKED_CONES.getName();
    }
    if ("No Cones".equalsIgnoreCase(inConeStatus)) {
      return RailConeStatusEnum.NO_CONES.getName();
    }
    return inConeStatus;
  }

  @Nullable
  private String getTextFromNodeNodeMap(NamedNodeMap inNamedNodeMap, final String attributeName) {
    if (inNamedNodeMap == null || attributeName == null) {
      return null;
    }
    final org.w3c.dom.Node node = inNamedNodeMap.getNamedItem(attributeName);
    if (node == null) {
      return null;
    }
    String retValue = node.getTextContent();
    return retValue;
  }

  /**
   * Udates unitFacilityVisit's railConeStatus
   */
  protected void processUfvRailConeStatusUpdate(Node inUnitsNode) {
    for (CasInUnit casInUnit : _casInUnits) {
      if (!casInUnit.hasError()) {
        validateUnitActive(casInUnit);
      }
      if (!casInUnit.hasError()) {
        handleUfvRailConeStatusUpdate(casInUnit);
      }
      if (!casInUnit.hasError() && !(Status.WARNING == casInUnit.getReturnStatus())) {
        casInUnit.setReturnStatus(Status.SUCCESS)
        casInUnit.setReturnCode(_casMessageHelper.UNIT_RAIL_CONE_STATUS_UPDATE_REQUEST_COMPLETED_CODE)
        casInUnit.setReturnMessage(_casMessageHelper.UNIT_RAIL_CONE_STATUS_UPDATE_REQUEST_COMPLETED_MESSAGE)
      }
    }
  }

/**
 * Handle Ufv's RailCone Status update
 */
  protected void handleUfvRailConeStatusUpdate(final CasInUnit inCasInUnit) {

    final PersistenceTemplate persistenceTemplate = new PersistenceTemplate(getUserContext())
    final MessageCollector messageCollector = persistenceTemplate.invoke(new CarinaPersistenceCallback() {
      @Override
      protected void doInTransaction() {
        Long ufvGkey = inCasInUnit.getUnitFacilityVisit().getUfvGkey();
        UnitFacilityVisit ufv = UnitFacilityVisit.hydrate(ufvGkey);
        Node unitNode = inCasInUnit.getUnitNode();
        if (unitNode.attributes().containsKey("rail-cone-status")) {
          String coneStatusStr = getAttributeValue("rail-cone-status", unitNode.@"rail-cone-status", String.class);
          RailConeStatusEnum coneStatusEnum = RailConeStatusEnum.getEnum(coneStatusStr);
          if (coneStatusEnum == null) {
            setErrorStrings(_casMessageHelper.INVALID_UNIT_RAIL_CONE_STATUS_CODE, _casMessageHelper.INVALID_UNIT_RAIL_CONE_STATUS_MESSAGE);
            inCasInUnit.setReturnStatus(Status.ERROR);
            inCasInUnit.setReturnMessage(getXmlErrorContent());
            return;
          }
          ufv.updateRailConeStatus(coneStatusEnum);
        }
      }
    })
    if (messageCollector.hasError()) {
      logMsg(_casMessageHelper.BIZ_ERROR_CODE + ":" + messageCollector.toString())
      inCasInUnit.setReturnStatus(Status.ERROR)
      inCasInUnit.setReturnCode(_casMessageHelper.BIZ_ERROR_CODE)
      inCasInUnit.setReturnMessage(messageCollector.toString())
      return
    }
  }

  /**
   * Processes the unit position update message after preliminary validations have been done
   */

  protected void processUnitPositionUpdate() {
    for (CasInUnit casInUnit : _casInUnits) {
      if (!casInUnit.hasError()) {
        validateUnitForPositionUpdate(casInUnit)
      }
      if (!casInUnit.hasError()) {
        handleUnitPositionUpdate(casInUnit)
      }
      if (!casInUnit.hasError() && !(Status.WARNING == casInUnit.getReturnStatus())) {
        casInUnit.setReturnStatus(Status.SUCCESS)
        casInUnit.setReturnCode(_casMessageHelper.UNIT_POSITION_UPDATED_SUCCESSFULLY_CODE)
        casInUnit.setReturnMessage(_casMessageHelper.UNIT_POSITION_UPDATED_SUCCESSFULLY_MESSAGE + " [Action=" + _action + "]")
      }
    }
  }

  protected Status processCraneTechnicalStatusUpdate(Map<String, String> inAdditionalInfo) {
    /*20141119*/
    final String craneId = _parameterMap.get(CRANE_ID_PARAM);
    final Che crane

    try {
      crane = _casHelper.validateCraneId(craneId)
    } catch (BizViolation ex) {
      setErrorStrings(_casMessageHelper.INVALID_CRANE_ID_CODE,
          _casMessageHelper.INVALID_CRANE_ID_MESSAGE + "[craneId = " + craneId + "]")
      return Status.ERROR
    }

    final String techStatus = _parameterMap.get(TECHNICAL_STATUS);

    if (techStatus == TECH_STATUS_GREEN || techStatus == TECH_STATUS_YELLOW || techStatus == TECH_STATUS_RED || techStatus == TECH_STATUS_ORANGE) {
      crane.setCheAutoCheTechnicalStatus(techStatus);
      HibernateApi.getInstance().saveOrUpdate(crane);
      return Status.SUCCESS;
    } else {
      setErrorStrings(_casMessageHelper.INVALID_TECHNICAL_STATUS_CODE, _casMessageHelper.INVALID_TECHNICAL_STATUS_MESSAGE)
      return Status.ERROR;
    }
  }

  protected void validateTbdLoadStatus(CasInUnit inCasInUnit) {
    if (inCasInUnit.getUnitFacilityVisit().getFinalPlannedPosition().equals(null)) {
      HcTbdUnitManager hcTbdMngr = (HcTbdUnitManager) Roastery.getBean(HcTbdUnitManager.BEAN_ID)
      hcTbdMngr.validateTbdUnitForLoad(inCasInUnit.getUnitFacilityVisit(), _carrierVisit)
    }
  }

  /**
   * Validate the required parameters for the message, for the unit's required parameters message is added to CasInUnit
   * @return true if all the required parameters for message as a whole are valid, for unit attributes it sets hasError of
   * unit to true and adds a return message for that unit, so that the other valid units are processed.
   */
  protected boolean parametersAreValidForUnitMessages(@NotNull final String inMessageType) {
    boolean isValid = true
    //validate request type parameter
    if (StringUtils.isBlank(inMessageType)) {
      setErrorStrings(_casMessageHelper.MISSING_REQUEST_TYPE_CODE,
          _casMessageHelper.MISSING_REQUEST_TYPE_MESSAGE)
      return false
    }
    //Validate 'visitType' and 'visitId' parameters
    if (StringUtils.isBlank(_visitId)) {
      setErrorStrings(_casMessageHelper.MISSING_VISIT_ID_CODE,
          _casMessageHelper.MISSING_VISIT_ID_MESSAGE)
      return false
    }
    if (StringUtils.isBlank(_visitType)) {
      setErrorStrings(_casMessageHelper.MISSING_VISIT_TYPE_CODE,
          _casMessageHelper.MISSING_VISIT_TYPE_MESSAGE)
      return false
    } else if (!(_visitType.equals(VISIT_TYPE_VESSEL) || _visitType.equals(VISIT_TYPE_TRAIN))) {
      setErrorStrings(_casMessageHelper.INVALID_VISIT_TYPE_CODE,
          _casMessageHelper.INVALID_VISIT_TYPE_MESSAGE)
      return false
    }
    //Validate the carrier visit (populated earlier in adjustCarrierVisit)
    if (_carrierVisit == null) {
      setErrorStrings(_casMessageHelper.NO_CARRIER_VISIT_FOUND_CODE,
          _casMessageHelper.NO_CARRIER_VISIT_FOUND_MESSAGE + "[visitId=" + _visitId + "]")
      return false
    }
    if (StringUtils.isBlank(_craneId)) {
      setErrorStrings(_casMessageHelper.MISSING_CRANE_ID_CODE,
          _casMessageHelper.MISSING_CRANE_ID_MESSAGE)
      return false
    } else {
      try {
        Che quayCrane = _casHelper.validateCraneId(_craneId)
        if (UNIT_CAPTURE_MESSAGE.equals(inMessageType)) {
          if (isValidCaptureAction()) {
            //Check if OCR data is being accepted null value equated to true value, that is, OCR data ia being accepted
            boolean dataAccepted = quayCrane.getCheIsOcrDataBeingAccepted() == null ? true : quayCrane.getCheIsOcrDataBeingAccepted()
            if (!dataAccepted) {
              setErrorStrings(_casMessageHelper.CRANE_OCR_DATA_NOT_BEING_ACCEPTED,
                  _casMessageHelper.CRANE_OCR_DATA_NOT_BEING_ACCEPTED_MESSAGE + "[craneId=" + _craneId + "]")
              return false
            }
          }
        }
      } catch (Exception ex) {
        setErrorStrings(_casMessageHelper.INVALID_CRANE_ID_CODE,
            _casMessageHelper.INVALID_CRANE_ID_MESSAGE + "[craneId=" + _craneId + "]")
        return false
      }
    }

    //Validate the required attributes of unit nodes
    for (CasInUnit casInUnit : _casInUnits) {
      //Validate 'casReferenceId'
      if (StringUtils.isBlank(casInUnit.getCasUnitReference())) {
        logMsg(_casMessageHelper.MISSING_CAS_UNIT_REFERENCE_CODE + ":" + _casMessageHelper.MISSING_CAS_UNIT_REFERENCE_MESSAGE)
        casInUnit.setReturnStatus(Status.ERROR)
        casInUnit.setReturnCode(_casMessageHelper.MISSING_CAS_UNIT_REFERENCE_CODE)
        casInUnit.setReturnMessage(_casMessageHelper.MISSING_CAS_UNIT_REFERENCE_MESSAGE)
      }
    }
    return isValid
  }

  private boolean isValidCaptureAction() {
    UNIT_CAPTURE_IMAGE_TYPE.equals(_action) || UNIT_CAPTURE_IDENTIFY_TYPE.equals(_action) ||
        UNIT_CAPTURE_CREATE_TYPE.equals(_action) || UNIT_CAPTURE_UPDATE_TYPE.equals(_action) ||
        UNIT_CAPTURE_CANNOT_IDENTIFY.equals(_action)
  }
  /**
   * Validates the attributes of CasInUnit for the unit capture message
   * @param inCasInUnit the CasInUnit to be validated
   */
  protected void validateUnitForCapture(CasInUnit inCasInUnit) {
    //Validate unit id
    if ((UNIT_CAPTURE_IDENTIFY_TYPE.equals(_action) || UNIT_CAPTURE_CREATE_TYPE.equals(_action) || UNIT_CAPTURE_UPDATE_TYPE.equals(_action))
        && StringUtils.isBlank(inCasInUnit.getUnitId())) {
      logMsg(_casMessageHelper.MISSING_UNIT_ID_CODE + ":" + _casMessageHelper.MISSING_UNIT_ID_MESSAGE)
      inCasInUnit.setReturnStatus(Status.ERROR)
      inCasInUnit.setReturnCode(_casMessageHelper.MISSING_UNIT_ID_CODE)
      inCasInUnit.setReturnMessage(_casMessageHelper.MISSING_UNIT_ID_MESSAGE)
      return
    }

    if (!isIsoCodeValid(inCasInUnit)) {
      return;
    }
    //Validate unit
    try {
      if (UNIT_CAPTURE_IMAGE_TYPE.equals(_action) || UNIT_CAPTURE_CREATE_TYPE.equals(_action) || UNIT_CAPTURE_CANNOT_IDENTIFY.equals(_action)) {
        return//nothing to validate
      }

      try {
        validateUnitActive(inCasInUnit)
      } catch (BizViolation inBv) {
        if (UNIT_CAPTURE_IDENTIFY_TYPE.equals(_action)) {
          enqueueCasEciServiceRequest(
              EciEsbConstants.ECISERVICE_TYPE_QCAS_CONTAINER_MISIDENTIFIED,
              inCasInUnit,
              null
          )
        }
        throw inBv
      }
      //Get the unit facility visit - it is set on inCasUnit in validateUnitActive
      UnitFacilityVisit unitFacilityVisit = inCasInUnit.getUnitFacilityVisit()
      //Validate that the in case of "Identify" action, unit is valid for load/discharge
      if (UNIT_CAPTURE_IDENTIFY_TYPE.equals(_action)) {
        //first check the Ufv's Position, there may be a chance that CAS might wrongly send a unit number which is in ASC Block
        LocPosition ufvPosition = unitFacilityVisit.getUfvLastKnownPosition();
        if (ufvPosition != null && ufvPosition.isProtectedYardPosition()) {
          enqueueCasEciServiceRequest(
                  EciEsbConstants.ECISERVICE_TYPE_QCAS_CONTAINER_MISIDENTIFIED,
                  inCasInUnit,
                  null
          )
          logMsg(_casMessageHelper.UNIT_IN_PROTECTED_YARD_POSITION_CODE + ":" + _casMessageHelper.UNIT_IN_PROTECTED_YARD_POSITION_MESSAGE + ":" +
                  ufvPosition.toString())
          inCasInUnit.setReturnStatus(Status.ERROR)
          inCasInUnit.setReturnCode(_casMessageHelper.UNIT_IN_PROTECTED_YARD_POSITION_CODE)
          inCasInUnit.setReturnMessage(_casMessageHelper.UNIT_IN_PROTECTED_YARD_POSITION_MESSAGE + ":" + ufvPosition.toString())
          return
        }

        if (unitFacilityVisit.getUfvCasUnitReference() != null) {
          final RestowTypeEnum ufvRestowTypeEnum = unitFacilityVisit.getUfvRestowType();
          LogUtils.forceLogAtDebug(LOGGER, "Handling " + unitFacilityVisit.toString() + " with restow type of: " + ufvRestowTypeEnum);
          if (RestowTypeEnum.RESTOW.equals(ufvRestowTypeEnum)) {
            LogUtils.forceLogAtDebug(LOGGER, "Allowing re-identification a restow " + unitFacilityVisit.toString());
          } else {
            BizViolation bv = BizViolation.create(CraneErrorPropertyKeys.MANUAL_OPS_UNIT_ALREADY_IDENTIFIED, null, inCasInUnit.getUnitId());
            throw bv;
          }
        }
        boolean isLoad = false
        List<WorkInstruction> currentWIList = unitFacilityVisit.getCurrentWiList()
        //currentWIList = currentWIList.reverse();
        if (currentWIList == null || currentWIList.isEmpty()) {
          logMsg(_casMessageHelper.NOT_IN_LOAD_OR_DSCH_PLAN_CODE + ":" + _casMessageHelper.NOT_IN_LOAD_OR_DSCH_PLAN_MESSAGE)
          inCasInUnit.setReturnStatus(Status.WARNING)
          inCasInUnit.setReturnCode(_casMessageHelper.NOT_IN_LOAD_OR_DSCH_PLAN_CODE)
          inCasInUnit.setReturnMessage(_casMessageHelper.NOT_IN_LOAD_OR_DSCH_PLAN_MESSAGE)

          recordExceptionServiceEventInInnerTransaction(unitFacilityVisit.getUfvGkey(), UnitFacilityVisit.class,
              EventType.resolveIEventType(EventEnum.CHE_ERROR_UNIT_NOT_IN_LOAD_DISCH_LIST),
              _casMessageHelper.NOT_IN_LOAD_OR_DSCH_PLAN_MESSAGE)
          return
        }
        CarrierVisit obCv = unitFacilityVisit.getUfvActualObCv();
        CarrierVisit ibCv = unitFacilityVisit.getUfvActualIbCv();
        WorkInstruction targetWI = null
        //To give priority to Discharge irrespective of type, container, VV details as these are not readily available at
        //        every customer site at time of discharge
        for (WorkInstruction currentWi : currentWIList) {
            if (currentWi.getWiMoveKind().equals(WiMoveKindEnum.VeslDisch)) {
              targetWI = currentWi
              break
            }
        }
        if (targetWI == null) {
          targetWI = currentWIList.get(0)
        }

        if (targetWI.getWiToPosition().isVesselPosition() || targetWI.getWiToPosition().isRailPosition()) {
          isLoad = true
        }
        if (isLoad) {
          if (!obCv.getCvId().equals(_visitId) && !unitFacilityVisit.treatAsFlexLoadEmpty()) {
            String warningMsg = "Identified " + unitFacilityVisit.getUfvUnit().getUnitId() + LOAD_WARNING + _visitId + " but planned to " +
                    obCv.getCvId();
            logMsg(_casMessageHelper.NOT_IN_LOAD_PLAN_CODE + ":" + warningMsg)
            inCasInUnit.setReturnStatus(Status.WARNING)
            inCasInUnit.setReturnCode(_casMessageHelper.NOT_IN_LOAD_PLAN_CODE)
            inCasInUnit.setReturnMessage(warningMsg)

            recordExceptionServiceEventInInnerTransaction(unitFacilityVisit.getUfvGkey(), UnitFacilityVisit.class,
                    EventType.resolveIEventType(EventEnum.CHE_ERROR_UNIT_NOT_IN_LOAD_DISCH_LIST), warningMsg)
          }
        } else {
          ibCv = InventoryControlUtils.getEffectiveInboundCarrierVisitForDischarge(unitFacilityVisit);
          if (ibCv == null || !ibCv.getCvId().equals(_visitId)) {
            logMsg(_casMessageHelper.NOT_IN_DSCH_PLAN_CODE + ":" + _casMessageHelper.NOT_IN_DSCH_PLAN_MESSAGE)
            final String warningMsg = "Identified " + unitFacilityVisit.getUfvUnit().getUnitId() + DISCH_WARNING + _visitId + " but planned from " +
                    ibCv.getCvId();
            inCasInUnit.setReturnStatus(Status.WARNING)
            inCasInUnit.setReturnCode(_casMessageHelper.NOT_IN_DSCH_PLAN_CODE)
            inCasInUnit.setReturnMessage(warningMsg)

            recordExceptionServiceEventInInnerTransaction(unitFacilityVisit.getUfvGkey(), UnitFacilityVisit.class,
                    EventType.resolveIEventType(EventEnum.CHE_ERROR_UNIT_NOT_IN_LOAD_DISCH_LIST),
                    _casMessageHelper.NOT_IN_DSCH_PLAN_MESSAGE)
          }
        }
      } else if (UNIT_CAPTURE_UPDATE_TYPE.equals(_action)) {
        //Verify that unit reference in ufv and unit reference of the incoming message match
        setUfvCasUnitReferenceIfNecessary(inCasInUnit, unitFacilityVisit)
        verifyUnitReferenceAndTransactionReference(inCasInUnit, unitFacilityVisit)
      }
    } catch (Exception ex) {
      logMsg(_casMessageHelper.BIZ_ERROR_CODE + ":" + ex.getLocalizedMessage())
      inCasInUnit.setReturnStatus(Status.ERROR)
      inCasInUnit.setReturnCode(_casMessageHelper.BIZ_ERROR_CODE)
      inCasInUnit.setReturnMessage(ex.getLocalizedMessage())

      Che che = Che.findCheByShortName(_craneId, ContextHelper.getThreadYard())
      UnitFacilityVisit ufv = inCasInUnit.getUnitFacilityVisit()
      Long ufvGkey = null
      Class entityClass = null
      //Check to make sure ufv is not null - it could be null if validateUnitCapture threw exception
      if (ufv != null) {
        ufvGkey = ufv.getUfvGkey()
        entityClass = UnitFacilityVisit.class
      }
      recordExceptionServiceEventInInnerTransaction(ufvGkey, entityClass,
          EventType.resolveIEventType(EventEnum.CHE_ERROR_DURING_UNIT_CAPTURE), ex.getLocalizedMessage())
    }
  }

  /**
   * Validates the attributes of CasInUnit for the unit position update message
   * @param inCasInUnit the CasInUnit to be validated
   */

  protected void validateUnitForPositionUpdate(CasInUnit inCasInUnit) {
    try {
      if (inCasInUnit.getUnitId() != null) {
        validateUnitActive(inCasInUnit)
        UnitFacilityVisit unitFacilityVisit = inCasInUnit.getUnitFacilityVisit()
        //Verify that unit reference in ufv and unit reference of the incoming message match
        setUfvCasUnitReferenceIfNecessary(inCasInUnit, unitFacilityVisit)
        verifyUnitReferenceAndTransactionReference(inCasInUnit, unitFacilityVisit)
      }
    } catch (Exception ex) {
      logMsg(_casMessageHelper.BIZ_ERROR_CODE + ":" + ex.getLocalizedMessage())
      inCasInUnit.setReturnStatus(Status.ERROR)
      inCasInUnit.setReturnCode(_casMessageHelper.BIZ_ERROR_CODE)
      inCasInUnit.setReturnMessage(ex.getLocalizedMessage())
    }
  }

  private void setUfvCasUnitReferenceIfNecessary(CasInUnit inCasInUnit, UnitFacilityVisit inUnitFacilityVisit) {
    if (inUnitFacilityVisit.getUfvCasUnitReference() == null && inCasInUnit.getCasUnitReference() != null) {
      inUnitFacilityVisit.setFieldValue(InventoryField.UFV_CAS_UNIT_REFERENCE, inCasInUnit.getCasUnitReference());
    }
  }

  private void verifyUnitReferenceAndTransactionReference(CasInUnit inCasInUnit, UnitFacilityVisit inUnitFacilityVisit) {
    if (!inCasInUnit.getCasUnitReference().equals(inUnitFacilityVisit.getUfvCasUnitReference())) {
      final String unitErrorCode = _casMessageHelper.UNIT_REFERENCE_NOT_SAME_CODE
      final String unitErrorMsg = _casMessageHelper.UNIT_REFERENCE_NOT_SAME_MESSAGE + "[Incoming=" + inCasInUnit.getCasUnitReference() +
          ",Existing=" + inUnitFacilityVisit.getUfvCasUnitReference() + "]"
      logMsg(unitErrorCode + ":" + unitErrorMsg)
      inCasInUnit.setReturnStatus(Status.ERROR)
      inCasInUnit.setReturnCode(unitErrorCode)
      inCasInUnit.setReturnMessage(unitErrorMsg)
      return
    }
    if (inCasInUnit.getCasTransactionReference() != null &&
        inUnitFacilityVisit.getUfvCasTransactionReference() != null &&
        !inCasInUnit.getCasTransactionReference().equals(inUnitFacilityVisit.getUfvCasTransactionReference())) {
      final String unitErrorCode = _casMessageHelper.TRANSACTION_REFERENCE_NOT_SAME_CODE
      final String unitErrorMsg = _casMessageHelper.TRANSACTION_REFERENCE_NOT_SAME_MESSAGE + "[Incoming=" + inCasInUnit.getCasTransactionReference() +
          ",Existing=" + inUnitFacilityVisit.getUfvCasTransactionReference() + "]"
      logMsg(unitErrorCode + ":" + unitErrorMsg)
      inCasInUnit.setReturnStatus(Status.ERROR)
      inCasInUnit.setReturnCode(unitErrorCode)
      inCasInUnit.setReturnMessage(unitErrorMsg)
    }
  }

  protected void validateUnitActive(CasInUnit inCasInUnit) throws BizViolation {
    BizViolation bv = null
    UnitManager unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID)
    UnitFacilityVisit unitFacilityVisit = null
    try {
      unitFacilityVisit = unitManager.findActiveUfvForUnitDigits(inCasInUnit.getUnitId())
    } catch (BizViolation inBizViolation) {
      if (InventoryPropertyKeys.UNIT_INSPECTOR_FOUND_NO_ACTIVE_UNITS.equals(inBizViolation.getMessageKey())) {
        //Unit is not active, check if it is payload in a bundle
        UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID)
        Equipment equipment = Equipment.findEquipment(inCasInUnit.getUnitId())
        if (equipment == null) {
          throw inBizViolation
        }
        Unit attachedUnit = unitFinder.findAttachedUnit(ContextHelper.getThreadComplex(), equipment)
        if (attachedUnit == null) {
          throw inBizViolation
        }
        if (attachedUnit.isUnitBundled()) {
          unitFacilityVisit = attachedUnit.getUnitActiveUfvNowActive()
          logMsg("The identified unit[" + inCasInUnit.getUnitId() + "] is part of the bundle whose primary unit is [" + attachedUnit.getUnitId() +
              "] which will be passed for business processing")
        } else {
          throw inBizViolation
        }
      } else {
        throw inBizViolation
      }
    }
    inCasInUnit.setUnitFacilityVisit(unitFacilityVisit)
    Unit unit = unitFacilityVisit.getUfvUnit()
    inCasInUnit.setUnit(unit)
    ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
    IEventType eventType = servicesManager.getEventType(EventEnum.UNIT_DERAMP.getKey())

    if (!unit.isActive()) {
      bv = BizViolation.create(InventoryPropertyKeys.UNITS__NOT_ACTIVE, bv, inCasInUnit.getUnitId(), eventType.getId())
    }

    if (!unitFacilityVisit.equals(unit.getUnitActiveUfvNowActive())) {
      bv = BizViolation.create(InventoryPropertyKeys.UNITS_NOT_ACTIVE_IN_FACILITY, bv,
          inCasInUnit.getUnitId(), unitFacilityVisit.getUfvFacility().getFcyId())
    }

    if (bv != null) {
      throw bv
    }
  }
  /**
   * Handles the unit capture message and update the unit. Creates/Updates the entity attributes from xml message in a separate inner transaction
   */
  protected void handleUnitCaptureAndUpdate(CasInUnit inCasInUnit) {
    if (UNIT_CAPTURE_CANNOT_IDENTIFY.equals(_action)) {
      Class relatedEntityClass = null
      Serializable relatedentityKey = null
      IControlFinder controlFinder = (IControlFinder) Roastery.getBean(IControlFinder.BEAN_ID)
      WorkAssignment workAssignment = controlFinder.findWaFromEcEvents(inCasInUnit.getCasUnitReference(), WaMovePurposeEnum.QC_MOVE)
      if (workAssignment != null) {
        relatedEntityClass = WorkAssignment.class
        relatedentityKey = workAssignment.getWorkassignmentGkey()
      }
      recordExceptionServiceEventInInnerTransaction(relatedentityKey, relatedEntityClass,
          EventType.resolveIEventType(EventEnum.CHE_ERROR_CONTAINER_COULD_NOT_BE_IDENTIFIED),
          CONTAINER_CANNOT_BE_IDENTIFIED_EVENT_MESSAGE + "[Unit Reference id = " + inCasInUnit.getCasUnitReference() + "]")
      return //nothing more to be done in this case
    }
    if (!UNIT_CAPTURE_IMAGE_TYPE.equals(_action)) {
      final PersistenceTemplate persistenceTemplate = new PersistenceTemplate(getUserContext())
      final MessageCollector messageCollector = persistenceTemplate.invoke(new CarinaPersistenceCallback() {
        @Override
        protected void doInTransaction() {
          if (UNIT_CAPTURE_CREATE_TYPE.equals(_action)) {
            createUnit(inCasInUnit)
          }
          // Record cas unit capture event
          recordCasUnitEvent(inCasInUnit, EventEnum.CAS_UNIT_CAPTURE)
          //Update the unit
          updateUnitAttributes(inCasInUnit)
        }
      })
      if (messageCollector.hasError()) {
        logMsg(_casMessageHelper.BIZ_ERROR_CODE + ":" + messageCollector.toString())
        inCasInUnit.setReturnStatus(Status.ERROR)
        inCasInUnit.setReturnCode(_casMessageHelper.BIZ_ERROR_CODE)
        inCasInUnit.setReturnMessage(messageCollector.toString())
        return
      }
      //Requery the Unit and UFV to get fresh Unit in case of create
      // in identify and update cases the unit and ufv were already fetched in outer session so refresh the unit and ufv
      try {
        if (inCasInUnit.getUnit() != null) {
          if (UNIT_CAPTURE_CREATE_TYPE.equals(_action)) {
            inCasInUnit.setUnit(Unit.hydrate(inCasInUnit.getUnit().getUnitGkey()))
          } else {
            HibernateApi.getInstance().refresh(inCasInUnit.getUnit())
          }
        }
        if (inCasInUnit.getUnitFacilityVisit() != null) {
          if (UNIT_CAPTURE_CREATE_TYPE.equals(_action)) {
            inCasInUnit.setUnitFacilityVisit(UnitFacilityVisit.hydrate(inCasInUnit.getUnitFacilityVisit().getUfvGkey()))
          } else {
            HibernateApi.getInstance().refresh(inCasInUnit.getUnitFacilityVisit())
          }
        }
      } catch (Exception ex) {
        logMsg(_casMessageHelper.BIZ_ERROR_CODE + ":" + ex.getLocalizedMessage())
        inCasInUnit.setReturnStatus(Status.ERROR)
        inCasInUnit.setReturnCode(_casMessageHelper.BIZ_ERROR_CODE)
        inCasInUnit.setReturnMessage(ex.getLocalizedMessage())
        return
      }
    }
    //Handle the capture message(enqueue it)
    try {
      handleUnitCapture(inCasInUnit)
    } catch (Exception ex) {
      logMsg(_casMessageHelper.BIZ_ERROR_CODE + ":" + ex.getLocalizedMessage())
      inCasInUnit.setReturnStatus(Status.ERROR)
      inCasInUnit.setReturnCode(_casMessageHelper.BIZ_ERROR_CODE)
      inCasInUnit.setReturnMessage(ex.getLocalizedMessage())
    }
  }
  /**
   * Records the Cas Unit event against the unit
   * @param inCasInUnit CAS Unit
   * @param inEventType event type
   */
  protected void recordCasUnitEvent(CasInUnit inCasInUnit, IEventType inEventType) {
    String nodeText = "Action: " + _action + "," + inCasInUnit.getUnitNode().toString()
    if (nodeText.length() > 255) {
      nodeText = nodeText.substring(0, 254)
    }
    Long unitGkey = inCasInUnit.getUnit().getUnitGkey()
    Unit unit = Unit.hydrate(unitGkey)
    recordEvent(unit, inEventType, null, nodeText)
  }

  private boolean areUnitsReadyToTransfer(@NotNull final String inCaptureType) {

    Boolean retVal = true;

    final OcrEvent imageEvent;

    if (UNIT_CAPTURE_IMAGE_TYPE.equals(inCaptureType)) {
      imageEvent = UNIT_CAPTURE_IMAGE;
    } else if (UNIT_CAPTURE_IDENTIFY_TYPE.equals(inCaptureType)) {
      imageEvent = UNIT_CAPTURE_IDENTIFY;
    }

    for (CasInUnit casInUnit : _casInUnits) {
      if (Status.ERROR != casInUnit.getReturnStatus()) {
        Boolean isTransferAllowed = false

        IControlFinder controlFinder = (IControlFinder) Roastery.getBean(IControlFinder.BEAN_ID);
        final WorkAssignment workAssignment = controlFinder.
                findWaFromEcEvents(casInUnit.getCasUnitReference(), WaMovePurposeEnum.QC_MOVE)
        final Double weight = casInUnit.getWeight();
        final UnitFacilityVisit ufv = casInUnit.getUnitFacilityVisit();
        final EquipNominalLengthEnum containerLength;
        if (ufv != null) {
          containerLength = ufv.getUfvUnit().getPrimaryEq().getEqEquipType().getEqtypNominalLength();
        } else {
          containerLength = null;
        }

        if (workAssignment == null) {
          casInUnit.setReturnStatus(Status.ERROR)
          casInUnit.setReturnCode(_casMessageHelper.INVALID_CAS_UNIT_REFERENCE_CODE)
          casInUnit.setReturnMessage(_casMessageHelper.INVALID_CAS_UNIT_REFERENCE_MESSAGE)
        } else {
          IControlManager controlManager = (IControlManager) Roastery.getBean(IControlManager.BEAN_ID);
          isTransferAllowed = controlManager.validateUnitTransferAllowed(imageEvent, casInUnit.getCasUnitReference(),
                  workAssignment, ufv, containerLength, weight, EciCraneLiftMode.SINGLE, null);
        }
          casInUnit.setReadyToTransfer(isTransferAllowed)

        if (!isTransferAllowed) {
          retVal = false;
        }
      }
    }

    return retVal;
  }

  /**
   * Sends the ready to transfer message
   */
  protected void sendReadyToTransferMessage(@NotNull final Boolean inReadyToTransfer) {
    String payloadXml = null
    Map<String, String> additionalInfoMap = new HashMap<String, String>()
    additionalInfoMap.put(REQUEST_TYPE_PARAM, READY_TO_TRANSFER_MESSAGE)
    additionalInfoMap.put(VISIT_TYPE_PARAM, _visitType)
    additionalInfoMap.put(VISIT_ID_PARAM, _visitId)
    additionalInfoMap.put(CRANE_ID_PARAM, _craneId)
    additionalInfoMap.put(READY_TO_TRANSFER, _casHelper.translateBooleanToYN(inReadyToTransfer))
    if (UNIT_CAPTURE_IMAGE_TYPE.equals(_action)) {
      payloadXml = _casHelper.getXmlPayloadContent(additionalInfoMap, _unitsXml)
    } else if (UNIT_CAPTURE_IDENTIFY_TYPE.equals(_action)) {
      //Create xml
      List<Serializable> primaryKeysList = new ArrayList<Serializable>()
      for (CasInUnit casInUnit : _casInUnits) {
        if (Status.ERROR != casInUnit.getReturnStatus() && casInUnit.getUnitFacilityVisit() != null) {
          primaryKeysList.add(casInUnit.getUnitFacilityVisit().getPrimaryKey())
        }
      }
      if (primaryKeysList.isEmpty()) {
        logMsg("No unit is ready for transfer for this capture message")
        return
      }
      Serializable[] primaryKeys = primaryKeysList.toArray(new Serializable[primaryKeysList.size()])
      DomainQuery dq = _casHelper.createUnitScalarQuery(primaryKeys)
      String unitsXmlFromN4 = _casHelper.createUnitXml(dq, true, null, null, null)
      final String rdtPayloadXml = getReadyToTransferXmlPayloadContent()
      final String tmpPayloadXml = "\n" + "<units>" + "\n" + unitsXmlFromN4 + "\n" + "</units>" + "\n" + rdtPayloadXml
      payloadXml = _casHelper.getXmlPayloadContent(additionalInfoMap, tmpPayloadXml)
      //Record ready to transfer event against the units
      for (CasInUnit casInUnit : _casInUnits) {
        if (Status.ERROR != casInUnit.getReturnStatus() && casInUnit.getReadyToTransfer()) {
          recordCasUnitEvent(casInUnit, EventEnum.CAS_UNIT_READY_TO_TRANSFER)
        }
      }
    } else {
      logMsg(Level.ERROR, "Send ready to transfer sent for message of a wrong action : " + _action)
    }
    if (payloadXml != null) {
      logMsg("Message from N4 to CAS service for 'readyToTransfer' message for action '" + _action + "': " + payloadXml)
      String endpoint = VISIT_TYPE_TRAIN.equals(_visitType)? _casHelper.CAS_RAIL_OUTBOUND : _casHelper.CAS_OUTBOUND;
      String webServiceResponse = _casHelper.invokeOutboundCasService(payloadXml, endpoint)
      logMsg("Response from CAS service for 'readyToTransfer' message sent by N4 for action '" + _action + "': " + webServiceResponse)
    }
  }

  private String getReadyToTransferXmlPayloadContent() {
    def writer = new StringWriter();
    def xml = new MarkupBuilder(writer);
    xml."ready-to-transfer"() {
      for (CasInUnit casInUnit : _casInUnits) {
        field(id: casInUnit.getUnitId(), "ready-to-transfer": casInUnit.getReadyToTransferAsString());
      }
    }

    String out = writer.toString();
    return out;
  }

  private logAndSetCasUnitError(CasInUnit inCasInUnit, final String inErrorCode, final String inErrorMessage) {
    logMsg(inErrorCode + ":" + inErrorMessage)
    inCasInUnit.setReturnStatus(Status.ERROR)
    inCasInUnit.setReturnCode(inErrorCode)
    inCasInUnit.setReturnMessage(inErrorMessage)
  }

  /**
   * Handles the unit position update. It records an event and pusts the message on the queue
   * @param inCasInUnit
   */
  protected void handleUnitPositionUpdate(CasInUnit inCasInUnit) {
    if (inCasInUnit.getUnit() != null) {
      //Record a unit position update event
      recordCasUnitEvent(inCasInUnit, EventEnum.CAS_UNIT_POSITION_UPDATE)
    }
    // handle the unit position update
    //Get the position
    String locType = null
    String location = null
    String block = null
    String row = null
    String column = null
    String tier = null
    String prevLocType = null
    String prevLocation = null
    String prevBlock = null
    String prevRow = null
    String prevColumn = null
    String prevTier = null

    Node unitNode = inCasInUnit.getUnitNode()
    Node currentPositionNode = unitNode."eci-updated-position"[0]
    if (currentPositionNode != null) {
      locType = currentPositionNode.attribute("loc-type")
      if (StringUtils.isBlank(locType)) {
        logAndSetCasUnitError(inCasInUnit, _casMessageHelper.MISSING_LOC_TYPE_CODE, _casMessageHelper.MISSING_LOC_TYPE_MESSAGE)
        return
      }
      location = currentPositionNode.attribute("location")
      if (StringUtils.isBlank(location)) {
        logAndSetCasUnitError(inCasInUnit, _casMessageHelper.MISSING_LOCATION_CODE, _casMessageHelper.MISSING_LOCATION_MESSAGE)
        return
      }
      // todo add error codes and messages for block etc.
      block = currentPositionNode.attribute("block")
      row = currentPositionNode.attribute("row")
      column = currentPositionNode.attribute("column")
      tier = currentPositionNode.attribute("tier")
    } else {
      logAndSetCasUnitError(inCasInUnit, _casMessageHelper.MISSING_CURRENT_POSITION_NODE_CODE,
          _casMessageHelper.MISSING_CURRENT_POSITION_NODE_MESSAGE)
      return
    }

    Node prevPositionNode = unitNode."eci-previous-position"[0]
    if (prevPositionNode != null) {
      prevLocType = prevPositionNode.attribute("loc-type")
      prevLocation = prevPositionNode.attribute("location")
      prevBlock = prevPositionNode.attribute("block")
      prevRow = prevPositionNode.attribute("row")
      prevColumn = prevPositionNode.attribute("column")
      prevTier = prevPositionNode.attribute("tier")
    }
    
    final EciCraneLiftMode craneLiftMode;
    if (MAIN_TROLLEY_LOC_TYPE.equals(locType) || PORTAL_TROLLEY_LOC_TYPE.equals(locType)) {
      craneLiftMode = CommonEciPositionAdapter.getEciCraneLiftMode(row, column);
    } else {
      craneLiftMode = null
    }

    final IUnitPositionUpdatedParms unitPositionUpdatedParms = new UnitPositionUpdatedParms(inCasInUnit.getUnitId(),
            inCasInUnit.getCasUnitReference(), inCasInUnit.getCasTransactionReference(), getUserContext(), _craneId, craneLiftMode,
            locType, location, block, row, column, tier,
            prevLocType, prevLocation, prevBlock, prevRow, prevColumn, prevTier);

    final IEciServiceInvoker eciServiceInvoker = (IEciServiceInvoker) PortalApplicationContext.getBean(IEciServiceInvoker.BEAN_ID)
    try {
      eciServiceInvoker.enqueueQcasServiceRequest(unitPositionUpdatedParms);
    }catch (Exception exc){
      LogUtils.forceLogAtDebug(LOGGER, "enqueueQcasServiceRequest result: " + CarinaUtils.getStackTrace(exc));
      _additionalInfoMap.put("serviceEnqueue", exc.getMessage());
    }
  }

  /**
   * Handles the unit capture message
   *
   */
  protected void handleUnitCapture(CasInUnit inCasInUnit) {

    if (UNIT_CAPTURE_IDENTIFY_TYPE.equals(_action) || UNIT_CAPTURE_CREATE_TYPE.equals(_action) || UNIT_CAPTURE_IMAGE_TYPE.equals(_action) ||
        UNIT_CAPTURE_UPDATE_TYPE.equals(_action)) {
      //Put the information on the ECI service request queue
      final String casEciServiceType
      if (UNIT_CAPTURE_IMAGE_TYPE.equals(_action)) {
        casEciServiceType = EciEsbConstants.ECISERVICE_TYPE_QCAS_CONTAINER_IMAGE_CAPTURED
      } else if (UNIT_CAPTURE_UPDATE_TYPE.equals(_action)) {
        casEciServiceType = EciEsbConstants.ECISERVICE_TYPE_QCAS_CONTAINER_UPDATED
      } else { //Identify or Create
        casEciServiceType = EciEsbConstants.ECISERVICE_TYPE_QCAS_CONTAINER_IDENTIFIED
      }

      final Double weight = (Double) getAttributeValue("gross-weight", inCasInUnit.getUnitNode().@"gross-weight", Double.class)

      enqueueCasEciServiceRequest(casEciServiceType, inCasInUnit, weight)
      if (UNIT_CAPTURE_IMAGE_TYPE.equals(_action)) {
        handleImageCaptureMessage(inCasInUnit)
      }
    }
  }

  /**
   * @param inRequestType type of CAS ECI request
   * @param inCasInUnit information about the CAS container operation
   * @param inWeight measured weight of the container (if available)
   */
  private void enqueueCasEciServiceRequest(
          @NotNull final String inRequestType,
          @NotNull final CasInUnit inCasInUnit,
          @Nullable final Double inWeight
  ) {
    final IEciServiceInvoker eciServiceInvoker = (IEciServiceInvoker) PortalApplicationContext.getBean(IEciServiceInvoker.BEAN_ID)
    //do not use the unit id from inCasUnit directly if unit is present, in case of bundles it might be not be the primary unit of bundle
    String unitId = null
    int unitLength = 20
    if (inCasInUnit.getUnit() != null) {
      unitId = inCasInUnit.getUnit().getUnitId()
      Integer actualLength = InventoryCargoUtils.getUnitLength(unitId)
      if (actualLength != null) {
        unitLength = actualLength.intValue()
      }
    } else {
      unitId = inCasInUnit.getUnitId()
    }
    eciServiceInvoker.enqueueQcasServiceRequest(
        inRequestType,
        getUserContext(),
        inCasInUnit.getCasTransactionReference(),
        inCasInUnit.getCasUnitReference(),
        _craneId,
        unitId,
        unitLength,
        inWeight
    )
    //logMsg(String.format("executing(%s) for(%s) weight(%s)", inRequestType, inCasInUnit, inWeight))
  }
  /**
   * Handles the unit capture message with action 'Image'
   *
   */

  protected void handleImageCaptureMessage(CasInUnit inCasInUnit) {
    inCasInUnit.setReturnStatus(Status.SUCCESS)
    inCasInUnit.setReturnCode(_casMessageHelper.UNIT_CAPTURE_HANDLED_SUCCESSFULLY_CODE)
    inCasInUnit.setReturnMessage(_casMessageHelper.UNIT_CAPTURE_HANDLED_SUCCESSFULLY_MESSAGE + " [Action=" + _action + "]")
  }
  /**
   * This is to validates unit so that a new unit with invalid equipment type is not created.
   * @param inUnit
   * @return
   */
  protected boolean isIsoCodeValid(final CasInUnit inUnit) {
    final Node node = inUnit.getUnitNode();
    final boolean isValid = isIsoCodeValid(node);
    if (!isValid) {
      logMsg(_casMessageHelper.INVALID_ISO_CODE_FOR_CREATE_OR_UPDATE_ERROR_CODE + ":" +
          _casMessageHelper.INVALID_ISO_CODE_FOR_CREATE_OR_UPDATE_ERROR_MESSAGE + " " + inUnit.getUnitId());

      inUnit.setReturnStatus(Status.ERROR);
      inUnit.setReturnCode(_casMessageHelper.INVALID_ISO_CODE_FOR_CREATE_OR_UPDATE_ERROR_CODE);
      inUnit.setReturnMessage(_casMessageHelper.INVALID_ISO_CODE_FOR_CREATE_OR_UPDATE_ERROR_MESSAGE);
    }
    return isValid;
  }

  /**
   * This is to validates unit so that a new unit with invalid equipment type is not created.
   * @param inUnit
   * @return
   */
  protected boolean isIsoCodeValid(@NotNull final groovy.util.Node inUnitNode) {

    final String isoCode = inUnitNode.attributes().get("iso-code");
    if (!StringUtils.isBlank(isoCode)) {
      EquipType equipType = EquipType.findEquipType(isoCode);
      if (equipType == null) {
        return false;
      }
    }
    return true;
  }

  /**
   * Creates a unit corresponding to the
   * @param inCasInUnit CAS unit
   */
  protected void createUnit(CasInUnit inCasInUnit) throws BizViolation {
    UnitManager unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID)

    Container container = null
    String isoCode = inCasInUnit.getUnitNode().attributes().get("iso-code")
    if (StringUtils.isBlank(isoCode)) {
      logMsg(_casMessageHelper.MISSING_ISO_CODE_FOR_CREATE_WARNING_CODE + ":" + _casMessageHelper.MISSING_ISO_CODE_FOR_CREATE_WARNING_MESSAGE)
      inCasInUnit.setReturnStatus(Status.WARNING)
      inCasInUnit.setReturnCode(_casMessageHelper.MISSING_ISO_CODE_FOR_CREATE_WARNING_CODE)
      inCasInUnit.setReturnMessage(_casMessageHelper.MISSING_ISO_CODE_FOR_CREATE_WARNING_MESSAGE)
      container = Container.findOrCreateContainer(inCasInUnit.getUnitId(), _dataSourceEnum)
    } else if (!isIsoCodeValid(inCasInUnit)) {
      //this covers non-empty but invalid iso-code
      //proper error code is set here as well
      return;
    } else {
      container = Container.findOrCreateContainer(inCasInUnit.getUnitId(), isoCode, _dataSourceEnum)
    }
    LineOperator unknownOpr = LineOperator.findOrCreateLineOperator(ScopedBizUnit.UNKNOWN_BIZ_UNIT)
    UnitFacilityVisit ufv = unitManager.findOrCreateStowplanUnit(container, _carrierVisit, unknownOpr,
        ContextHelper.getThreadFacility())
    RectifyParms parms = new RectifyParms()
    parms.setUfvTransitState(UfvTransitStateEnum.S20_INBOUND)
    parms.setUnitVisitState(UnitVisitStateEnum.ACTIVE)
    parms.setObCv(CarrierVisit.getGenericCarrierVisit(ContextHelper.getThreadComplex()))
    ufv.rectify(parms)
    final Unit unit = ufv.getUfvUnit()
    unit.updateCategory(UnitCategoryEnum.IMPORT)
    unit.updateFreightKind(FreightKindEnum.MTY)
    //Set the newly created unit and ufv on CasInUnit
    inCasInUnit.setUnit(unit)
    inCasInUnit.setUnitFacilityVisit(ufv)
  }

  /**
   * Update the unit with attributes from unit xml
   */
  protected void updateUnitAttributes(CasInUnit inCasInUnit) throws BizViolation {
    Node unitNode = inCasInUnit.getUnitNode()
    Long unitGkey = inCasInUnit.getUnit().getUnitGkey()
    Long ufvGkey = inCasInUnit.getUnitFacilityVisit().getUfvGkey()

    Unit unit = Unit.hydrate(unitGkey)
    UnitFacilityVisit ufv = UnitFacilityVisit.hydrate(ufvGkey)

    Equipment primaryEquipment = unit.getPrimaryEq()
    UnitEquipment unitEquipment = unit.getUnitPrimaryUe()

    //Validate bundled Equipment
    Node bundledEquipmentNode = unitNode."bundled-equipment"[0]
    if (bundledEquipmentNode != null) {
      NodeList equipmentNodeList = bundledEquipmentNode."equipment"
      equipmentNodeList.each { Node equipmentNode ->
        Equipment equipment = Equipment.findEquipment(equipmentNode.@'id')
        if (equipment == null) {
          final String errorCode = _casMessageHelper.BUNDLED_EQUIPMENT_NOT_FOUND_CODE
          final String errorMsg = _casMessageHelper.BUNDLED_EQUIPMENT_NOT_FOUND_MESSAGE + "[id=" + equipmentNode.@'id' + "]"
          logMsg(errorCode + ":" + errorMsg)
          inCasInUnit.setReturnStatus(Status.ERROR)
          inCasInUnit.setReturnCode(errorCode)
          inCasInUnit.setReturnMessage(errorMsg)
          return
        }
      }
    }

    if (!isIsoCodeValid(inCasInUnit)) {
      //proper error code is set here as well
      return;
    }

    //Gross Weight
    if (unitNode.attributes().containsKey("gross-weight")) {
      unit.updateGoodsAndCtrWtKg((Double) getAttributeValue("gross-weight", unitNode.@"gross-weight", Double.class))
      //unit.setFieldValue(UnitField.UNIT_GOODS_AND_CTR_WT_KG, (Double)getAttributeValue("gross-weight", unitNode.@"gross-weight", Double.class))
    }
    //Yard Measured weight
    if (unitNode.attributes().containsKey("yard-measured-weight")) {
      unit.updateGoodsAndCtrWtKgAdvised((Double) getAttributeValue("yard-measured-weight", unitNode.@"yard-measured-weight", Double.class))
    }
    //ISO code
    if (!UNIT_CAPTURE_CREATE_TYPE.equals(_action)) { //iso-code is handled as part of create
      if (unitNode.attributes().containsKey("iso-code")) {
        primaryEquipment.upgradeEqType(unitNode.@"iso-code", _dataSourceEnum)
      }
    }
    //Height mm
    if (unitNode.attributes().containsKey("height-mm")) {
      primaryEquipment.upgradeEqHeight((Long) getAttributeValue("height-mm", unitNode.@"height-mm", Long.class), _dataSourceEnum)
    }
    //Length mm
    if (unitNode.attributes().containsKey("length-mm")) {
      primaryEquipment.setFieldValue(ArgoRefField.EQ_LENGTH_MM, (Long) getAttributeValue("length-mm", unitNode.@"length-mm", Long.class))
    }
    //Width mm
    if (unitNode.attributes().containsKey("width-mm")) {
      primaryEquipment.setFieldValue(ArgoRefField.EQ_WIDTH_MM, (Long) getAttributeValue("width-mm", unitNode.@"width-mm", Long.class))
    }
    //Tank rail type
    if (unitNode.attributes().containsKey("tank-rail-type")) {
      String tankRails = unitNode.@"tank-rail-type"
      TankRailTypeEnum tankRailTypeEnum = TankRailTypeEnum.getEnum(tankRails)
      if (tankRailTypeEnum == null) {
        throw invalidValueViolation("tank-rail-type", tankRails)
      }
      primaryEquipment.setFieldValue(ArgoRefField.EQ_TANK_RAILS, tankRailTypeEnum)
    }
    //Door direction
    if (unitNode.attributes().containsKey("door-direction")) {
      String doorDirection = unitNode.@"door-direction"
      DoorDirectionEnum doorDirectionEnum = DoorDirectionEnum.getEnum(doorDirection)
      if (doorDirectionEnum == null) {
        throw invalidValueViolation("door-direction", doorDirection)
      }
      ufv.updateCurrentDoorDir(doorDirectionEnum)
    }
    //Update the is-sealed, is-bundle and is-placarded attributes update it before seals, bundle and observer placards
    //Is Sealed
    if (unitNode.attributes().containsKey("is-sealed")) {
      unit.setFieldValue(InventoryField.UNIT_IS_CTR_SEALED, (Boolean) getAttributeValue("is-sealed", unitNode.@"is-sealed", Boolean.class))
    }
    //Is Bundled
    if (unitNode.attributes().containsKey("is-bundle")) {
      unit.setFieldValue(InventoryField.UNIT_IS_BUNDLE, (Boolean) getAttributeValue("is-bundle", unitNode.@"is-bundle", Boolean.class))
    }
    //Is Placarded
    if (unitNode.attributes().containsKey("is-placarded") && unitEquipment != null) {
      boolean isPlacarded = (Boolean) getAttributeValue("is-placarded", unitNode.@"is-placarded", Boolean.class)
      unitEquipment.setFieldValue(InventoryField.UE_PLACARDED, isPlacarded ? PlacardedEnum.YES : PlacardedEnum.NO)
    }
    //OOG
    Node oogNode = unitNode.'oog'[0]
    if (oogNode != null) {
      unit.
          updateOog(extractOog("back-cm", oogNode.@"back-cm"), extractOog("front-cm", oogNode.@"front-cm"), extractOog("left-cm", oogNode.@"left-cm"),
              extractOog("right-cm", oogNode.@"right-cm"), extractOog("top-cm", oogNode.@"top-cm"))
    }
    //Seals
    Node sealsNode = unitNode.'seals'[0]
    if (sealsNode != null) {
      unit.updateSeals(sealsNode.@"seal-1", sealsNode.@"seal-2", sealsNode.@"seal-3", sealsNode.@"seal-4")
    }

    //Damages
    Node damagesNode = unitNode.'damages'[0]
    if (damagesNode != null) {
      if (unitEquipment != null) {
        // Clear all existing damages - they will be completely replaced by this update
        unitEquipment.attachDamages(null)
        EquipClassEnum equipClass = unitEquipment.getUeEquipment().getEqClass()
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
    //Flags
    Node flagsNode = unitNode.'flags'[0]
    if (flagsNode != null) {
      ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
      NodeList holdsNodeList = flagsNode.'hold'
      holdsNodeList.each { Node holdNode ->
        String holdId = holdNode.@'id'
        servicesManager.applyHold(holdId, unit, null, null, "cas")
      }
      NodeList permissionsNode = flagsNode.'permission'
      permissionsNode.each { Node permissionNode ->
        String permissionId = permissionNode.@'id'
        servicesManager.applyPermission(permissionId, unit, null, null, "cas")
      }
    }

    //Observed placards
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
          //Update the attributes
          //ISO code
          if (equipmentNode.attributes().containsKey("iso-code")) {
            equipment.upgradeEqType(equipmentNode.@"iso-code", _dataSourceEnum)
          }
          //Height mm
          if (equipmentNode.attributes().containsKey("height-mm")) {
            equipment.upgradeEqHeight((Long) getAttributeValue("height-mm", equipmentNode.@"height-mm", Long.class), _dataSourceEnum)
          }
          //Length mm
          if (equipmentNode.attributes().containsKey("length-mm")) {
            equipment.setFieldValue(ArgoRefField.EQ_LENGTH_MM, (Long) getAttributeValue("length-mm", equipmentNode.@"length-mm", Long.class))
          }
          //Width mm
          if (equipmentNode.attributes().containsKey("width-mm")) {
            equipment.setFieldValue(ArgoRefField.EQ_WIDTH_MM, (Long) getAttributeValue("width-mm", equipmentNode.@"width-mm", Long.class))
          }
          //Attach it to the unit
          //unit.attachPayload(equipment)
          unit.attachEquipment(equipment, EqUnitRoleEnum.PAYLOAD, true, false, true, false);
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

  private Long extractOog(String inField, String inValue) {
    return getAttributeValue(inField, inValue, Long.class) as Long
  }
  /**
   * A hook for subclasses to add additional parameters/attributes to the attribute map of CasInUnit. This method is called immediately after the
   * CasInUnit is constructed
   * @param inCasInUnit newly created CasInUnit
   */
  protected void initializeCasUnit(CasInUnit inCasInUnit) {
  }

  protected String createResponseXMLForUfvConeStatusUpdate(final ArrayList<String> updateResult) {
    Collections.sort(updateResult);
    Map<String, String> updateResultMap = new HashMap<>();
    for (String result : updateResult) {
      if (result.toLowerCase().contains("error")) {
        String[] keyVal = result.split("~");
        if (keyVal.length == 2) {
          updateResultMap.put(keyVal[0], keyVal[1]);
        }
      }
    }
    return createResponseXml(updateResultMap);
  }

  protected String createResponseXml(Map<String, String> inAdditionalInfo) {
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.payload() {
      parameters() {
        if (_parameterMap != null && !_parameterMap.isEmpty()) {
          _parameterMap.keySet().each {
            parameter(id: it, value: _parameterMap.get(it))
          }
        }
      }
      if (inAdditionalInfo != null && !inAdditionalInfo.isEmpty()) {
        "additional-info"() {
          inAdditionalInfo.keySet().each {
            field(id: it, value: inAdditionalInfo.get(it))
          }
        }
      }
    }
    String out = writer.toString()
    return out
  }

  protected String createResponseXmlForUnitMessages(Map<String, String> inAdditionalInfo) {
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.payload() {
      parameters() {
        if (_parameterMapForResponse != null && !_parameterMapForResponse.isEmpty()) {
          _parameterMapForResponse.keySet().each {
            parameter(id: it, value: _parameterMapForResponse.get(it))
          }
        }
      }
      if (inAdditionalInfo != null && !inAdditionalInfo.isEmpty()) {
        "additional-info"() {
          inAdditionalInfo.keySet().each {
            field(id: it, value: inAdditionalInfo.get(it))
          }
        }
      }
      "units-response"() {
        for (CasInUnit casInUnit : _casInUnits) {
          "unit-response"("cas-unit-reference": casInUnit.getCasUnitReference(), "cas-transaction-reference": casInUnit.getCasTransactionReference(),
              id: casInUnit.getUnitId(), status: casInUnit.getStatusAsString(), "ready-to-transfer": casInUnit.getReadyToTransferAsString()) {
            message(code: casInUnit.getReturnCode(), text: casInUnit.getReturnMessage())
          }
        }
      }
    }
    String out = writer.toString()
    return out
  }

  /**
   * Translates an attribute value to correct class.
   *
   * @param inName name of the attribute
   * @param inName value of the attribute
   * @param inValueClass The java Class of the value within the Entity to which it belongs
   * @return translated value of the attribute
   * @throws BizViolation if attribute is present and can not be parsed
   */
  protected Object getAttributeValue(String inName, String inValue, Class inValueClass) throws BizViolation {
    IPropertyResolver resolver = ArgoUtils.getPropertyResolver(inValueClass)
    return resolver.resolve(inName, inValue)
  }
  /**
   * Returns exception for invalid value
   * @param inFieldId id of the field
   * @param inValue field value
   * @return biz violation
   */
  public BizViolation invalidValueViolation(String inFieldId, Object inValue) {
    return BizViolation.create(ArgoPropertyKeys.VALIDATION_INVALID_VALUE_FOR_FIELD, null, inFieldId, inValue)
  }

  private void initCasHelper() {
    //Get the CasMessageHelper instance
    if (_casHelper == null) {
      _casHelper = getLibrary(CAS_HELPER)
    }
  }

  private void initCasMessageHelper() {
    //Get the CasMessageHelper instance
    if (_casMessageHelper == null) {
      _casMessageHelper = getLibrary(CAS_MESSAGE_HELPER)
    }
  }

  /**
   * Creates xml to be returned in case of errors
   * @param inErrorCode error code
   * @param inErrorMessage error message
   * @return
   */
  private String getXmlErrorContent() {
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.payload() {
      parameters() {
        if (_parameterMap != null && !_parameterMap.isEmpty()) {
          _parameterMap.keySet().each {
            parameter(id: it, value: _parameterMap.get(it))
          }
        }
      }
      error(code: _errorCode, message: _errorMessage) {
      }
    }
    String out = writer.toString()
    logMsg("\nRequest: " + getParametersAsString() + "\nResponse : " + out)
    return out;
  }

  /**
   * Records a service event for a Che exception condition. This method records *and persists* the service event in a transaction. Do not call this
   * method with outstanding changes in the current transaction as these changes *will* be commited when the inner transaction is completed!
   *
   * @param inRelatedEntityKey
   * @param inRelatedEntityClass
   * @param inEventType
   * @param inMessage
   */
  private void recordExceptionServiceEventInInnerTransaction(@Nullable final Serializable inRelatedEntityKey,
                                                             @Nullable final Class inRelatedEntityClass, @NotNull final EventType inEventType,
                                                             @NotNull final String inMessage) {
    final Che che = Che.findCheByShortName(_craneId, ContextHelper.getThreadYard())
    if (che == null) {
      logMsg(Level.ERROR, "Could not find Che with name: " + _craneId + ". Cannot record exception: " + inMessage)
      return
    }
    final PersistenceTemplate persistenceTemplate = new PersistenceTemplate(userContext)
    final MessageCollector messageCollector = persistenceTemplate.invoke(new CarinaPersistenceCallback() {
      @Override
      protected void doInTransaction() {
        Entity relatedEntity = null
        if (inRelatedEntityClass != null && inRelatedEntityKey != null) {
          relatedEntity = (Entity) HibernateApi.getInstance().load(inRelatedEntityClass, inRelatedEntityKey)
        }
        InventoryServicesUtils.recordServiceEventOnCheException(che, relatedEntity, inEventType, inMessage)
      }
    })
    if (messageCollector.hasError()) {
      logMsg(_casMessageHelper.BIZ_ERROR_CODE + ":" + messageCollector.toString())
      return
    }
  }

  /**
   * For double banking node VISIT_ID may contain two comma-separated visit IDs. If UFV is available, we leave the visit ID, corresponding to a "from"
   * or "to" position of UFV's next work instruction. In that case response message will contain only that visit ID.
   *
   * If this information is not available, we validate both given visit IDs: leave the one (any)
   * that has valid corresponding carrier visit. In that case response message will contain both comma-separated visit IDs.
   */
  private void adjustCarrierVisit() {
    if (_visitType.equals(VISIT_TYPE_VESSEL)) {
      String[] visitIDs = StringUtils.split(_visitId, ',');
      if (visitIDs.size() > 1) {
        _carrierVisit = null
        if (_casInUnits[0] != null) {
          Container ctr = Container.findContainer(_casInUnits[0].getUnitId());
          if (ctr != null) {
            UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
            UnitFacilityVisit ufv = unitFinder.findLiveUfvByEquipment(ContextHelper.getThreadFacility(), ctr);
            if (ufv != null) {
              WorkInstruction wi = ufv.getNextWorkInstruction();
              if(wi != null) {
                def fromVisitId = wi.getWiFromPosition().getPosLocId()
                def toVisitId = wi.getWiToPosition().getPosLocId()

                def actualVisitID = null
                for (aVisitID in visitIDs) {
                  if (aVisitID.equals(fromVisitId) || aVisitID.equals(toVisitId)) {
                    actualVisitID = aVisitID
                    logMsg("Of two comma separated visit IDs " + _visitId + " the one, corresponding to given UFV is selected: " + actualVisitID)
                    break
                  }
                }
                if(actualVisitID != null) {
                  _visitId = actualVisitID
                  _parameterMapForResponse.put(VISIT_ID_PARAM, _visitId)
                }
              }
            }
          }
        }
      }
      _carrierVisit = CarrierVisit.findVesselVisit(ContextHelper.getThreadFacility(), _visitId)
      if(_carrierVisit == null) {
        logMsg("Cannot determine actual visit ID from two comma separated visit IDs " + _visitId)
        for(aVisitID in visitIDs) {
          CarrierVisit aVisit = CarrierVisit.findVesselVisit(ContextHelper.getThreadFacility(), aVisitID)
          if(aVisit != null) {
            _visitId = aVisitID
            _carrierVisit = aVisit
            break
          }
        }
      }
    } else if (_visitType.equals(VISIT_TYPE_TRAIN)) {
      _carrierVisit = CarrierVisit.findTrainVisit(ContextHelper.getThreadComplex(), ContextHelper.getThreadFacility(), _visitId)
    }
  }

  /*** CasInUnit **********************************************************************************************************************************/

  /**
   * This class encapsulates the unit information coming from the CAS, it also holds any error information added during validation and unit facility
   * visit information id validation is successful
   */
  protected class CasInUnit {

    CasInUnit(Node inUnitNode) {
      _unitNode = inUnitNode
      _casUnitReference = _unitNode."@cas-unit-reference"
      _casTransactionReference = _unitNode."@cas-transaction-reference"
      _unitId = _unitNode."@id"
      final String weightStr = _unitNode."@gross-weight"
      _weight = (weightStr == null) ? null : Double.parseDouble(weightStr)
      _returnStatus = Status.SUCCESS
      _returnCode = _casMessageHelper.UNIT_SUCCESSFULLY_PROCESSED_CODE
      _returnMessage = _casMessageHelper.UNIT_SUCCESSFULLY_PROCESSED_MESSAGE
      _readyToTransfer = null
    }

    private String _casUnitReference
    private String _casTransactionReference
    private String _unitId
    private Double _weight
    private UnitFacilityVisit _unitFacilityVisit
    private Unit _unit
    //Message which will be returned in the response for this unit
    private String _returnMessage
    private String _returnCode
    private Status _returnStatus
    private Node _unitNode
    private Boolean _readyToTransfer
    //A generic fields for any additional attributes which may be used by subclasses
    protected Map<String, Object> _attributeMap = new HashMap<String, Object>()

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

    Double getWeight() {
      return _weight
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

    boolean isSuccess() {
      return Status.SUCCESS == _returnStatus
    }

    public String getStatusAsString() {
      switch (_returnStatus) {
        case Status.SUCCESS: return "SUCCESS"
        case Status.ERROR: return "ERROR"
        case Status.WARNING: return "WARNING"
      }
      return ""
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

    Boolean getReadyToTransfer() {
      return _readyToTransfer
    }

    String getReadyToTransferAsString() {
      return _casHelper.translateBooleanToYN(_readyToTransfer)
    }

    void setReadyToTransfer(Boolean inReadyToTransfer) {
      _readyToTransfer = inReadyToTransfer
    }
  }

  private void logMsg(Object inMsg) {
    LOGGER.debug(inMsg);
  }

  protected Map<String, String> _parameterMapForResponse;

  private static final Logger LOGGER = Logger.getLogger(DefaultN4InboundCasMessageHandler_NotInUse.class);
}
