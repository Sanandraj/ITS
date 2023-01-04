/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.ArgoExtractEntity
import com.navis.argo.ArgoExtractField
import com.navis.argo.ArgoPropertyKeys
import com.navis.argo.ContextHelper
import com.navis.argo.business.extract.ChargeableMarineEvent
import com.navis.argo.business.extract.billing.ConfigurationProperties
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.webservice.types.v1_0.ScopeCoordinateIdsWsType
import com.navis.external.framework.AbstractExtensionCallback
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageLevel
import com.navis.services.business.event.Event
import com.navis.services.business.event.EventFieldChange
import com.navis.www.services.argoservice.ArgoServiceLocator
import com.navis.www.services.argoservice.ArgoServicePort
import groovy.xml.MarkupBuilder

import javax.xml.rpc.ServiceException
import javax.xml.rpc.Stub

/*
     *
     * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 12/Nov/2022
     *
     * Requirements : B 5-1 Standardize Marine Invoices -- This groovy is used to request the Marine Billing invoice request to create the invoice in N4 Billing, by passing the eventId and VisitId.
     *                                                     This class will have all the common method for this operation as utility.
     *
     * @Inclusion Location	: Incorporated as a code extension of the type LIBRARY.
     *
     *  Load Code Extension to N4:
            1. Go to Administration --> System -->  Code Extension
            2. Click Add (+)
            3. Enter the values as below:
                Code Extension Name:  ITSAutoBillMarineUtility
                Code Extension Type:  LIBRARY
               Groovy Code: Copy and paste the contents of groovy code.
            4. Click Save button

     *  Set up where ever in code call -ExtensionUtils.getLibrary("LIBRARY_NAME");
     *
     *  S.No    Modified Date   Modified By     Jira      Description
     *
 */


class ITSAutoBillMarineUtility extends AbstractExtensionCallback {


    /**
     *
     * @param inEvent
     * @param inMetafieldId
     * @return
     */
    public static EventFieldChange getFieldChange(Event inEvent, String inMetafieldId) {
        Set<EventFieldChange> fcs = inEvent != null ? inEvent.getFieldChanges() : null
        if (fcs != null && inMetafieldId != null) {
            for (EventFieldChange efc : fcs) {
                if (inMetafieldId.equals(efc.getEvntfcMetafieldId())) {
                    return efc
                }
            }
        }
        return null
    }


    /**
     * Get the Genereal Reference value
     * @return
     */
    public static List<String> getEventsToRecord(String lineId) {
        GeneralReference inEvntFromGenRef = GeneralReference.findUniqueEntryById("ITS", "VESSEL_READY_TO_BILL", "BILLABLE_EVENT", lineId)
        if (inEvntFromGenRef == null) {
            inEvntFromGenRef = GeneralReference.findUniqueEntryById("ITS", "VESSEL_READY_TO_BILL", "BILLABLE_EVENT", null)
        }
        List<String> inEvntList = new ArrayList<String>()

        if (inEvntFromGenRef != null) {
            if (inEvntFromGenRef.getRefValue1() != null) {
                inEvntList.addAll(inEvntFromGenRef.getRefValue1().split(","))
            }
            if (inEvntFromGenRef.getRefValue2() != null) {
                inEvntList.addAll(inEvntFromGenRef.getRefValue2().split(","))
            }
            if (inEvntFromGenRef.getRefValue3() != null) {
                inEvntList.addAll(inEvntFromGenRef.getRefValue3().split(","))
            }
            if (inEvntFromGenRef.getRefValue4() != null) {
                inEvntList.addAll(inEvntFromGenRef.getRefValue4().split(","))
            }
            if (inEvntFromGenRef.getRefValue5() != null) {
                inEvntList.addAll(inEvntFromGenRef.getRefValue5().split(","))
            }
            if (inEvntFromGenRef.getRefValue6() != null) {
                inEvntList.addAll(inEvntFromGenRef.getRefValue6().split(","))
            }
        }
        return inEvntList != null ? inEvntList : null
    }


