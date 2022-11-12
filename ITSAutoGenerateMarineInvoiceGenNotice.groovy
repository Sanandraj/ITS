/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.ContextHelper
import com.navis.argo.business.extract.billing.ConfigurationProperties
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger
import javax.xml.rpc.ServiceException
import javax.xml.rpc.Stub
import com.navis.argo.webservice.types.v1_0.GenericInvokeResponseWsType
import com.navis.argo.webservice.types.v1_0.ResponseType
import com.navis.argo.webservice.types.v1_0.ScopeCoordinateIdsWsType
import com.navis.www.services.argoservice.ArgoServiceLocator
import groovy.xml.MarkupBuilder
import com.navis.www.services.argoservice.ArgoServicePort


/*
     *
     * @Author : Gopinath Kannappan, 12/Nov/2022
     *
     * Requirements : B 5-1 Standardize Marine Invoices -- This groovy is used to request the Marine Billing invoice request to create the invoice in N4 Billing, by passing the eventId and VisitId.
     *
     * @Inclusion Location	: Incorporated as a code extension of the type GENERAL_NOTICE_CODE_EXTENSION.
     *
     *  Load Code Extension to N4:
            1. Go to Administration --> System -->  Code Extension
            2. Click Add (+)
            3. Enter the values as below:
                Code Extension Name:  ITSAutoGenerateMarineInvoiceGenNotice
                Code Extension Type:  GENERAL_NOTICE_CODE_EXTENSION
               Groovy Code: Copy and paste the contents of groovy code.
            4. Click Save button

     *  Set up General Notice for required marine auto invoice event type For eg : "DOCKAGE","COLD IRONGING" etc on VesselVisit Entity then execute this code extension (ITSAutoGenerateMarineInvoiceGenNotice).
     *
     *
 */


class ITSAutoGenerateMarineInvoiceGenNotice extends AbstractGeneralNoticeCodeExtension {

    private static Logger LOGGER = Logger.getLogger(ITSAutoGenerateMarineInvoiceGenNotice.class)

    @Override
    void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSAutoGenerateMarineInvoiceGenNotice started")
        VesselVisitDetails vvDetail = inGroovyEvent != null ? (VesselVisitDetails) inGroovyEvent.getEntity() : null;
        Event inEvent = inGroovyEvent != null ? inGroovyEvent.getEvent() : null;
        if (vvDetail != null && inEvent != null) {
            ScopedBizUnit vvOperator = vvDetail.getCvdCv() != null ? vvDetail.getCvdCv().getCvOperator() : null;
            String lineId = vvOperator != null ? vvOperator.getBzuId() : null;
            String inVisitId = vvDetail.getCvdCv() != null ? vvDetail.getCvdCv().getCvId() : null;
            String marineInvoiceXML = inVisitId != null ? generateInvoiceRequest(inEvent.getEventTypeId(), lineId, inVisitId) : null;
            LOGGER.debug("ITSAutoGenerateMarineInvoiceGenNotice marineInvoiceXML :" + marineInvoiceXML)
            ScopeCoordinateIdsWsType scopeCoordinates = getScopeCoordinatesForWebService()
            ArgoServicePort servicePort = getWebServiceStub()
            if (servicePort != null) {
                GenericInvokeResponseWsType webServiceResponse = servicePort.genericInvoke(scopeCoordinates, marineInvoiceXML);
                ResponseType ptResponse = webServiceResponse != null ? webServiceResponse.getCommonResponse() : null;
                if (ptResponse == null) {
                    LOGGER.error("Something went wrong in N4 Billing.Billing invoice request failed")
                } else {
                    LOGGER.debug("ITSAutoGenerateMarineInvoiceGenNotice ptResponse :" + ptResponse)
                }
            }

        }
    }

    /**
     * generateInvoiceRequest - Construct the request XML
     * @param eventId
     * @param lineId
     * @param visitId
     * @return
     */
    private String generateInvoiceRequest(String eventId, String lineId, String visitId) {
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
    private ScopeCoordinateIdsWsType getScopeCoordinatesForWebService() {
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
    private ArgoServicePort getWebServiceStub() throws ServiceException {
        ArgoServiceLocator serviceLocator = new ArgoServiceLocator();
        ArgoServicePort servicePort =
                serviceLocator.getArgoServicePort(ConfigurationProperties.getBillingServiceURL());
        Stub stub = (Stub) servicePort;
        stub._setProperty(Stub.USERNAME_PROPERTY, ConfigurationProperties.getBillingWebServiceUserId());
        stub._setProperty(Stub.PASSWORD_PROPERTY, ConfigurationProperties.getBillingWebServicePassWord());
        return servicePort;
    }


    private static final String DRAFT = "DRAFT";
    private static final String LINEOP = "LINEOP";
}
