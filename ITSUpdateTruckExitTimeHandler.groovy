package ITSIntegration

import com.navis.argo.TruckVisitDocument
import com.navis.argo.impl.TruckVisitDocumentImpl
import com.navis.external.argo.AbstractArgoCustomWSHandler
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.UserContext
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageLevel
import com.navis.road.business.atoms.TruckVisitStatusEnum
import com.navis.road.business.model.Truck
import com.navis.road.business.model.TruckVisitDetails
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger
import org.jdom.Element
import org.jdom.Namespace

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDateTime
/*
     *
     * @Author : Mohan Babu, 29/06/2022
     *
     * Requirements : Complete Open Truck Visit at Security Exit stage.
     *
     * @Inclusion Location : Incorporated as a code extension of the type WS_ARGO_CUSTOM_HANDLER.
     *
     *
     * S.No Modified Date   Modified By     Jira Id     SFDC        Change Description
        1     29/06/2022      Mohan         IP-324
        2     11/01/2023      Mohan         IP-458                  Set next stage to empty for truck visit
 */

class ITSUpdateTruckExitTimeHandler extends AbstractArgoCustomWSHandler{
    @Override
    void execute(UserContext userContext, MessageCollector messageCollector, org.jdom.Element inECustom, org.jdom.Element inOutEResponse, Long aLong) {
        Element rootElement = inECustom.getChild("truck-visit")
        Element exitTimeElement = rootElement == null ? null : rootElement.getChild("exit-time")
        Element tvKeyElement = rootElement == null ? null : rootElement.getChild("tv-key")

        if(validateRequest(rootElement, exitTimeElement, tvKeyElement)){
            Namespace sNS = Namespace.getNamespace("argo", "http://www.navis.com/sn4")
            Element responseRoot = new Element("truck-visit", sNS)
            inOutEResponse.addContent(responseRoot)

            TruckVisitDetails tvDetails = TruckVisitDetails.findTruckVisitByGkey(tvKeyElement.getValue() as Long)
            if(tvDetails != null){
                DateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.ENGLISH);
                tvDetails.setTvdtlsFlexDate01(format.parse(exitTimeElement.getValue()))
                if(tvDetails.hasStatus(TruckVisitStatusEnum.OK)){
                    tvDetails.setTvdtlsStatus(TruckVisitStatusEnum.COMPLETE)
                }
                if(StringUtils.isNotBlank(tvDetails.getTvdtlsNextStageId())){
                    tvDetails.setNextStageId(null)
                }
                HibernateApi.getInstance().save(tvDetails)
            }
            else{
                _logger.error("Could not find truck visit with gkey - " + tvKeyElement.getValue())
                registerError("Cannot find truck visit - " + tvKeyElement.getValue())
            }

        }
    }

    private Boolean validateRequest(Element rootElement, Element exitTimeElement, Element tvKeyElement){
        if(rootElement == null) {
            _logger.error("truck visit element is not found in the request")
            return false
        }
        if (exitTimeElement == null || exitTimeElement.getValue() == null || StringUtils.isEmpty(exitTimeElement.getValue())){
            _logger.error("truck exit time element is not found or its value is null")
            return false
        }
        if (tvKeyElement == null || tvKeyElement.getValue() == null || StringUtils.isEmpty(tvKeyElement.getValue())){
            _logger.error("tvkey element is not found or its value is null")
            return false
        }
        return true
    }
    private static Logger _logger = Logger.getLogger(this.class);
}
