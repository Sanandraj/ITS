package ITS.Enhancements

import com.navis.argo.ArgoExtractEntity
import com.navis.argo.ArgoExtractField
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.*
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.extract.Guarantee
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.business.services.IServiceExtract
import com.navis.external.argo.AbstractGroovyJobCodeExtension
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.portal.query.QueryFactory
import com.navis.inventory.InvEntity
import com.navis.inventory.InvField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.GuaranteeManager
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import org.apache.commons.collections.CollectionUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.time.LocalDate
import java.time.ZoneId

/*
* @Author <a href="mailto:annalakshmig@weservetech.com">Annalakshmi G</a>, 02/JAN/2023

* Requirements : This groovy is used to record waiver for the unit, if the unit is placed in non-deliverble block*
*
* Related enhancement:
* @Inclusion Location	: Incorporated as a code extension of the type GROOVY_JOB_CODE_EXTENSION.groovy
*/

class ITSRecordWaiverForNonDeliverableUnitsJob extends AbstractGroovyJobCodeExtension {

    @Override
    void execute(Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSRecordWaiverForNonDeliverableUnitsJob begin")
        Serializable[] ufvGkeys = fetchNDBUnitsToRecordWaiver()
        Date today = ArgoUtils.timeNow()
        LocalDate lcToday = getLocalDate(today)
        LocalDate lcPreviousDay = lcToday.minusDays(1l)
        if (ufvGkeys != null && ufvGkeys.size() > 0) {
            Serializable[] extractGkeys = fetchExtractGkeys(ufvGkeys)
            LOGGER.debug("ITSRecordWaiverForNonDeliverableUnitsJob ufvGkeys" + ufvGkeys.size())
            UnitFacilityVisit ufv = null

            boolean isWaiverApplied = false
            ChargeableUnitEvent cue
            if (extractGkeys != null && extractGkeys.size() > 0) {
                LOGGER.debug("ITSRecordWaiverForNonDeliverableUnitsJob extractGkeys" + extractGkeys.size())

                for (Serializable extractGkey : extractGkeys) {
                    List<Guarantee> guaranteeList = (List<Guarantee>) Guarantee.getListOfGuarantees(BillingExtractEntityEnum.INV, extractGkey)
                    isWaiverApplied = false
                    cue = ChargeableUnitEvent.hydrate(extractGkey)
                    if (!CollectionUtils.isEmpty(guaranteeList)) {
                        for (Guarantee guarantee : guaranteeList) {
                            isWaiverApplied = false
                            //updating a waiver record is possible only if the CUE status is "CANCELLED"
                            if (dwellEvent.equals(cue.getEventType()) && !IServiceExtract.CANCELLED.equals(cue.getBexuStatus())) {
                                cue.setBexuStatus(IServiceExtract.CANCELLED)

                            }
                            if (guarantee.isWavier() /*&& NDB_WAIVER.equals(guarantee.getGnteNotes())*/) {
                                if (guarantee.getGnteGuaranteeEndDay() != null && lcPreviousDay.equals(getLocalDate(guarantee.getGnteGuaranteeEndDay()))) {
                                    guarantee.setGnteGuaranteeEndDay(today)
                                    isWaiverApplied = true
                                } else if (guarantee.getGnteGuaranteeEndDay() != null && lcToday.equals(getLocalDate(guarantee.getGnteGuaranteeEndDay()))) {
                                    isWaiverApplied = true
                                }
                            }
                        }
                    }
                    if (!isWaiverApplied && cue != null) {
                        LOGGER.debug("ITSRecordWaiverForNonDeliverableUnitsJob inside isWaiveraplied")
                        Guarantee gtr = new Guarantee();
                        String gtId = gtr.getGuaranteeIdFromSequenceProvide();
                        gtr.setFieldValue(ArgoExtractField.GNTE_GUARANTEE_ID, gtId);
                        gtr.setGnteAppliedToClass(BillingExtractEntityEnum.INV);
                        gtr.setGnteAppliedToPrimaryKey((Long) extractGkey);
                        gtr.setGnteAppliedToNaturalKey(cue.getBexuEqId())
                        gtr.setGnteExternalUserId(ContextHelper.getThreadUserId());
                        gtr.setGnteGuaranteeType(GuaranteeTypeEnum.WAIVER)
                        gtr.setGnteOverrideValueType(GuaranteeOverrideTypeEnum.FREE_NOCHARGE)
                        gtr.setGnteQuantity(1)
                        gtr.setGnteGuaranteeStartDay(today)
                        gtr.setGnteGuaranteeEndDay(today)
                        gtr.setGnteNotes("Waived for NDB")
                        gtr.setGnteGuaranteeCustomer(deriveScopedBizUnit("ITSOAC"))
                        try {
                            GuaranteeManager.recordGuarantee(gtr);
                            // guaranteeList.add(gtr)

                        } catch (Exception e) {
                            LOGGER.debug("ITSRecordWaiverForNonDeliverableUnitsJob exception from catch" + e)
                        }
                    }
                }
            }
        }
        Serializable[] ufvDBGkeys = fetchDBUnitsToRecordWaiverForYardAreaOpenDate()
        if (ufvDBGkeys != null && ufvDBGkeys.size() > 0) {
            Serializable[] extractGkeys = fetchExtractGkeys(ufvDBGkeys)
            LOGGER.debug("ITSRecordWaiverForDeliverableUnitsJob ufvGkeys" + ufvDBGkeys.size())
            // UnitFacilityVisit ufv = null
            ChargeableUnitEvent cue
            if (extractGkeys != null && extractGkeys.size() > 0) {
                LOGGER.debug("ITSRecordWaiverForDeliverableUnitsJob extractGkeys" + extractGkeys.size())
                for (Serializable extractGkey : extractGkeys) {
                    List<Guarantee> guaranteeList = (List<Guarantee>) Guarantee.getListOfGuarantees(BillingExtractEntityEnum.INV, extractGkey)
                    cue = ChargeableUnitEvent.hydrate(extractGkey)
                    boolean isWaiverApplied = false
                    if (!CollectionUtils.isEmpty(guaranteeList)) {
                        for (Guarantee guarantee : guaranteeList) {
                            if (dwellEvent.equals(cue.getEventType()) && !IServiceExtract.CANCELLED.equals(cue.getBexuStatus())) {
                                cue.setBexuStatus(IServiceExtract.CANCELLED)
                            }
                            if (guarantee.isWavier() /*&& NDB_WAIVER.equals(guarantee.getGnteNotes())*/) {
                                if (guarantee.getGnteGuaranteeEndDay() != null && lcPreviousDay.equals(getLocalDate(guarantee.getGnteGuaranteeEndDay()))) {
                                    guarantee.setGnteGuaranteeEndDay(today)
                                    isWaiverApplied = true
                                }
                            }
                        }
                    }
                    if (isWaiverApplied) {
                        Unit unit = Unit.hydrate((Serializable) cue.getBexuUnitGkey())
                        unit?.setUnitFlexString08(null)

                    }
                }
            }
        }

        LOGGER.debug("ITSRecordWaiverForNonDeliverableUnitsJob end")
    }

