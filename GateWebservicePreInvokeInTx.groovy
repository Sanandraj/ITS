import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.util.ArgoGroovyUtils
import com.navis.framework.business.Roastery
import com.navis.framework.business.atoms.LifeCycleStateEnum
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.orders.business.api.OrdersFinder
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.orders.business.eqorders.EquipmentOrderItem
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
 * Requirements : This groovy is used to handle process truck and submit transaction from GOS
 *
 * and this groovy handles only at Ingate.
 *
 * @Inclusion Location	: Incorporated as GroovyPlugin
 *
 * @Author <a href="mailto:smohanbabug@weservetech.com">Mohan Babu</a>
 */

class GateWebservicePreInvokeInTx {
    public void preHandlerInvoke(Map parameter) {
        logMsg("starts");
        LOGGER.setLevel(Level.DEBUG)
        Element element = (Element) parameter.get(ArgoGroovyUtils.WS_ROOT_ELEMENT);
        Element processTruckElement = element.getChild("process-truck") != null ?
                 element.getChild("process-truck") : null;
        Element submitTransactionElement = element.getChild("submit-transaction") != null ?
                element.getChild("submit-transaction") : null;

        if (processTruckElement != null)
        {
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

        if(submitTransactionElement != null){
            Element trkElement = submitTransactionElement != null ? submitTransactionElement.getChild(GateApiConstants.TRUCK) : null
            Element trkTranElement = submitTransactionElement != null ? submitTransactionElement.getChild(GateApiConstants.TRUCK_TRANSACTION) : null
            Element attrTrkTranAppt = trkTranElement.getAttribute(GateApiConstants.APPOINTMENT_NBR)
            Element stage = submitTransactionElement.getChild("stage-id") != null ? submitTransactionElement.getChild("stage-id") : null
            String stageId = stage != null ? stage.getValue() : null;
            if (stageId != null && stageId.equalsIgnoreCase("INGATE")){
                if(trkElement != null && attrTrkTranAppt == null){
                    Attribute attrTrkLic = trkElement.getAttribute(GateApiConstants.TRUCK_LICENSE_NBR)
                    Attribute attrTranType = trkTranElement.getAttribute(GateApiConstants.TRAN_TYPE)
                    Element orderElement = trkTranElement.getChild(GateApiConstants.EQ_ORDER)
                    Element ctrlElement = trkTranElement.getChild(GateApiConstants.EQO_CONTAINER)

                    String trkLic = attrTrkLic != null ? attrTrkLic.getValue() : null
                    String tranType = attrTranType != null ? attrTranType.getValue() : null
                    logMsg("Truck License - " + trkLic)
                    DomainQuery dq = getGateAppointmentQuery(tranType)
                    GateAppointment appt = null
                    Boolean filterAdded = false
                    if(StringUtils.equalsIgnoreCase("DOE",tranType)){
                        if(orderElement != null){
                            dq = addOrderNbrFilter(dq,orderElement)
                            filterAdded = true
                        }
                        if(ctrlElement != null){
                            dq = addCtrNbrFilter(dq,ctrlElement)
                            filterAdded = true
                        }

                    }
                    else if(StringUtils.equalsIgnoreCase("DOM",tranType) && ctrlElement != null){
                        dq = addCtrNbrFilter(dq,ctrlElement)
                        filterAdded = true
                    }
                    else if(StringUtils.equalsIgnoreCase("PUM",tranType)){
                        LOGGER.debug("Inside PUM")
                        Attribute attrOrderNbr = trkTranElement.getAttribute(GateApiConstants.EQO_NBR)
                        String containerNbr = ctrlElement?.getAttribute(GateApiConstants.EQ_ID)?.getValue()
                        if(attrOrderNbr != null){
                            LOGGER.debug("Inside ORder Nbr")
                            String orderNbr = attrOrderNbr.getValue()
                            if(StringUtils.isNotBlank(orderNbr)){
                                dq = dq.addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("gapptOrder.eqboNbr"),orderNbr))
                                filterAdded = true
                                LOGGER.debug("ORder filter added")
                                String lineId =  ctrlElement?.getAttribute("line-id")?.getValue()
                                if(StringUtils.isNotBlank(lineId) ){
                                    ScopedBizUnit lineOp = ScopedBizUnit.findScopedBizUnit(lineId, BizRoleEnum.LINEOP)
                                    OrdersFinder ordersFinder = Roastery.getBean(OrdersFinder.BEAN_ID)
                                    List<EquipmentOrder> orders = ordersFinder.findEquipmentOrderByNbrAndLine(orderNbr, lineOp)
                                    if(orders != null && !orders.isEmpty()){
                                        Set<EquipmentOrderItem> orderItems = orders.get(0).getEqboOrderItems()
                                        if(orderItems != null && !orderItems.isEmpty()){

                                            int index = orderItems.findIndexOf { EquipmentOrderItem i ->
                                                StringUtils.equalsIgnoreCase(i.getEqoiSampleEquipType().getEqtypId(),ctrlElement.getAttribute(GateApiConstants.EQ_TYPE)?.getValue())
                                            }

                                            if(index >= 0){
                                                EquipmentOrderItem orderItem = orderItems[index]
                                                dq = dq.addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("gapptOrderItem.eqboiGkey"),orderItem.getEqboiGkey()))
                                            }
                                        }
                                    }
                                }

                            }
                        }
                        else if(StringUtils.isNotBlank(containerNbr)){
                            dq = addCtrNbrFilter(dq,ctrlElement)
                            filterAdded = true
                        }
                    }
                    else if(StringUtils.equalsIgnoreCase("PUI",tranType) && ctrlElement != null){
                        dq = addCtrNbrFilter(dq,ctrlElement)
                        filterAdded = true
                    }
                    if(filterAdded){
                        LOGGER.debug("Inside filter added")
                        LOGGER.debug("Appt query -" + dq)
                        appt = findAppointment(dq)
                    }
                    else{
                        LOGGER.debug(" filter added false")
                    }
                    if(appt != null){
                        LOGGER.info("Found Appointment for truck license - " + trkLic + "Appt Nbr - "+appt.getApptNbr().toString())
                        trkTranElement.setAttribute(GateApiConstants.APPOINTMENT_NBR, appt.getApptNbr().toString())

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

    private GateAppointment findAppointment(DomainQuery dq){
        dq.setDqMaxResults(1);
        LOGGER.debug("Appt query -" + dq)
        List<GateAppointment> appts = (List<GateAppointment>) HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        if(appts != null && !appts.isEmpty()){
            return appts.get(0)
        }
        else{
            return null
        }
    }
    private DomainQuery getGateAppointmentQuery(String tranType){
        DomainQuery dq = QueryUtils.createDomainQuery("GateAppointment")
                .addDqPredicate(PredicateFactory.eq(RoadApptsField.GAPPT_TRAN_TYPE, tranType))
                .addDqPredicate(PredicateFactory.in(RoadApptsField.GAPPT_STATE,AppointmentStateEnum.CREATED,AppointmentStateEnum.USED))
                .addDqOrdering(Ordering.desc(RoadApptsField.GAPPT_REQUESTED_DATE))
        return dq
    }

    private DomainQuery addOrderNbrFilter(DomainQuery dq, Element orderElement){
        Attribute attrOrderNbr =  orderElement.getAttribute(GateApiConstants.EQO_NBR)
        if(attrOrderNbr != null){
            dq.addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("gapptOrder.eqboNbr"),attrOrderNbr.getValue()))
        }
        return dq
    }
    private DomainQuery addCtrNbrFilter(DomainQuery dq, Element ctrElement){
        Attribute attrCtrNbr =  ctrElement.getAttribute(GateApiConstants.EQ_ID)
        if(attrCtrNbr != null){
            dq.addDqPredicate(PredicateFactory.eq(RoadApptsField.GAPPT_CTR_ID,attrCtrNbr.getValue()))
        }
        return dq
    }

    private void logMsg(String msg) {
        LOGGER.warn("GateWebservicePreInvokeInTx " + msg)
    }
    private final static Logger LOGGER = Logger.getLogger(GateWebservicePreInvokeInTx.class)
}
