import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.Junction
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.services.business.event.GroovyEvent
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Logger


/**
 * Billing 6-1 - Volume Discount
 * Author: uaarthi@weservetech.com - 08/29 - Calculate total moves for the vessel,
 * write it to the vessel and all load/discharge events for the units loaded to/discharged from the vessel
 *
 * Configure against PHASE_VV when the phase is updated to Departed
 */
class ITSCalculateVesselMoveGeneralNotice extends AbstractGeneralNoticeCodeExtension {

    private static Logger LOGGER = Logger.getLogger(ITSCalculateVesselMoveGeneralNotice.class);
    public void execute(GroovyEvent inGroovyEvent) {
        logMsg("ITSCalculateVesselMoveGeneralNotice execution begins ");
        try {
            if (inGroovyEvent == null) {
                return;
            }
            VesselVisitDetails vesselVisitDetails = (VesselVisitDetails) inGroovyEvent.getEntity();
            if (vesselVisitDetails != null) {
                CarrierVisit cv = vesselVisitDetails.getCvdCv()
                if (cv != null && isVslComplete(cv.getCvId())) {
                    int moveCount = calculateMoveCount(cv.getCvId())
                    if(moveCount){
                        vesselVisitDetails.setVvFlexString01(moveCount.toString())
                    }

                }
            }
        } catch (Exception e) {
            logMsg("Exception :" + e.getMessage());
        }
    }

    int calculateMoveCount(String cvId){

        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
                .addDqPredicate(PredicateFactory.eq(InventoryField.UFV_VISIT_STATE, UnitVisitStateEnum.ACTIVE))
                .addDqPredicate(PredicateFactory.ne(UnitField.UFV_UNIT_CATEGORY, UnitCategoryEnum.THROUGH))

        Junction importYard = PredicateFactory.conjunction()
                .add(PredicateFactory.eq(MetafieldIdFactory.valueOf("ufvActualIbCv.cvId"), cvId))
                .add(PredicateFactory.in(InventoryField.UFV_TRANSIT_STATE, [UfvTransitStateEnum.S40_YARD]))

        Junction exportLoaded = PredicateFactory.conjunction()
                .add(PredicateFactory.eq(MetafieldIdFactory.valueOf("ufvActualObCv.cvId"), cvId))
                .add(PredicateFactory.in(InventoryField.UFV_TRANSIT_STATE, [UfvTransitStateEnum.S60_LOADED, UfvTransitStateEnum.S70_DEPARTED]));


        Junction disjunction = PredicateFactory.disjunction()
                .add(importYard)
                .add(exportLoaded);

        dq.addDqPredicate(disjunction)

        int count = HibernateApi.getInstance().findCountByDomainQuery(dq)
        logMsg("Pending count- " + count)
        return count
    }

    boolean isVslComplete(String cvId) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
                .addDqPredicate(PredicateFactory.eq(InventoryField.UFV_VISIT_STATE, UnitVisitStateEnum.ACTIVE))
                .addDqPredicate(PredicateFactory.ne(UnitField.UFV_UNIT_CATEGORY, UnitCategoryEnum.THROUGH))

        Junction importInbound = PredicateFactory.conjunction()
                .add(PredicateFactory.eq(MetafieldIdFactory.valueOf("ufvActualIbCv.cvId"), cvId))
                .add(PredicateFactory.in(InventoryField.UFV_TRANSIT_STATE, [UfvTransitStateEnum.S20_INBOUND, UfvTransitStateEnum.S30_ECIN]))

        Junction exportYard = PredicateFactory.conjunction()
                .add(PredicateFactory.eq(MetafieldIdFactory.valueOf("ufvActualObCv.cvId"), cvId))
                .add(PredicateFactory.in(InventoryField.UFV_TRANSIT_STATE, [UfvTransitStateEnum.S20_INBOUND, UfvTransitStateEnum.S30_ECIN, UfvTransitStateEnum.S40_YARD]));


        Junction disjunction = PredicateFactory.disjunction()
                .add(importInbound)
                .add(exportYard);

        dq.addDqPredicate(disjunction)

        int count = HibernateApi.getInstance().findCountByDomainQuery(dq)
        logMsg("Pending count- " + count)
        return count == 0

    }

    private void logMsg(Object msg) {
        LOGGER.warn(msg);
    }
}
