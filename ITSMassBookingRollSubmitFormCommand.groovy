/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/


import com.navis.argo.ArgoPropertyKeys
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.PointCall
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.util.FieldChangeTracker
import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChange
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.FieldChanges
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.framework.presentation.ui.message.ButtonTypes
import com.navis.framework.presentation.ui.message.MessageDialog
import com.navis.framework.presentation.ui.message.MessageType
import com.navis.framework.presentation.ui.message.OptionDialog
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.api.UnitReroutePoster
import com.navis.inventory.business.units.Routing
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.orders.OrdersField
import com.navis.orders.OrdersPropertyKeys
import com.navis.orders.business.api.EquipmentOrderManager
import com.navis.orders.business.eqorders.Booking
import com.navis.road.RoadPropertyKeys
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.vessel.business.schedule.VesselVisitLine
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import javax.naming.Context

/**
 * @Author: mailto:skishore@weservetech.com, Kishore S; Date: 11/10/2022
 *
 *  Requirements: 5-14-Mass-Booking-Roll -- This groovy is used to roll multiple booking selected from booking entity.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSMassBookingRollSubmitFormCommand
 *     Code Extension Type: FORM_SUBMISSION_INTERCEPTOR
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup override configuration in variformId - REV_ORD001
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */

class ITSMassBookingRollSubmitFormCommand extends AbstractFormSubmissionCommand {
    private static Logger LOGGER = Logger.getLogger(ITSMassBookingRollSubmitFormCommand.class)

    @Override
    void doBeforeSubmit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inOutFieldChanges,
                        EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        //LOGGER.setLevel(Level.INFO)
        //LOGGER.info("ITSMassBookingRollSubmitFormCommand Starts ::")
        if (inGkeys == null && inGkeys.isEmpty()) {
            return;
        }
        PersistenceTemplate persistenceTemplate = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext())
        persistenceTemplate.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                String constantLineOpr = null
                String constantVvd = null
                int count = 0
                for (Serializable gKey : inGkeys as List<Serializable>) {
                    count = count + 1
                    Booking booking = Booking.hydrate(gKey)
                    if (booking == null) {
                        return;
                    }
                    if (count == 1) {

                        constantLineOpr = booking.getEqoLine() ? booking.getEqoLine().getBzuId() : null;
                        constantVvd = booking.getEqoVesselVisit() != null ? booking.getEqoVesselVisit().getCvId() : null;

                    }
                    if (count > 1) {
                        String bkgCurrentLineOpr = booking.getEqoLine() != null ? booking.getEqoLine().getBzuId() : null;
                        if (bkgCurrentLineOpr != null && !bkgCurrentLineOpr.equals(constantLineOpr)) {
                            registerError("Error : Cannot perform Booking roll for selected bookings")
                            return
                        }

                        if (booking.getEqoVesselVisit()) {
                            String bkgVvd = booking.getEqoVesselVisit().getCvId()
                            if (!bkgVvd.equals(constantVvd)) {
                                registerError("Error : Cannot perform Booking roll for selected bookings")
                                return
                            }
                        }
                    }


                    Long bkgTallyReceive = booking?.getEqoTallyReceive()
                    /*if (bkgTallyReceive > 0) {
                        registerError("Error : Cannot perform Booking roll for selected bookings")
                        return
                    }*/
                }
            }
        })
    }

