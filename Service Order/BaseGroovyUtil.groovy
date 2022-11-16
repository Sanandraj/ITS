/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */
import java.util.TimeZone;
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.log4j.Level
import org.apache.log4j.Logger
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.model.Complex
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.argo.webservice.ArgoWebServicesFacade
import com.navis.argo.webservice.IArgoWebService
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldIdFactory;
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.FieldChanges;
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.presentation.context.PresentationContextUtils
import com.navis.framework.util.DateUtil;
import com.navis.framework.util.message.MessageCollector
import com.navis.inventory.InvField;
import com.navis.inventory.InventoryBizMetafield;
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.*
import com.navis.orders.business.api.ServiceOrderManager;
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.orders.business.serviceorders.ServiceOrder
import com.navis.services.business.api.EventManager;
import com.navis.services.business.event.Event;
import com.navis.services.business.rules.EventType
import com.ulcjava.base.application.ClientContext


/*
 *
 *  @Author <ahref="mailto:mharikumar@weservetech.com"  >  Harikumar M</a>,
 *  Date : 17/Oct/2022
 *  Requirement: This groovy is used for maintaining the inventory for all the common methods that needs to be called from majority of the Groovy classes.
 *  @Inclusion Location : Incorporated as a code extension of the type LIBRARY. Copy -->Paste this code(BaseGroovyUtil.groovy)

 */


class BaseGroovyUtil {
	private Logger LOGGER = Logger.getLogger(BaseGroovyUtil.class);
	HibernateApi _hibernateApi = Roastery.getHibernateApi();

