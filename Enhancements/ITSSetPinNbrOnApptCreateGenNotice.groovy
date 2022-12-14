/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.reference.Equipment
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.road.business.appointment.model.GateAppointment
import com.navis.road.business.atoms.TruckerFriendlyTranSubTypeEnum
import com.navis.services.business.event.GroovyEvent

/*
     *
     * @Author : Gopinath Kannappan, 12/Dec/2022
     *
     * Requirements : B 5-1 Standardize Marine Invoices -- This groovy is used to record the Marine Billable event, while Vessel is ready to bill.
     *
     * @Inclusion Location	: Incorporated as a code extension of the type GENERAL_NOTICE_CODE_EXTENSION.
     *
     *  Load Code Extension to N4:
            1. Go to Administration --> System -->  Code Extension
            2. Click Add (+)
            3. Enter the values as below:
                Code Extension Name:  ITSSetPinNbrOnApptCreateGenNotice
                Code Extension Type:  GENERAL_NOTICE_CODE_EXTENSION
               Groovy Code: Copy and paste the contents of groovy code.
            4. Click Save button

     *  Set up General Notice for event type "APPT_CREATE" on GATEAPPOINTMENT Entity then execute this code extension (ITSSetPinNbrOnApptCreateGenNotice).
     *
     *
 */


class ITSSetPinNbrOnApptCreateGenNotice extends AbstractGeneralNoticeCodeExtension {

    @Override
    void execute(GroovyEvent inGroovyEvent) {
        GateAppointment gateAppointment = (GateAppointment) inGroovyEvent.getEntity()
        if (gateAppointment != null && TruckerFriendlyTranSubTypeEnum.PUI.equals(gateAppointment.getGapptTranType())) {
            String gapptCtrId = gateAppointment.getGapptCtrId()

            Unit inUnit = gapptCtrId != null ? findApptUnit(gapptCtrId) : null;
            if (inUnit != null) {
                gateAppointment.setGapptUnitFlexString01(inUnit.getUnitRouting() != null ? inUnit.getUnitRouting().getRtgPinNbr() : null)
            }
        }
    }

    private Unit findApptUnit(String inCtrId) {
        Unit inGapptUnit = null
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                .addDqPredicate(PredicateFactory.in(UnitField.UNIT_VISIT_STATE, [UnitVisitStateEnum.ACTIVE, UnitVisitStateEnum.ADVISED]))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_ID, inCtrId))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CATEGORY, UnitCategoryEnum.IMPORT))
        Unit[] unitList = Roastery.getHibernateApi().findEntitiesByDomainQuery(dq)
        if (unitList != null && unitList.size() > 0) {
            inGapptUnit = (Unit) unitList[0]
        }
        return inGapptUnit;

    }

}
