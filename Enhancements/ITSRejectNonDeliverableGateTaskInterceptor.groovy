/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

package Enhancements

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.LocPosition
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.spatial.business.model.AbstractBin
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat

/*
 * @Author: mailto: annalakshmig@weservetech.com, Annalakshmi G; Date: 21/11/2022
 *
 *  Requirements: This groovy is used to reject DI transaction if the unit is in non deliverable bay(configured in General reference).
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSRejectNonDeliverableGateTaskInterceptor
 *     Code Extension Type: GATE_TASK_INTERCEPTOR
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *  @Setup: Customize the Groovy code ITSRejectNonDeliverableGateTaskInterceptor at the Ingate stage in RejectContainerNotInYard business task in transaction type DI
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 *
 */


class ITSRejectNonDeliverableGateTaskInterceptor extends AbstractGateTaskInterceptor implements EGateTaskInterceptor {


    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {

        LOGGER.setLevel(Level.DEBUG);
        LOGGER.debug("ITSRejectNonDeliverableGateTaskInterceptor begins !!!")
        TruckTransaction thisTran = inWfCtx.getTran();

        if (null == thisTran) {
            return;
        }
        Unit inUnit = thisTran.getTranUnit();
        if (null == inUnit) {
            return;
        }
        String blockName = null
        String bayName = null
        UnitFacilityVisit ufv = inUnit.getUnitActiveUfvNowActive()
        if (null == ufv) {
            return
        }
        LocPosition position = ufv.getUfvLastKnownPosition()
        if (position) {
            blockName = position.getBlockName()
            bayName = getBayNumber(position)

            String currPosition = position.getPosSlot()
            if (currPosition != null && inUnit?.getUnitEquipment()?.getEqEquipType() != null) {
                position = LocPosition.resolvePosition(ContextHelper.getThreadFacility(), LocTypeEnum.YARD, ContextHelper.getThreadYardId(), currPosition, null, inUnit.getUnitEquipment().getEqEquipType().getEqtypBasicLength())
            }

            if (position != null && StringUtils.isNotEmpty(position.getPosSlot())) {
                blockName = (position.getBlockName() != null) ? position.getBlockName() :
                        position.getPosSlot().indexOf('.') != -1 ? position.getPosSlot().split('\\.')[0] : null
            }
            boolean isSpotDeliverable = true
            if (!StringUtils.isEmpty(blockName)) {
                if (!isBlockDeliverable(blockName, bayName)) {
                    this.getMessageCollector().appendMessage(MessageLevel.SEVERE, PropertyKeyFactory.valueOf("UNIT_DELIVERABLE_STATUS"), "Unit is in Non Deliverable bay", null);
                }
            }
        }
        executeInternal(inWfCtx)

    }

    boolean isBlockDeliverable(String blkId, String bayId) {
        GeneralReference genRef = GeneralReference.findUniqueEntryById("ITS", "DELIVERABLE_BLOCK", blkId)
        if (genRef != null && "Y".equalsIgnoreCase(genRef.getRefValue1()) && isDateWithinRange(genRef)) {
            return true
        }
        if (StringUtils.isNotEmpty(bayId)) {
            return isBayDeliverable(blkId, bayId)
        }
        return false
    }

    boolean isBayDeliverable(String blkId, String bayId) {
        List<GeneralReference> genRefList = (List<GeneralReference>) GeneralReference.findAllEntriesById("ITS", "DELIVERABLE_BAY", blkId)
        if (!CollectionUtils.isEmpty(genRefList)) {
            List<String> deliverableBayList = new ArrayList<>()
            for (GeneralReference generalReference : genRefList) {
                if (generalReference.getRefId3() != null && "Y".equalsIgnoreCase(generalReference.getRefValue1()) && isDateWithinRange(generalReference)) {
                    String[] bays = StringUtils.split(generalReference.getRefId3(), ",")
                    for (String bay : bays) {

                        deliverableBayList.add(generalReference.getRefId2().concat(":").concat(bay))

                    }

                }
            }
            if (deliverableBayList.contains(blkId.concat(":").concat(bayId))) {
                return true
            }

        }

        return false
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
    private final static String block_identifier = "DELIVERABLE_BLOCK"
    private final static String bay_identifier = "DELIVERABLE_BAY"
    private final static String Type = "ITS"
    private final static String No = "N"
    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    private static final Logger LOGGER = Logger.getLogger(ITSRejectNonDeliverableGateTaskInterceptor.class);

}
