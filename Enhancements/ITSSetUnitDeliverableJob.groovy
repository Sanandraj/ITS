/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/

package ITS

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.LogicalEntity
import com.navis.argo.business.api.Serviceable
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.LocPosition
import com.navis.cargo.business.model.BillOfLading
import com.navis.cargo.business.model.GoodsBl
import com.navis.external.argo.AbstractGroovyJobCodeExtension
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InvEntity
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.rules.FlagType
import com.navis.spatial.business.model.AbstractBin
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat

/*
* @Author: mailto: annalakshmig@weservetech.com, Annalakshmi G; Date: 18/11/2022
*
*  Requirements: This groovy is used to update the deliverable status of the unit.
*  Related enhancement: Set First Available Day (UnitFlexDate01)
*  3-2 - Set Deliverable only if the List of Delivery holds are released for the Unit - '1H', '7H', '2H', '71', '72', '73'
* IP-452
*
* @Inclusion Location: Incorporated as a code extension of the type
*
*  Load Code Extension to N4:
*  1. Go to Administration --> System --> Code Extensions
*  2. Click Add (+)
*  3. Enter the values as below:
*     Code Extension Name: ITSSetUnitDeliverableJob
*     Code Extension Type: GROOVY_JOB_CODE_EXTENSION
*     Groovy Code: Copy and paste the contents of groovy code.
*  4. Click Save button
*
*  @Setup: Add the Groovy Job with ITSSetUnitDeliverableJob code included
*
*  S.No    Modified Date   Modified By     Jira      Description
*
*/

class ITSSetUnitDeliverableJob extends AbstractGroovyJobCodeExtension {