//Gopal - All units in yard should be rolled as well, when booking rolls.
    @Override
    void submit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {

        PersistenceTemplate persistenceTemplate = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext())
        persistenceTemplate.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                //LOGGER.setLevel(Level.DEBUG)
                List<String> lineInvalid = new ArrayList()
                List<String> podInvalid = new ArrayList()
                List<String> inActivePhase = new ArrayList()
                CarrierVisit carrierVisit = null
                VesselVisitDetails vvd = null
                boolean vvdLineExists = false
                EFieldChange eFieldChange = inFieldChanges.findFieldChange(OrdersField.EQO_VESSEL_VISIT)
                if (eFieldChange != null) {
                    Serializable cvd = (Serializable) eFieldChange?.getNewValue()
                    if (cvd != null) {
                        carrierVisit = CarrierVisit.hydrate(cvd)
                        if (carrierVisit != null) {
                            vvd = VesselVisitDetails.resolveVvdFromCv(carrierVisit)
                        }
                    }
                }

                Set<CarrierVisitPhaseEnum> carrierVisitStateEnum = new HashSet<CarrierVisitPhaseEnum>();
                carrierVisitStateEnum.add(CarrierVisitPhaseEnum.DEPARTED);
                carrierVisitStateEnum.add(CarrierVisitPhaseEnum.CLOSED);
                String pod = null
                for (Serializable gKey : inGkeys as List<Serializable>) {
                    Booking booking = Booking.hydrate(gKey)
                    if (booking == null) {
                        return;
                    }
                    boolean hasException = false
                    if (vvd == null) {
                        vvd = VesselVisitDetails.resolveVvdFromCv(booking?.getEqoVesselVisit())
                        carrierVisit = booking.getEqoVesselVisit()
                    }
                    if (vvd != null && !carrierVisitStateEnum.contains(carrierVisit?.getCvVisitPhase())) {
                        EFieldChange fieldChange = inFieldChanges.findFieldChange(OrdersField.EQO_POD1)
                        if (fieldChange != null) {
                            Serializable rpGkey = (Serializable) fieldChange?.getNewValue()
                            if (rpGkey != null) {
                                RoutingPoint routingPoint = RoutingPoint.hydrate(rpGkey)
                                if (routingPoint != null) {
                                    pod = routingPoint?.getPointId()
                                }
                            }
                        }

                        ScopedBizUnit scopedBizUnit = booking?.getEqoLine()
                        if (pod == null) {
                            pod = booking?.getEqoPod1()?.getPointId()
                        }
                        if (!booking.getEqoLine().equals(vvd.getVvdBizu())) {

                            Set vvdLineSet = vvd?.getVvdVvlineSet()
                            for (VesselVisitLine vesselVisitLine : vvdLineSet) {
                                if (vesselVisitLine.getVvlineBizu().equals(scopedBizUnit)) {
                                    vvdLineExists = true;
                                    break
                                }
                            }
                            if (!vvdLineExists) {
                                lineInvalid.add(booking?.getEqboNbr())
                                hasException = true
                            }
                        }
                        List itineraryPoints = vvd?.getCvdItinerary()?.getItinPoints()
                        if (itineraryPoints != null && itineraryPoints.size() > 0) {
                            Map<String, PointCall> map = new HashMap<String, PointCall>();
                            for (PointCall pointCall : itineraryPoints) {
                                map.put(pointCall.getCallPoint()?.getPointId(), pointCall);
                            }
                            if (!map.containsKey(pod)) {
                                podInvalid.add(booking?.getEqboNbr())
                                hasException = true
                            }
                        }
                    } else {
                        inActivePhase.add(booking?.getEqboNbr())
                        hasException = true
                    }

                    if (hasException) {
                        continue
                    }
                    Boolean isVVChanged = inFieldChanges.hasFieldChange(OrdersField.EQO_VESSEL_VISIT);
                    Boolean isPODChanged = inFieldChanges.hasFieldChange(OrdersField.EQO_POD1);
                    if (isVVChanged || isPODChanged) {
                        UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
                        Collection unitsReceivedForOrder = unitFinder.findUnitsAdvisedOrReceivedForOrder(booking);
                        booking.setEqoVesselVisit(carrierVisit)
                        //if
                        booking.setEqoPod1(RoutingPoint.findRoutingPoint(pod))
                        if (unitsReceivedForOrder != null && !unitsReceivedForOrder.isEmpty()) {
                            Iterator unitIterator = unitsReceivedForOrder.iterator();
                            while (unitIterator.hasNext()) {
                                try {
                                    Unit unit = (Unit) unitIterator.next();
                                    if (unit != null) {
                                        FieldChangeTracker fieldChangeTracker = unit.createFieldChangeTracker();
                                        UnitFacilityVisit unitFacilityVisit = unitFinder.findPreadvisedUnit(ContextHelper.getThreadFacility(), unit?.getPrimaryEq())
                                        if (unitFacilityVisit == null) {
                                            unitFacilityVisit = unit.getUnitActiveUfvNowActive();
                                        }

                                        if (unitFacilityVisit != null) {
                                            Routing unitRouting = unit.getUnitRouting()
                                            if (isVVChanged) {
                                                if (booking.getEqoVesselVisit() != null) {
                                                    unitRouting.setRtgDeclaredCv(booking.getEqoVesselVisit());
                                                    if (booking.getEqoVesselVisit().getCvCvd() != null) {
                                                        unitRouting.setRtgCarrierService(booking.getEqoVesselVisit().getCvCvd().getCvdService());
                                                        unitFacilityVisit.updateObCv(booking.getEqoVesselVisit());
                                                    }
                                                }
                                            }
                                            if (isPODChanged) {
                                                unitRouting.setRtgPOD1(RoutingPoint.findRoutingPoint(pod));
                                            }
                                            UnitReroutePoster reroutePoster = (UnitReroutePoster) Roastery.getBean("unitReroutePoster");
                                            reroutePoster.updateRouting(unit, unitRouting);
                                            FieldChanges fieldChanges = fieldChangeTracker.getChanges(unit);
                                            unit.recordUnitEvent(EventEnum.UNIT_ROLL, fieldChanges, (String) null);
                                        }
                                    }
                                } catch (Exception inEx) {
                                    registerError("Error while routing received unit " + inEx.toString());
                                }
                            }
                        }
                    }
                }

                if (lineInvalid != null && !lineInvalid.isEmpty()) {
                    registerError("Error vessel visit ${carrierVisit?.getCvId()} line  not shared by booking: ${lineInvalid}");

                }
                if (podInvalid != null && !podInvalid.isEmpty()) {
                    registerError("Error POD does't matches the vessel visit itineraryPoints for Booking: ${podInvalid}");
                }
                if (inActivePhase != null && !inActivePhase.isEmpty()) {
                    registerError("Error Vessel visit phase is already in departed or closed");
                }
            }
        })
    }
}

