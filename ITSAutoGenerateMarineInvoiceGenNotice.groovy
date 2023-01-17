/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.webservice.types.v1_0.GenericInvokeResponseWsType
import com.navis.argo.webservice.types.v1_0.ResponseType
import com.navis.argo.webservice.types.v1_0.ScopeCoordinateIdsWsType
import com.navis.external.framework.util.ExtensionUtils
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.www.services.argoservice.ArgoServicePort
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 12/Nov/2022
 *
 * Requirements : B 5-1 Standardize Marine Invoices -- This groovy is used to request the Marine Billing invoice request to create the invoice in N4 Billing, by passing the eventId and VisitId.
 *
 * @Inclusion Location	: Incorporated as a code extension of the type GENERAL_NOTICE_CODE_EXTENSION.
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSAutoGenerateMarineInvoiceGenNotice
 *     Code Extension Type: GENERAL_NOTICES_CODE_EXTENSION
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup General Notice for required marine auto invoice event type For eg : "DOCKAGE","COLD IRONGING" etc on VesselVisit Entity then execute this code extension (ITSAutoGenerateMarineInvoiceGenNotice).
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */


class ITSAutoGenerateMarineInvoiceGenNotice extends AbstractGeneralNoticeCodeExtension {

    private static Logger LOGGER = Logger.getLogger(ITSAutoGenerateMarineInvoiceGenNotice.class)

    @Override
    void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.INFO)
        VesselVisitDetails vvDetail = inGroovyEvent != null ? (VesselVisitDetails) inGroovyEvent.getEntity() : null
        Event inEvent = inGroovyEvent != null ? inGroovyEvent.getEvent() : null
        if (vvDetail == null || inEvent == null) {
            return
        }

        if (vvDetail != null && !(CarrierVisitPhaseEnum.COMPLETE.equals(vvDetail.getVvdVisitPhase()) || CarrierVisitPhaseEnum.DEPARTED.equals(vvDetail.getVvdVisitPhase()))) {
            return
        }

        Object library = ExtensionUtils.getLibrary(ContextHelper.getThreadUserContext(), "ITSAutoBillMarineUtility")
        ExtensionUtils.getLibrary(ContextHelper.getThreadUserContext(), "ITSAutoBillMarineUtility")
        if (vvDetail != null && inEvent != null) {
            ScopedBizUnit vvOperator = vvDetail.getCvdCv() != null ? vvDetail.getCvdCv().getCvOperator() : null
            String lineId = vvOperator != null ? vvOperator.getBzuId() : null
            String inVisitId = vvDetail.getCvdCv() != null ? vvDetail.getCvdCv().getCvId() : null
            String marineInvoiceXML = inVisitId != null ? library.generateInvoiceRequest(inEvent.getEventTypeId(), lineId, inVisitId) : null
            ScopeCoordinateIdsWsType scopeCoordinates = library.getScopeCoordinatesForWebService()
            ArgoServicePort servicePort = library.getWebServiceStub()
            if (servicePort != null && marineInvoiceXML != null) {
                try {
                    GenericInvokeResponseWsType webServiceResponse = scopeCoordinates != null ? servicePort.genericInvoke(scopeCoordinates, marineInvoiceXML) : null
                    ResponseType ptResponse = webServiceResponse != null ? webServiceResponse.getCommonResponse() : null
                    if (ptResponse == null) {
                        LOGGER.error("Something went wrong in N4 Billing.Billing invoice request failed")
                    } else {
                        LOGGER.info("ITSAutoGenerateMarineInvoiceGenNotice ptResponse :" + ptResponse)
                    }
                } catch (Exception ex) {
                    LOGGER.error("Exception while calling billing service" + ex)
                }
            }

        }
    }

}
