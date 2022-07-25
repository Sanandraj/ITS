import com.navis.argo.business.model.GeneralReference
import com.navis.argo.util.ArgoGroovyUtils
import com.navis.framework.business.Roastery
import com.navis.framework.business.atoms.LifeCycleStateEnum
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.road.RoadApptsField
import com.navis.road.RoadField
import com.navis.road.business.appointment.api.IGateAppointmentManager
import com.navis.road.business.appointment.model.GateAppointment
import com.navis.road.business.appointment.model.TruckVisitAppointment
import com.navis.road.business.atoms.AppointmentActionEnum
import com.navis.road.business.atoms.AppointmentStateEnum
import com.navis.road.business.atoms.TruckVisitStatusEnum
import com.navis.road.business.atoms.TruckerFriendlyTranSubTypeEnum
import com.navis.road.business.model.Gate
import com.navis.road.business.model.Truck
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.model.TruckingCompany
import com.navis.road.portal.GateApiConstants
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger
import org.jdom.Attribute
import org.jdom.Element
import org.apache.log4j.Level
/*
 *
 * Requirements : This groovy is used to handle Submit Multiple transactions from GOS for Pick Up Imports
 *
 * and this groovy handles only at Ingate.
 *
 * @Inclusion Location	: Incorporated as GroovyPlugin
 *
 */

class GateWebservicePreInvokeInTx {
    public void preHandlerInvoke(Map parameter) {
        logMsg("starts");
        Element element = (Element) parameter.get(ArgoGroovyUtils.WS_ROOT_ELEMENT);
        Element processTruckElement = element.getChild("process-truck") != null ?
                 element.getChild("process-truck") : null;


        if (processTruckElement != null) {
            Element trkVisitElement = processTruckElement != null ? processTruckElement.getChild(GateApiConstants.TRUCK_VISIT) : null
            Element eqElement = processTruckElement.getChild(GateApiConstants.EQUIPMENT)
            Element containerElement = eqElement != null ? eqElement.getChild(GateApiConstants.CONTAINER) : null
            Element stage = processTruckElement.getChild("stage-id") != null ? processTruckElement.getChild("stage-id") : null
            String stageId = stage != null ? stage.getValue() : null;

            if (stageId != null && stageId.equalsIgnoreCase("INGATE")) {
                if (containerElement != null) {
                    Attribute attrEqId = containerElement.getAttribute(GateApiConstants.EQ_ID)
                    String eqId = attrEqId != null ? attrEqId.getValue() : null
                    logMsg("Equipment ID - " + eqId)
                    if (StringUtils.isNotBlank(eqId)) {
                       List<GateAppointment> gateAppts = findAppointment(eqId)
                        GateAppointment gateAppt = gateAppts != null && !gateAppts.isEmpty() ?  gateAppts.get(0) : null
                        if(gateAppt != null){
                            LOGGER.info("Found Appointment for container number - " + eqId + "Appt Nbr - "+gateAppt.getApptNbr().toString())
                            TruckVisitAppointment tvAppt = gateAppt.getGapptTruckVisitAppointment()
                            if(tvAppt != null){
                                LOGGER.info("Inside truck visit appointment")
                                String truckVisitApptNbr = tvAppt.getApptNbr().toString()
                                trkVisitElement.setAttribute(GateApiConstants.APPOINTMENT_NBR,truckVisitApptNbr)
                                LOGGER.info("Truck visit appt nbr set - " + truckVisitApptNbr)
                            }
                        }
                    }
                }
            }
        }
    }

    private List<GateAppointment> findAppointment(String inCtr) {
        DomainQuery dq = QueryUtils.createDomainQuery("GateAppointment")
                .addDqPredicate(PredicateFactory.eq(RoadApptsField.GAPPT_CTR_ID, inCtr))
                .addDqPredicate(PredicateFactory.eq(RoadApptsField.GAPPT_STATE, AppointmentStateEnum.CREATED));
        dq.setDqMaxResults(1);
        LOGGER.debug("Appt query -" + dq)
        return (List<GateAppointment>) HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

    }


    private void logMsg(String msg) {
        LOGGER.warn("GateWebservicePreInvokeInTx " + msg)
    }
    private final static Logger LOGGER = Logger.getLogger(GateWebservicePreInvokeInTx.class)
}