    @Override
    void execute(Map<String, Object> inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSSetUnitDeliverableJob begin")
        List<GeneralReference> blockList = (List<GeneralReference>) GeneralReference.findAllEntriesById(Type, block_identifier);
        List<GeneralReference> bayList = (List<GeneralReference>) GeneralReference.findAllEntriesById(Type, bay_identifier);
        List<GeneralReference> generalReferenceList = new ArrayList<>()
        generalReferenceList.addAll(blockList)
        generalReferenceList.addAll(bayList)
        List<String> deliverableList = new ArrayList<>()
        List<String> nonDeliverableList = new ArrayList<>()
        UnitFacilityVisit ufv = null
        if (!CollectionUtils.isEmpty(generalReferenceList)) {
            for (GeneralReference generalReference : generalReferenceList) {
                if (isValidGenReference(generalReference) && isDateWithinRange(generalReference)) {
                    if (YES.equalsIgnoreCase(generalReference.getRefValue1())) {
                        if (generalReference.getRefId3() != null) {
                            String[] bays = StringUtils.split(generalReference.getRefId3(), ",")
                            for (String bay : bays) {
                                deliverableList.add(generalReference.getRefId2().concat(":").concat(bay))
                            }

                        } else {
                            deliverableList.add(generalReference.getRefId2())
                        }
                    } else if (NO.equalsIgnoreCase(generalReference.getRefValue1())) {
                        if (generalReference.getRefId3() != null) {
                            String[] bays = StringUtils.split(generalReference.getRefId3(), ",")
                            for (String bay : bays) {
                                nonDeliverableList.add(generalReference.getRefId2().concat(":").concat(bay))
                            }

                        } else {
                            nonDeliverableList.add(generalReference.getRefId2())
                        }
                    }
                }
            }

            if (!CollectionUtils.isEmpty(deliverableList) || !CollectionUtils.isEmpty(nonDeliverableList)) {
                Serializable[] ufvGkeys = fetchUnitsToUpdateDeliverableStatus()
                if (ufvGkeys != null && ufvGkeys.size() > 0) {
                    for (int i = 0; i < ufvGkeys.size(); i++) {
                        ufv = UnitFacilityVisit.hydrate(ufvGkeys[i])
                        if (ufv != null && ufv.getUfvLastKnownPosition() != null) {
                            LocPosition lastKnownPosition = ufv.getUfvLastKnownPosition()
                            Unit unit = ufv.getUfvUnit()
                            if (lastKnownPosition.isWheeled() || lastKnownPosition.isWheeledHeap() || (ufv.getUfvActualObCv() != null
                                    && LocTypeEnum.TRAIN == ufv.getUfvActualObCv().getCvCarrierMode()) || (unit.getUnitRouting() != null && unit.getUnitRouting().getRtgGroup() != null
                                    && StringUtils.isNotEmpty(unit.getUnitRouting().getRtgGroup().getGrpId()))) {
                                unit.setUnitFlexString03("Y")
                                unit.setUnitFlexString06("N")
                                if (ufv.getUfvFlexDate01() == null) { // DO not clear the FAD - [Container sorting Fee]
                                    ufv.setUfvFlexDate01(ArgoUtils.timeNow())
                                }
                                if (ufv.getUfvFlexDate03() == null) {
                                    ufv.setUfvFlexDate03(ArgoUtils.timeNow())
                                }

                            }
                           else {
                                String blockName = null;
                                String bayName = null
                                LocPosition position = ufv.getUfvLastKnownPosition()
                                String currPosition = position.getPosSlot()
                                if (currPosition != null && ufv.getUfvUnit()?.getUnitEquipment()?.getEqEquipType() != null) {
                                    position = LocPosition.resolvePosition(ContextHelper.getThreadFacility(), LocTypeEnum.YARD, ContextHelper.getThreadYardId(), currPosition, null, ufv.getUfvUnit().getUnitEquipment().getEqEquipType().getEqtypBasicLength())
                                }

                                if (position != null && StringUtils.isNotEmpty(position.getPosSlot())) {
                                    blockName = (position.getBlockName() != null) ? position.getBlockName() :
                                            position.getPosSlot().indexOf('.') != -1 ? position.getPosSlot().split('\\.')[0] : null
                                }

                                bayName = getBayNumber(position)
                                if (blockName != null) {
                                    String blockBayId = blockName
                                    if (deliverableList.contains(blockBayId)) {
                                        if (!YES.equalsIgnoreCase(ufv.getUfvUnit()?.getUnitFlexString03())) {
                                            ufv.getUfvUnit()?.setUnitFlexString03(YES)
                                            ufv.getUfvUnit()?.setUnitFlexString07(YES)
                                        }
                                        if (null == ufv.getUfvFlexDate01()) {
                                            GoodsBase goodsBase = ufv.getUfvUnit()?.getUnitGoods()
                                            boolean isHoldReleased = true
                                            if (goodsBase) isHoldReleased = isDeliverableHoldsReleased(goodsBase)
                                            if (isHoldReleased) {
                                                ufv.setUfvFlexDate01(ArgoUtils.timeNow())
                                            }
                                        }
                                        if (null == ufv.getUfvFlexDate03()) {
                                            ufv.setUfvFlexDate03(ArgoUtils.timeNow())
                                        }
                                    } else if (nonDeliverableList.contains(blockBayId)) {
                                        ufv.getUfvUnit()?.setUnitFlexString03(NO)
                                        ufv.getUfvUnit()?.setUnitFlexString07(null)
                                    }
                                    if (bayName != null) {
                                        blockBayId = new StringBuilder().append(blockName).append(":").append(bayName).toString()
                                        if (deliverableList.contains(blockBayId)) {
                                            if (!YES.equalsIgnoreCase(ufv.getUfvUnit()?.getUnitFlexString03())) {
                                                ufv.getUfvUnit()?.setUnitFlexString03(YES)
                                                ufv.getUfvUnit()?.setUnitFlexString07(YES)
                                            }
                                            if (null == ufv.getUfvFlexDate01()) {
                                                GoodsBase goodsBase = ufv.getUfvUnit()?.getUnitGoods()
                                                boolean isHoldReleased = true
                                                if (goodsBase) isHoldReleased = isDeliverableHoldsReleased(goodsBase)
                                                if (isHoldReleased) {
                                                    ufv.setUfvFlexDate01(ArgoUtils.timeNow())
                                                }
                                            }
                                            if (null == ufv.getUfvFlexDate03()) {
                                                ufv.setUfvFlexDate03(ArgoUtils.timeNow())
                                            }
                                        } else if (nonDeliverableList.contains(blockBayId)) {
                                            ufv.getUfvUnit()?.setUnitFlexString03(NO)
                                            ufv.getUfvUnit()?.setUnitFlexString07(null)
                                        }
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }
        LOGGER.debug("ITSDwellPTDUpdateJob end")
    }

    boolean isDeliverableHoldsReleased(goodsBase) {
        List<String> holdMap = ['1H', '7H', '2H', '71', '72', '73']
        GoodsBl goodsBl = GoodsBl.resolveGoodsBlFromGoodsBase(goodsBase)
        Set<BillOfLading> blSet = goodsBl?.getGdsblBillsOfLading()

        boolean flagReleased = Boolean.TRUE
        blSet.each {
            bl ->
                holdMap.each {
                    if (isFlagActive(bl, it)) {
                        flagReleased = Boolean.FALSE
                    }
                }
        }
        return flagReleased
    }

    private boolean isFlagActive(LogicalEntity logicalEntity, String holdId) {
        FlagType type = FlagType.findFlagType(holdId)
        if (type != null) {
            return type.isActiveFlagPresent(logicalEntity, null, (Serviceable) logicalEntity)
        }
        return false
    }

    private boolean isValidGenReference(GeneralReference generalReference) {
        if (generalReference != null && generalReference.getRefId2() != null && generalReference.getRefValue1() != null) {
            return true
        }
        return false
    }

    private String getBayNumber(LocPosition position) {

        String bay = null
        String row = null;
        String slot = null
        AbstractBin stackBin = position.getPosBin();
        if (stackBin != null) {
            if ("ABM_STACK".equalsIgnoreCase(stackBin.getAbnBinType().getBtpId())) {
                String stackBinName = stackBin.getAbnName()
                AbstractBin sectionBin = stackBin.getAbnParentBin();
                if (sectionBin != null && "ABM_SECTION".equalsIgnoreCase(sectionBin.getAbnBinType().getBtpId())) {
                    String sectionBinName = sectionBin.getAbnName()
                    row = sectionBinName;
                    slot = stackBinName.substring(stackBinName.indexOf(sectionBinName) + sectionBinName.size())
                    AbstractBin blockBin = sectionBin.getAbnParentBin();
                    if (!position.isWheeled() && blockBin != null && "ABM_BLOCK".equalsIgnoreCase(blockBin.getAbnBinType().getBtpId())) {
                        String blockBinName = blockBin.getAbnName()
                        row = sectionBinName.substring(sectionBinName.indexOf(blockBinName) + blockBinName.size());
                    }
                }
                if (position.isWheeled()) {
                    bay = slot;
                } else {
                    bay = row;
                }

            }
        }
        return bay
    }

    private boolean isDateWithinRange(GeneralReference generalReference) {
        Date endDate = null
        Date startDate = null
        Date testDate = ArgoUtils.timeNow()
        try {
            startDate = getDate(generalReference.getRefValue2())
            endDate = getDate(generalReference.getRefValue3())
            if (null == startDate && null == endDate) {
                return true
            } else if (startDate != null && testDate.after(startDate) && null == endDate) {
                return true
            } else if (startDate != null && endDate != null && testDate.after(startDate) && testDate.before(endDate)) {
                return true
            } else {
                return false
            }
        } catch (ParseException e) {
            e.printStackTrace()
        }

    }

    private static Date getDate(String dt) throws ParseException {
        if (!StringUtils.isEmpty(dt)) {
            Calendar cal = Calendar.getInstance()
            cal.setTime(dateFormat.parse(dt))
            return cal.getTime()
        }
        return null
    }

    private Serializable[] fetchUnitsToUpdateDeliverableStatus() {

        DomainQuery dq = QueryUtils.createDomainQuery(InvEntity.UNIT_FACILITY_VISIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_CATEGORY, UnitCategoryEnum.IMPORT))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD))
        return HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq)

    }

    private final static Logger LOGGER = Logger.getLogger(ITSSetUnitDeliverableJob.class)
    private final static String YES = "Y"
    private final static String NO = "N"
    private final static String block_identifier = "DELIVERABLE_BLOCK"
    private final static String bay_identifier = "DELIVERABLE_BAY"
    private final static String Type = "ITS"
    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

}