		public void recordEvent(String inEventTypeId, Unit inUnit) {
			final ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);
			EventType eventType = EventType.findEventType(inEventTypeId);
			servicesManager.recordEvent(eventType, inEventTypeId, null, null, inUnit)
		}

		public void recordEventWithFieldChanges(String inEventTypeId, Unit inUnit,FieldChanges inRecordOtherChanges) {
			final ServicesManager servicesManager = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);
			EventType eventType = EventType.findEventType(inEventTypeId);
			servicesManager.recordEvent(eventType, inEventTypeId, null, null, inUnit,inRecordOtherChanges)
		}

	public void recordEventOnCompleteServiceOrder(String inEventTypeId, Unit inUnit,  ServiceOrder serviceOrder,ScopedBizUnit billingParty) {

		final EventManager eventManager = (EventManager) Roastery.getBean(EventManager.BEAN_ID);
		EventType eventType = EventType.findEventType(inEventTypeId);
		Map inEventFields = new HashMap()
		TimeZone timeZone = ContextHelper.getThreadUserTimezone();
		LOGGER.setLevel(Level.DEBUG);
		Event currentEvent = eventManager.createEventAndPerformRules(null, eventType, inEventTypeId, null, null, null, inUnit,null ,null, null)
		currentEvent.setEvntResponsibleParty(billingParty)
		try{
			ServiceOrderManager serviceOrderManager = (ServiceOrderManager)Roastery.getBean("serviceOrderManager")
			serviceOrderManager.updateServiceOrderStatus(currentEvent, serviceOrder.getSrvoGkey(), inUnit)
		}catch(Exception e){
			LOGGER.debug("Exception while setting the meta filed id for event :: "+ e.getMessage())
			LOGGER.debug("Exception while setting the meta filed id for event Stack trace :: "+ e.getStackTrace())
		}

	}

	public Collection<Unit> fetchUnitsFromOrder(EqBaseOrder inEqBaseOrder) {
		final UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
		return unitFinder.findUnitsForOrder(inEqBaseOrder)
	}

	public Booking resolveBookingFromUnit(Unit inUnit) {
		if (inUnit) {
			if (inUnit.getDepartureOrder()) {
				EquipmentOrder equipmentOrder = EquipmentOrder.resolveEqoFromEqbo(inUnit.getDepartureOrder());
				if (equipmentOrder) {
					return Booking.resolveBkgFromEqo(equipmentOrder);
				}
			}
		}



		return null;
	}

	/**
	 * Method to invoke the API that is used while submitting the basic web serivce invoke from N4.
	 * @param inScopeCoordinateIds
	 * @param inXml
	 * @param inUserContext
	 * @return
	 */
	public String invokeGenericN4WebService(String inScopeCoordinateIds, String inXml, UserContext inUserContext) {
		LOGGER.setLevel(Level.WARN);
		IArgoWebService ws = new ArgoWebServicesFacade(inUserContext);
		return ws.basicInvoke(inScopeCoordinateIds, inXml);
	}

	/**
	 * Method to invoke the API that is used while submitting the custom web serivce invoke from N4.
	 * @param inScopeCoordinateIds
	 * @param inXml
	 * @param inUserContext
	 * @param inHandlerId
	 * @return
	 */
	public String invokeCustomN4WebService(String inScopeCoordinateIds, String inXml, UserContext inUserContext, String inHandlerId) {
		IArgoWebService ws = new ArgoWebServicesFacade(inUserContext);
		return ws.invoke(inScopeCoordinateIds, inXml, inHandlerId, null);
	}

	/**
	 * Method to return the base User Context
	 * @return
	 */
	public UserContext getUserContext() {
		LOGGER.setLevel(Level.WARN);
		if (PresentationContextUtils.getRequestContext()) {
			logMsg("Fetched User Contect from Presentation Utils");
			return PresentationContextUtils.getRequestContext().getUserContext();
		} else {
			logMsg("Error here ----->ContextHelper.getThreadUserContext() ----->" + ContextHelper.getThreadUserContext());
			return ContextHelper.getThreadUserContext()
		}

	}

	/**
	 * Method to return the current scope coordinates where the user is logged in
	 * @return
	 */
	public String getScopeCordinates(UserContext inUserContext) {
		StringBuilder scopeString = new StringBuilder(ContextHelper.getThreadYardScopeString());
		return scopeString.deleteCharAt(0).toString();
	}

	/**
	 * getFTPClient - Return FTPClient object
	 * @param serverName
	 * @param port
	 * @param userName
	 * @param password
	 * @return
	 */
	public FTPClient getFTPClient(String serverName, int port, String userName, String password) {
		FTPClient ftpClient = new FTPClient();
		ftpClient.connect(serverName, port);
		ftpClient.login(userName, password);
		ftpClient.enterLocalPassiveMode();
		ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

		return ftpClient;
	}
	/**
	 * downloadFtpFile - Return download status of FTP file from remote location - boolean
	 * @param ftpClient
	 * @param remoteFilePath
	 * @param localFilePath
	 * @return
	 */
	public boolean downloadFtpFile(FTPClient ftpClient, String remoteFilePath, String localFilePath) {

		boolean isSucceed = false;
		logMsg("ftpClient : " + ftpClient);
		if (ftpClient == null) {
			return isSucceed;
		}

		try {
			//String remoteFile1 = "/css/ram/new.jpg";
			//String localFilePath = "E:/Docs/FTPfiles/new.jpg";

			File downloadableFile = new File(localFilePath);
			OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(downloadableFile));
			isSucceed = ftpClient.retrieveFile(remoteFilePath, outputStream);
			outputStream.close();

			if (isSucceed) {
				logMsg("Ftp file has been downloaded successfully");
			} else {
				logMsg("Ftp file download failed");
			}


		} catch (IOException e) {
			logMsg("IOException occured : " + e);

		} catch (Exception e) {
			logMsg("Exception occured : " + e);

		} finally {
			try {
				if (ftpClient.isConnected()) {
					ftpClient.logout();
					ftpClient.disconnect();
				}

			} catch (IOException e) {
				logMsg("IOException in finally block : " + e);
				e.printStackTrace();
			}
		}
		return isSucceed;
	}

	/**
	 * fileReader - Return file contents as String
	 * @param localFilePath
	 * @return
	 */
	public String fileReader(String localFilePath) {

		//String localFilePath = "C:\\XYZ.xml";
		BufferedReader reader = null;
		StringBuffer sb = new StringBuffer();

		try {
			String currentLine;
			reader = new BufferedReader(new FileReader(localFilePath));
			while ((currentLine = reader.readLine()) != null) {
				sb = sb.append(currentLine);
			}

			return (sb != null ? sb.toString() : "");

		} catch (IOException e) {
			logMsg("IOException occured : " + e);

		} catch (Exception e) {
			logMsg("Exception occured : " + e);

		} finally {
			try {
				if (reader != null) {
					reader.close();
				}

			} catch (IOException e) {
				logMsg("IOException occured : " + e);
			}
		}


		return "";
	}

	/**
	 * Method to return the latest unit by passing equipment and currentUnit
	 * @param inEquipment
	 * @param inCurrentUnit
	 * @return
	 */
	public Unit fetchLatestUnit(Equipment inEquipment, Unit inCurrentUnit) {
		final UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
		return unitFinder.findNewestPreviousUnit(inEquipment, inCurrentUnit);
	}

	/**
	 * Method to return the latest unit facility visit by passing facility id and eqGkey
	 * @return
	 */
	public UnitFacilityVisit fetchLatestUFV(Long facilityId, Long eqGkey) {

		DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit").addDqPredicate(PredicateFactory.eq(UnitField.UFV_FACILITY, facilityId)).
				addDqPredicate(PredicateFactory.in(UnitField.UFV_VISIT_STATE, Unit.HISTORY_STATES)).addDqPredicate(PredicateFactory.eq(UnitField.UFV_PRIMARY_EQ, eqGkey)).
				addDqOrdering(Ordering.asc(UnitField.UFV_VISIT_STATE));

		List matches = this._hibernateApi.findEntitiesByDomainQuery(dq);

		if (matches != null && matches.size() > 0) {
			return (UnitFacilityVisit) matches.get(0);
		}


	}

	/**
	 * Method to update the latest Advised and Active unit
	 * @param inComplex
	 * @param equipment
	 * @param equipState
	 */
	public void updateActiveAndAdvisedUnit(Complex inComplex, Equipment equipment, EquipmentState equipState) {

		UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
		Unit currentActiveUnit = unitFinder.findActiveUnit(inComplex, equipment);
		Collection<Unit> currentAdvisedUnitList = unitFinder.findAdvisedUnitsUsingEq(inComplex, equipment);

		if (currentActiveUnit != null && currentActiveUnit.getUnitRouting() != null) {
			currentActiveUnit.setUnitGradeID(equipState.getEqsGradeID());
			refreshUnit(currentActiveUnit);
		}


		if (currentAdvisedUnitList != null) {
			for (Unit currentAdvisedUnit : currentAdvisedUnitList) {
				if (currentAdvisedUnit != null && currentAdvisedUnit.getUnitRouting() != null) {
					currentAdvisedUnit.setUnitGradeID(equipState.getEqsGradeID());
					refreshUnit(currentAdvisedUnit);
				}
			}
		}

	}

	/*
	 * Persist the unit object by calling hibernate api (Refreshing Unit)
	 * @param inUnit
	 */

	private void refreshUnit(Unit inUnit) {
		if (inUnit != null) {
			ImpedimentsBean impedimentsBean = inUnit.calculateImpediments(Boolean.TRUE);
			if (impedimentsBean != null) {
				Date dateNow = new Date(ArgoUtils.timeNowMillis() + 1);
				inUnit.updateImpediments(impedimentsBean, dateNow);
				HibernateApi.getInstance().save(inUnit);
				HibernateApi.getInstance().flush();
			}
		}
	}

	/**
	 * Refreshing the Entity Object to persist the data
	 * @return void
	 */
	public void refreshEntity(Object obj) {
		if (obj != null) {
			_hibernateApi.save(obj);
			_hibernateApi.flush();
			logMsg("entity saved");
		}
	}

	/*
	 * Replace the give data with escape sequence wherever necessary
	 * @param xmlData
	 * @return
	 */

	public String escapingXMLData(String xmlData) {
		xmlData = xmlData.replaceAll("<", "&lt;");
		xmlData = xmlData.replaceAll(">", "&gt;");
		xmlData = xmlData.replaceAll("\"", "&quot;");
		xmlData = xmlData.replaceAll("\'", "&apos;");
		return xmlData;
	}

	public Set<String> splitMessage(String inValue, String beginTag, String endTag) {

		LOGGER.debug("splitMessage - BEGIN : " + (inValue != null ? inValue.length() : "null"));
		if (inValue == null || inValue.length() == 0) {
			LOGGER.debug("Invalid data - return from splitMessage");
			return;
		}

		String msgValue = inValue;
		int beginIndex = msgValue.indexOf(beginTag);
		int endIndex = msgValue.indexOf(endTag);
		int lastIndex = 0, count = 0;
		String message
		Set<String> messageSet = new HashSet<String>()

		
		while ((endIndex = msgValue.indexOf(endTag)) != -1) {
			beginIndex = msgValue.indexOf(beginTag);
			endIndex += endTag.length();
			message = msgValue.substring(beginIndex, endIndex);
			messageSet.add(message)
			msgValue = msgValue.substring(endIndex);
		}

		LOGGER.debug("splitMessage - END : " + messageSet.size());

		return messageSet;
	}


    public ServiceOrder fetchServiceOrder(Serializable inServGkey) {
        LOGGER.debug("Inside Method - Start : " + inServGkey);
        ServiceOrder srvOrder = null;
        srvOrder = (ServiceOrder) HibernateApi.getInstance().load(ServiceOrder.class, inServGkey);
        LOGGER.debug("Inside Method - srvOrder : " + srvOrder);
        return srvOrder;
    }

	private void logMsg(String inMsg) {
		LOGGER.debug(inMsg);
	}

}