    /**
     *
     * @param eventExtractEventGkey
     * @return
     */
    public List<ChargeableMarineEvent> findCME(Long eventExtractBatchId) {
        DomainQuery query = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_MARINE_EVENT)
                .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXM_BATCH_ID, eventExtractBatchId));
        List<ChargeableMarineEvent> cmes = HibernateApi.getInstance().findEntitiesByDomainQuery(query);
        return cmes;
    }


    /**
     * generateInvoiceRequest - Construct the request XML
     * @param eventId
     * @param lineId
     * @param visitId
     * @return
     */
    public String deleteInvoiceRequest(String draftNbr) {
        StringWriter writer = new StringWriter();
        MarkupBuilder markupBuilder = new MarkupBuilder(writer);
        markupBuilder.setDoubleQuotes(true);
        markupBuilder.'billing' {
            'delete-draft-invoice-request' {
                'drftInvoiceNbr'(draftNbr)
            }
        }
        return writer.toString()
    }

    /**
     * generateInvoiceRequest - Construct the request XML
     * @param eventId
     * @param lineId
     * @param visitId
     * @return
     */
    public String generateInvoiceRequest(String eventId, String lineId, String visitId) {
        StringWriter writer = new StringWriter();
        MarkupBuilder markupBuilder = new MarkupBuilder(writer);
        markupBuilder.setDoubleQuotes(true);
        markupBuilder.'billing' {
            'generate-invoice-request' {
                'action'(DRAFT)
                'invoiceTypeId'("${eventId}")
                'payeeCustomerId'("${lineId}")
                'payeeCustomerBizRole'(LINEOP)
                'currencyId'("USD")
                markupBuilder.'invoiceParameters' {
                    markupBuilder.'invoiceParameter' {
                        'VesselVisitId'("${visitId}")

                    }
                }
            }
        }
        return writer.toString()
    }


    /**
     * Get the ScopeCoordinates.
     * @return scopeCoordinates
     */
    public ScopeCoordinateIdsWsType getScopeCoordinatesForWebService() {
        ScopeCoordinateIdsWsType scopeCoordinates = new ScopeCoordinateIdsWsType();
        scopeCoordinates.setOperatorId(ContextHelper.getThreadOperator() != null ? ContextHelper.getThreadOperator().getId() : null);
        scopeCoordinates.setComplexId(ContextHelper.getThreadComplex() != null ? ContextHelper.getThreadComplex().getCpxId() : null);
        scopeCoordinates.setFacilityId(ContextHelper.getThreadFacility() != null ? ContextHelper.getThreadFacility().getFcyId() : null);
        scopeCoordinates.setYardId(ContextHelper.getThreadYard() != null ? ContextHelper.getThreadYard().getYrdId() : null);
        return scopeCoordinates;
    }

    /**
     * Fetch the details from N4 Settings - Biling URL, User ID and Password
     * @return ArgoServicePort
     * @throws ServiceException
     */
    public ArgoServicePort getWebServiceStub() throws ServiceException {
        ArgoServiceLocator serviceLocator = new ArgoServiceLocator();
        ArgoServicePort servicePort = serviceLocator != null ?
                serviceLocator.getArgoServicePort(ConfigurationProperties.getBillingServiceURL()) : null;
        Stub stub = (Stub) servicePort;
        if (stub != null) {
            stub._setProperty(Stub.USERNAME_PROPERTY, ConfigurationProperties.getBillingWebServiceUserId());
            stub._setProperty(Stub.PASSWORD_PROPERTY, ConfigurationProperties.getBillingWebServicePassWord());
        }

        return servicePort;
    }


/**
 * registerWarning
 * @param inWarningMessage
 */
    public void registerWarning(String inWarningMessage) {
        MessageCollector ms = getMessageCollector();
        if (ms != null) {
            String[] stringArray = [1]
            stringArray[0] = inWarningMessage
            ms.appendMessage(MessageLevel.WARNING, ArgoPropertyKeys.GROOVY_GENERIC_MESSAGE, null, stringArray);
        }
    }


    private static final String DRAFT = "DRAFT";
    private static final String LINEOP = "LINEOP";

}
