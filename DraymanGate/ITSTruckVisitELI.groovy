package DraymanGate

import com.navis.argo.ContextHelper
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.external.framework.util.ExtensionUtils
import com.navis.road.RoadField
import com.navis.road.business.atoms.TruckVisitStatusEnum
import com.navis.road.business.model.TruckVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @author <a href="mailto:sramasamy@weservetech.com"> Ramasamy Sathappan</a>
 * @since 29-Apr-2022
 * */
class ITSTruckVisitELI extends AbstractEntityLifecycleInterceptor {

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        process(inEntity, inOriginalFieldChanges, inMoreFieldChanges);
    }

    private void process(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        try {
            LOGGER.setLevel(Level.DEBUG);
            logMsg("inOriginalFieldChanges: " + inOriginalFieldChanges);

            def library = ExtensionUtils.getLibrary(ContextHelper.getThreadUserContext(), "ITSDraymanGateAdaptor");
            if (library) {

                TruckVisitDetails truckVisitDetails = inEntity._entity;
                logMsg("TvdtlsNextStageId: " + truckVisitDetails.getTvdtlsNextStageId());

                // process at InGate stage
                if (inOriginalFieldChanges.hasFieldChange(RoadField.TVDTLS_NEXT_STAGE_ID)
                        && (T__INGATE == (String) inOriginalFieldChanges.findFieldChange(RoadField.TVDTLS_NEXT_STAGE_ID).getPriorValue() || T__TROUBLE == (String) inOriginalFieldChanges.findFieldChange(RoadField.TVDTLS_NEXT_STAGE_ID).getPriorValue())
                        && (T__YARD == (String) inOriginalFieldChanges.findFieldChange(RoadField.TVDTLS_NEXT_STAGE_ID).getNewValue() || T__CHECK_DELIVERY == (String) inOriginalFieldChanges.findFieldChange(RoadField.TVDTLS_NEXT_STAGE_ID).getNewValue())
                        && TruckVisitStatusEnum.OK.equals(truckVisitDetails.getTvdtlsStatus())) {

                    logMsg("process at InGate stage");
                    library.prepareAndPushMessageForTvdtls(truckVisitDetails, null); //Site-arrival or pickup
                }

                // On CANCEL or COMPLETE truck visit
                if (inOriginalFieldChanges.hasFieldChange(RoadField.TVDTLS_STATUS)
                        && TruckVisitStatusEnum.COMPLETE != inOriginalFieldChanges.findFieldChange(RoadField.TVDTLS_STATUS).getPriorValue()
                        && TruckVisitStatusEnum.COMPLETE == inOriginalFieldChanges.findFieldChange(RoadField.TVDTLS_STATUS).getNewValue()) {

                    LOGGER.debug("call prepareAndPushMessage for COMPLETE transaction");
                    library.prepareAndPushMessageForTvdtls(truckVisitDetails, T__SITE_DEPARTURE);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Exception in onUpdate : " + e.getMessage());
        }
    }


    private void logMsg(Object inMsg) {
        LOGGER.debug(inMsg);
    }


    private static final String T__INGATE = "ingate";
    private static final String T__TROUBLE = "trouble";
    private static final String T__YARD = "yard";
    private static final String T__CHECK_DELIVERY = "checkdelivery";
    private static final String T__PICKUP = "Pickup";
    private static final String T__SITE_ARRIVAL = "SiteArrival";
    private static final String T__SITE_DEPARTURE = "SiteDeparture";

    private static final Logger LOGGER = Logger.getLogger(ITSTruckVisitELI.class);

}