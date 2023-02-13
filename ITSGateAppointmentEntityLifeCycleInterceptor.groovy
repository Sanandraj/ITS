/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.portal.FieldChange
import com.navis.road.RoadApptsField
import com.navis.road.business.appointment.model.GateAppointment
import com.navis.road.business.appointment.model.TruckVisitAppointment
import org.apache.log4j.Logger

/*
     *
     *  @Author: mailto:uaarthi@weservetech.com, Aarthi U; Date:19/10/2022
     *
     * Requirements : TT-31 Dual Flag Appears in Appointment List,ELI on Appointment to set Dual Flag for Truck Visit Appointments associated to multiple Appointments.
     *
     * @Inclusion Location	: Incorporated as a code extension of the type ENTITY_LIFECYCLE_INTERCEPTION.
     *
     *  Load Code Extension to N4:
            1. Go to Administration --> System -->  Code Extension
            2. Click Add (+)
            3. Enter the values as below:
                Code Extension Name:  ITSGateAppointmentEntityLifeCycleInterceptor
                Code Extension Type:  ENTITY_LIFECYCLE_INTERCEPTION
               Groovy Code: Copy and paste the contents of groovy code.
            4. Click Save button
    * @Set up groovy code in Extension Trigger against "GateAppointment" Entity then execute this code extension (ITSGateAppointmentEntityLifeCycleInterceptor).
    *
    *  S.No    Modified Date   Modified By     Jira      Description
    *   1       25/10/2022      Gopinath K     IP-327    Pin number issue for DI-Setting the FlexString01 with the Pin Number.
    *   2       29/11/2022      Kishore S      IP-311    Removed Persistence - Fixed creation of duplicate appointments even error occured while generating appointment.
 */


class ITSGateAppointmentEntityLifeCycleInterceptor extends AbstractEntityLifecycleInterceptor {
    private static Logger LOGGER = Logger.getLogger(this.class)

    @Override
    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        this.onCreateOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges)
    }

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        this.onCreateOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges)
    }

    private void onCreateOrUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        GateAppointment gateAppointment = inEntity._entity;
        if (gateAppointment == null) {
            return;
        }

        // Dual Flag implementation

        TruckVisitAppointment tva = null

        FieldChange tvaFc = inOriginalFieldChanges.hasFieldChange(RoadApptsField.GAPPT_TRUCK_VISIT_APPOINTMENT) ? (FieldChange) inOriginalFieldChanges.findFieldChange(RoadApptsField.GAPPT_TRUCK_VISIT_APPOINTMENT) : null;
        FieldChange dualFC = inOriginalFieldChanges.hasFieldChange(RoadApptsField.GAPPT_UNIT_FLEX_STRING02) ? (FieldChange) inOriginalFieldChanges.findFieldChange(RoadApptsField.GAPPT_UNIT_FLEX_STRING02) : null;

        if (tvaFc != null) {
            if (tvaFc.getNewValue()) {
                tva = (TruckVisitAppointment) tvaFc.getNewValue()
                reloadDualFlag(tva, gateAppointment, inMoreFieldChanges, tvaFc.getNewValue())

            }
            if (tvaFc.getPriorValue()) {
                tva = (TruckVisitAppointment) tvaFc.getPriorValue()
                reloadDualFlag(tva, gateAppointment, inMoreFieldChanges, tvaFc.getNewValue())
            }
        } else if (dualFC != null && dualFC.getPriorValue() != null && dualFC.getNewValue() == null && gateAppointment.getGapptTruckVisitAppointment() != null) {
            tva = gateAppointment.getGapptTruckVisitAppointment()
            reloadDualFlag(tva, gateAppointment, inMoreFieldChanges, tva)
        }
    }

    void reloadDualFlag(TruckVisitAppointment tva, GateAppointment gappt, EFieldChanges inMoreFieldChanges, Object newValue) {

        if (tva != null) {
            Set<GateAppointment> apptSet = tva.getAppointments(GateAppointment.AppointmentStateGroupEnum.ACTIVE)
            String isDual = (apptSet != null && apptSet.size() >= 2) ? 'Y' : 'N'
            apptSet.each {
                it.setGapptUnitFlexString02(isDual)
            }
            inMoreFieldChanges.setFieldChange(RoadApptsField.GAPPT_UNIT_FLEX_STRING02, isDual)
        } else {
            inMoreFieldChanges.setFieldChange(RoadApptsField.GAPPT_UNIT_FLEX_STRING02, 'N')

        }

        if (newValue == null) {
            inMoreFieldChanges.setFieldChange(RoadApptsField.GAPPT_UNIT_FLEX_STRING02, 'N')
        }
    }
}