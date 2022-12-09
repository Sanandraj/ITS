package ITS

import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.LogicalEntity
import com.navis.argo.business.api.Serviceable
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.LocPosition
import com.navis.cargo.business.model.BillOfLading
import com.navis.cargo.business.model.BlGoodsBl
import com.navis.cargo.business.model.GoodsBl
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import com.navis.services.business.rules.FlagType
import org.apache.commons.collections.CollectionUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author <ahref="mailto:annalakshmig@weservetech.com"  >
 * @Inclusion Location    : Incorporated as a code extension of the type GENERAL_NOTICES_CODE_EXTENSION -->
 * Record an event in associated units of the BL if any one of the holds ['1H', '7H', '2H', '71', '72', '73'] is released/applied.
 *
 */

class ITSRecordBlReleaseOnUnitGenNotice extends AbstractGeneralNoticeCodeExtension {
    private static final Logger LOGGER = Logger.getLogger(ITSRecordBlReleaseOnUnitGenNotice.class);

    @Override
    public void execute(GroovyEvent inGroovyEvent) {
        LOGGER.info("ITSRecordBlReleaseOnUnitGenNotice: Started execution")
        LOGGER.setLevel(Level.DEBUG)
        BillOfLading billOfLading = (BillOfLading) inGroovyEvent.getEntity()
        EventType eventType = EventType.findEventType("BL_HOLD_CHECK")
        Unit unit = null
        if (billOfLading != null) {
            Set<BlGoodsBl> blBlGoodsBls = (Set<BlGoodsBl>) billOfLading.getBlBlGoodsBls()

            if (!CollectionUtils.isEmpty(blBlGoodsBls)) {
                for (BlGoodsBl blGoodsBl : blBlGoodsBls) {
                    unit = blGoodsBl.getBlgdsblGoodsBl().getGdsUnit()
                    UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
                    LOG.warn("ufv " + ufv)
                    if (unit && isDeliverableHoldsReleased(unit.getUnitGoods())) {

                        if(ufv){
                            LocPosition position = ufv.getUfvLastKnownPosition()
                            if (position != null && position.getBlockName() != null && isBlockDeliverable(position.getBlockName())) {
                               /* if( eventType != null)
                                unit.recordEvent(eventType, null, "Recorded by Groovy", ArgoUtils.timeNow())*/
                                unit.setUnitFlexString03("Y")
                                ufv.setUfvFlexDate01(ArgoUtils.timeNow())
                            }
                        }
                    } else {
                        unit.setUnitFlexString03("N")
                        if(ufv) ufv.setUfvFlexDate01(null)
                    }
                }
            }
        }
    }

    boolean isDeliverableHoldsReleased(goodsBase) {
        def holdMap = ['1H', '7H', '2H', '71', '72', '73']
        GoodsBl goodsBl = GoodsBl.resolveGoodsBlFromGoodsBase(goodsBase)
        Set<BillOfLading> blSet = goodsBl?.getGdsblBillsOfLading()

        boolean flagReleased = Boolean.TRUE
         blSet.each {
             bl ->
                 holdMap.each {
                     if (isFlagActive(bl, it)) {
                         flagReleased =  Boolean.FALSE
                     }
                 }
         }

        return flagReleased
    }

    boolean isBlockDeliverable(String blkId) {
        GeneralReference genRef = GeneralReference.findUniqueEntryById("ITS", "DELIVERABLE_BLOCK", blkId)

        if (genRef != null && genRef.getRefValue1().equalsIgnoreCase("Y")) {
            LOG.warn("Block deliverable " + blkId)
            return true
        }
        return false
    }

    private boolean isFlagActive(LogicalEntity logicalEntity, String holdId) {
        FlagType type = FlagType.findFlagType(holdId)
        if (type != null) {
            return type.isActiveFlagPresent(logicalEntity, null, (Serviceable) logicalEntity)
        }
        return false
    }

    private static final Logger LOG = Logger.getLogger(this.class)


}
