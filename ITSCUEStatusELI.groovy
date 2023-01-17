/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.ArgoExtractField
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.services.IServiceExtract
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChange
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.portal.FieldChange
import com.navis.framework.portal.UserContext
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * @Author: mailto:annalakshmig@weservetech.com, AnnaLakshmi G; Date: 16/SEP/2022
 *
 * Requirements : Prod code will update the status as invoiced for UNIT_EXTENDED_DWELL at the time of finalizing.
 *                This groovy will check the out time of the unit and then update the status.
 *
 * @Inclusion Location : Incorporated as a code extension of the type ENTITY_LIFECYCLE_INTERCEPTION
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name:ITSCUEStatusELI
 *     Code Extension Type:ENTITY_LIFECYCLE_INTERCEPTION
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *
 *  S.No    Modified Date        Modified By                                              Jira      Description
 *  1       25/OCT/2022          mailto:mnaresh@weservetech.com Naresh Kumar M.R.        IP-407     update the flex field (dwell date) whenever there is change in First Deliverable Date/Line LFD
 */


class ITSCUEStatusELI extends AbstractEntityLifecycleInterceptor {
    @Override

    public void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        validateSrvOrderFC(inEntity, inOriginalFieldChanges, "onCreate");
    }

    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        ChargeableUnitEvent cue = (ChargeableUnitEvent) inEntity._entity
        LOGGER.setLevel(Level.DEBUG)
        if (cue != null && "UNIT_EXTENDED_DWELL".equals(cue.getBexuEventType()) && inOriginalFieldChanges.hasFieldChange(ArgoExtractField.BEXU_STATUS)) {
            String cueNewStatus = (String) inOriginalFieldChanges.findFieldChange(ArgoExtractField.BEXU_STATUS).getNewValue()
            if (IServiceExtract.CANCELLED.equals(cueNewStatus) || IServiceExtract.GUARANTEED.equals(cueNewStatus)) {
                LOGGER.debug("Guarantee status change")
                inMoreFieldChanges.setFieldChange(ArgoExtractField.BEXU_STATUS, IServiceExtract.QUEUED)
                if (!"FIXED_PRICE".equals(cue.getBexuOverrideValueType())) {
                    inMoreFieldChanges.setFieldChange(ArgoExtractField.BEXU_IS_OVERRIDE_VALUE, Boolean.FALSE)
                }
            }
        }


        if (cue != null && "UNIT_EXTENDED_DWELL".equals(cue.getBexuEventType()) && cue.getBexuPaidThruDay() != null) {
            LocalDateTime lcPTDDate = cue.getBexuPaidThruDay().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            LocalDateTime lcUnitOut = null
            LocalDateTime possibleOutTime = null

            if (cue.getBexuUfvTimeOut() != null) {
                lcUnitOut = cue.getBexuUfvTimeOut().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            }
            if (lcPTDDate != null) {
                possibleOutTime = lcPTDDate.withHour(23).withMinute(59).withSecond(59)

            }
            if (IServiceExtract.QUEUED.equals(cue.getBexuStatus()) && (lcUnitOut != null && lcUnitOut.isBefore(possibleOutTime))) {
                updateStatus(inMoreFieldChanges, "INVOICED")
            }
        }

        validateSrvOrderFC(inEntity, inOriginalFieldChanges, "onUpdate");

        if (inOriginalFieldChanges.hasFieldChange(ArgoExtractField.BEXU_LINE_OPERATOR_ID) && inOriginalFieldChanges.hasFieldChange(ArgoExtractField.BEXU_ISO_CODE)) {
            GeneralReference generalReference = GeneralReference.findUniqueEntryById(ITS, POWER_CONTAINERS, (String) ((FieldChange) inOriginalFieldChanges.findFieldChange(ArgoExtractField.BEXU_LINE_OPERATOR_ID)).getNewValue())
            if (generalReference != null && generalReference.getRefValue1() != null) {
                if (generalReference.getRefValue1().toUpperCase().contains((String) ((FieldChange) inOriginalFieldChanges.findFieldChange(ArgoExtractField.BEXU_ISO_CODE)).getNewValue())) {
                    inMoreFieldChanges.setFieldChange(ArgoExtractField.BEXU_STATUS, NON_BILLABLE);
                }
            }
        }
    }

    private void validateSrvOrderFC(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, String inCaller) {
        LOGGER.setLevel(Level.INFO)
        ChargeableUnitEvent cue = (ChargeableUnitEvent) inEntity._entity;
        LOGGER.info("ITS: ChargeableUnitEvent : " + cue)
        if (inOriginalFieldChanges.hasFieldChange(ArgoExtractField.BEXU_SERVICE_ORDER)) {
            EFieldChange fc = inOriginalFieldChanges.findFieldChange(ArgoExtractField.BEXU_SERVICE_ORDER);
            String oldValue = (String) fc.getPriorValue();
            String newValue = (String) fc.getNewValue();
            LOGGER.info("ITS: Call Method : $inCaller, ChargeableUnitEvent : " + cue + " and Service Order FC Old Value : " + oldValue + " New Value : " +
                    newValue);
            LOGGER.info("ITS: Call Method : $inCaller, ChargeableUnitEvent : " + cue + " and Service Order FC New Value is Empty : " +
                    Boolean.valueOf(newValue.isEmpty()));
            DataSourceEnum dataSourceEnum = ContextHelper.getThreadDataSource();
            UserContext context = ContextHelper.getThreadUserContext();
            String userId = context != null ? context.getUserId() : null;
            LOGGER.info("ITS: Call Method : $inCaller, ChargeableUnitEvent : " + cue + " and Datasource : " + dataSourceEnum + " , UserId : " +
                    userId);
        }
    }

    private static updateStatus(EFieldChanges inMoreFieldChanges, String status) {
        inMoreFieldChanges.setFieldChange(ArgoExtractField.BEXU_STATUS, status);
    }

    private static final String ITS = "ITS";
    private static final String POWER_CONTAINERS = "POWER_CONTAINERS"
    private static final String NON_BILLABLE = "NON_BILLABLE"
    private static final Logger LOGGER = Logger.getLogger(this.class)

}