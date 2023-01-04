package ITS

import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.external.argo.AbstractGroovyJobCodeExtension
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.quartz.QuartzHelper
import com.navis.inventory.InvEntity
import com.navis.inventory.InventoryField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import org.apache.log4j.Logger
import org.quartz.JobExecutionContext

class ITSSendUnitUpdatesEmodalJob extends AbstractGroovyJobCodeExtension {

    @Override
    void execute(Map<String, Object> inParams) {
        def unitUtil = getLibrary("ITSEmodalLibrary");
        UnitFacilityVisit ufv = null
        Unit unit = null
        DomainQuery dq = QueryUtils.createDomainQuery(InvEntity.UNIT_FACILITY_VISIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_CATEGORY, UnitCategoryEnum.IMPORT))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_FREIGHT_KIND, FreightKindEnum.FCL))
                .addDqPredicate(PredicateFactory.eq(InventoryField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD))
        //   .setDqMaxResults(20)

        List<Serializable> ufvGkeys = HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq)

        if (ufvGkeys != null && !ufvGkeys.isEmpty()) {
            JobExecutionContext executionContext = (JobExecutionContext) inParams.get("JobContext")
            for (Serializable ufvGkey : ufvGkeys) {
                ufv = ufvGkey != null ? UnitFacilityVisit.hydrate(ufvGkey) : null
                if (isInterrupted(executionContext)) {
                    LOGGER.warn("Job Interrupted.... ")
                    break
                }
                if (ufv != null) {
                    unit = ufv.getUfvUnit()
                    LOGGER.warn("unit id "+unit.getUnitId())

                    unitUtil.execute(unit, null)
                }
            }
        }
    }

    private boolean isInterrupted(JobExecutionContext executionContext) {
        return QuartzHelper.isInterrupted(executionContext)
    }

    private final static Logger LOGGER = Logger.getLogger(ITSSendUnitUpdatesEmodalJob.class)

}
