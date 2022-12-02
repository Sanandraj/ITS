package ITS

import com.navis.argo.ArgoExtractField
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.model.GeneralReference
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChange
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.portal.FieldChange
import com.navis.framework.portal.UserContext
import com.navis.framework.util.LogUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Created by annalakshmig@weservetech.com on 03/NOV/2022.
 * Requirements : Prod code will update the status as invoiced for UNIT_EXTENDED_DWELL at the time of finalizing.
 * This code extension  is used to change the status of cue as partial/Invoiced for the event UNIT_EXTENDED_DWELL based on ufvTimeOut and ptd
 * @Inclusion Location : Incorporated as a code extension of the type EntityLifecycleInterceptor.
 * Updated by mnaresh@weservetech.com on 15-11-2022
 * The groovy will update  the CUE status as NON_BILLABLE for the LIne and ISO configured in the general reference
 */
class ITSCUEStatusELI extends AbstractEntityLifecycleInterceptor {
    @Override

    public void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        LOGGER.debug("ITSCUEStatusELI Starts :: oncreate")
        validateSrvOrderFC(inEntity, inOriginalFieldChanges, "onCreate");
    }

    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSCUEStatusELI Starts :: on update inOriginalFieldChanges"+inOriginalFieldChanges)
        ChargeableUnitEvent cue = (ChargeableUnitEvent) inEntity._entity

        if (cue != null && "UNIT_EXTENDED_DWELL".equals(cue.getBexuEventType()) && cue.getBexuPaidThruDay() != null) {
            LOGGER.debug("ITSCUEStatusELI Starts :: on update inOriginalFieldChanges PTD")
            LocalDateTime lcPTDDate = cue.getBexuPaidThruDay().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            LocalDateTime lcUnitOut = null
            LocalDateTime possibleOutTime = null

            if (cue.getBexuUfvTimeOut() != null) {
                lcUnitOut = cue.getBexuUfvTimeOut().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            }
            if (lcPTDDate.getHour() < 3) {
                possibleOutTime = lcPTDDate.withHour(3).withMinute(0).withSecond(0)
            } else {
                possibleOutTime = lcPTDDate.plusDays(1).withHour(3).withMinute(0).withSecond(0)

            }

            if ("INVOICED".equals(cue.getBexuStatus()) && (lcUnitOut == null || lcUnitOut.isAfter(possibleOutTime))) {
                updateStatus(inMoreFieldChanges, "QUEUED")

            }
            if ("QUEUED".equals(cue.getBexuStatus()) && (lcUnitOut != null && lcUnitOut.isBefore(possibleOutTime))) {
                updateStatus(inMoreFieldChanges, "INVOICED")
            }
        }

        validateSrvOrderFC(inEntity, inOriginalFieldChanges, "onUpdate");

        if (inOriginalFieldChanges.hasFieldChange(ArgoExtractField.BEXU_LINE_OPERATOR_ID) && inOriginalFieldChanges.hasFieldChange(ArgoExtractField.BEXU_ISO_CODE)) {
            GeneralReference generalReference = GeneralReference.findUniqueEntryById(ITS, POWER_CONTAINERS, (String) ((FieldChange) inOriginalFieldChanges.findFieldChange(ArgoExtractField.BEXU_LINE_OPERATOR_ID)).getNewValue())
            LOGGER.debug("general reference value ::" + generalReference)
            if (generalReference != null && generalReference.getRefValue1() != null) {
                if (generalReference.getRefValue1().toUpperCase().contains((String) ((FieldChange) inOriginalFieldChanges.findFieldChange(ArgoExtractField.BEXU_ISO_CODE)).getNewValue())) {
                    inMoreFieldChanges.setFieldChange(ArgoExtractField.BEXU_STATUS, NON_BILLABLE);
                }
            }
        }
    }

    private void validateSrvOrderFC(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, String inCaller) {
        LogUtils.setLogLevel(ITSCUEStatusELI.class, Level.INFO);
        ChargeableUnitEvent cue = (ChargeableUnitEvent) inEntity._entity;
        log("ITS: ChargeableUnitEvent : " + cue)
        if (inOriginalFieldChanges.hasFieldChange(ArgoExtractField.BEXU_SERVICE_ORDER)) {
            EFieldChange fc = inOriginalFieldChanges.findFieldChange(ArgoExtractField.BEXU_SERVICE_ORDER);
            String oldValue = (String) fc.getPriorValue();
            String newValue = (String) fc.getNewValue();
            log("ITS: Call Method : $inCaller, ChargeableUnitEvent : " + cue + " and Service Order FC Old Value : " + oldValue + " New Value : " +
                    newValue);
            log("ITS: Call Method : $inCaller, ChargeableUnitEvent : " + cue + " and Service Order FC New Value is Empty : " +
                    Boolean.valueOf(newValue.isEmpty()));
            DataSourceEnum dataSourceEnum = ContextHelper.getThreadDataSource();
            UserContext context = ContextHelper.getThreadUserContext();
            String userId = context != null ? context.getUserId() : null;
            log("ITS: Call Method : $inCaller, ChargeableUnitEvent : " + cue + " and Datasource : " + dataSourceEnum + " , UserId : " +
                    userId);
        }
    }

    private static updateStatus(EFieldChanges inMoreFieldChanges, String status) {
        inMoreFieldChanges.setFieldChange(ArgoExtractField.BEXU_STATUS, status);
    }

    private static clearPTD(EFieldChanges inMoreFieldChanges) {
        inMoreFieldChanges.setFieldChange(ArgoExtractField.BEXU_PAID_THRU_DAY, null);
    }
    private static final String ITS = "ITS";
    private static final String POWER_CONTAINERS = "POWER_CONTAINERS"
    private static final String NON_BILLABLE = "NON_BILLABLE"
    private static final Logger LOGGER = Logger.getLogger(this.class)

}