    private ScopedBizUnit deriveScopedBizUnit(String lineId) {
        ScopedBizUnit scopedBizUnit
        scopedBizUnit = ScopedBizUnit.findScopedBizUnit(lineId, BizRoleEnum.LINEOP)
        return scopedBizUnit;
    }

    private getLocalDate(Date date) {
        if (date != null) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        }
        return null
    }

    private Serializable[] fetchExtractGkeys(Serializable[] ufvGkeys) {
        DomainQuery dq = QueryFactory.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT)
                .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_EVENT_TYPE, ["LINE_STORAGE", "UNIT_EXTENDED_DWELL"]))
                .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_UFV_GKEY, ufvGkeys))
                .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_STATUS, ["QUEUED", "PARTIAL"]))
        return HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq)
    }

    private Serializable[] fetchNDBUnitsToRecordWaiver() {

        DomainQuery dq = QueryUtils.createDomainQuery(InvEntity.UNIT_FACILITY_VISIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_CATEGORY, UnitCategoryEnum.IMPORT))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_FLEX03, NO))
                .addDqPredicate(PredicateFactory.isNotNull(InvField.UFV_FLEX_DATE01))
                .addDqOrdering(Ordering.desc(InvField.UFV_TIME_OF_LAST_MOVE)).setDqMaxResults(10)
// for testing purpose
        return HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq)

    }

    private Serializable[] fetchDBUnitsToRecordWaiverForYardAreaOpenDate() {

        DomainQuery dq = QueryUtils.createDomainQuery(InvEntity.UNIT_FACILITY_VISIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_CATEGORY, UnitCategoryEnum.IMPORT))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_FLEX03, YES))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_FLEX08, YES))
                .addDqOrdering(Ordering.desc(InvField.UFV_TIME_OF_LAST_MOVE))/*.setDqMaxResults(2)*/
        // for testing purpose
        return HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq)

    }

    private final static Logger LOGGER = Logger.getLogger(this.class)
    private final static String NO = "N"
    private final static String YES = "Y"
    private final static String dwellEvent = "UNIT_EXTENDED_DWELL"
    private final static String NDB_WAIVER = "Waived for NDB"

}
