


import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.RoutingPoint
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.inventory.InventoryField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.EventFieldChange
import com.navis.services.business.event.GroovyEvent
import com.navis.vessel.business.operation.Vessel
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.text.SimpleDateFormat

/*
*
* * @Author <a href="mailto:sanandaraj@servimostech.com">S Anandaraj</a>, 15/JUL/2022
*
* Requirements : This groovy is used to PIERPASS announcement for the UNIT.
*
* @Inclusion Location	: Incorporated as a code extension of the type GENERAL_NOTICE_CODE_EXTENSION.Copy --> Paste this code (ITSPierPassImportUpdateGenNotice.groovy)
*
* @Set up General Notice for event type "EVNT_TYPE" on  Entity then execute this code extension (ITSPierPassImportUpdateGenNotice).
*
*/

public class ITSPierPassImportUpdateGenNotice extends AbstractGeneralNoticeCodeExtension {
    private static Logger LOGGER = Logger.getLogger(ITSPierPassImportUpdateGenNotice.class)

    public void execute(GroovyEvent inGroovyEvent) {
        //LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSPierPassImportUpdateGenNotice Started at " + new Date());
        String eventType = inGroovyEvent?.getEvent()?.getEventTypeId()
        Unit unit = inGroovyEvent.getEntity()
        UnitFacilityVisit ufv = unit ? unit.getUnitActiveUfv() : null;
        if (!ufv) {
            return;
        }
        if (unit!=null && unit.getUnitCategory() != UnitCategoryEnum.IMPORT ||
                unit.getUnitFreightKind() == FreightKindEnum.MTY) {
            return;
        }


     String TMF_EXEMPT=   unit?.getUnitRouting()?.getRtgGroup()?.getGrpFlexString01()
        GeneralReference genRef = GeneralReference.findUniqueEntryById("PIERPASS", "IMPORT_ANNOUNCEMENT", "FILE_PATH");
        if (!genRef || !genRef.getRefValue1()) {
            return;
        }
        boolean isPodChange = false;
        boolean isObCarrierandDestChange = true;
        String filePath = "";

        Set efcs = inGroovyEvent.getEvent().getEvntFieldChanges();
        for (EventFieldChange efc : efcs) {
            if (eventType!=null && eventType.equals(UNIT_REROUTE) && ((ufv?.isTransitStateAtLeast(UfvTransitStateEnum.S40_YARD)))) {
                if ((inGroovyEvent.wasFieldChanged(UnitField.UNIT_RTG_GROUP.getFieldId())) && "YES".equals(TMF_EXEMPT)) {
                    //Do nothing
                } else {
                    isObCarrierandDestChange = false;
                    break;
                }

            } else if (efc.getMetafieldId() == UnitField.UNIT_RTG_POD1.getFieldId()) {
                RoutingPoint rtg = RoutingPoint.hydrate(efc.getNewVal().toLong());
                String podNew = rtg != null ? rtg.getPointUnlocId() : '';
                if (podNew != "USSPQ")
                    FILE_STATUS = "D";
                //isPodChange = true;
            }
        }

        LOGGER.debug("Preparing Pierpass for Unit -- ${unit.getUnitId()} with isObCarrierandDestChange ${isObCarrierandDestChange}")
        String exempStatus = "";
        if (ufv.getUfvIntendedObCv().getLocType() == LocTypeEnum.RAILCAR || ufv.getUfvIntendedObCv().getLocType() == LocTypeEnum.TRAIN) {
            exempStatus = "RE";
            FILE_STATUS = "R";
        } else
            exempStatus = "NE";
        ufv.setUfvFlexString05(exempStatus);
        if (!isObCarrierandDestChange) {
            return;
        }

        String pierPassMsg = "";
        String currTime = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String dateStr = currTime.substring(0, 8);
        String timeStr = currTime.substring(8, 14);

        filePath = genRef.getRefValue1() + currTime + "_" + unit.getUnitId() + ".cff";
        File annoucFile = new File(filePath);

        VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(ufv.getUfvActualIbCv());
        if (vvd == null){
            return;
        }

        Vessel vessel = vvd ? vvd.getVvdVessel() : null;
        String ISO = unit.getPrimaryEq().getEqEquipType().getEqtypId();
        String destination = unit.getUnitGoods().getGdsDestination() ? unit.getUnitGoods().getGdsDestination() : "";
        if (destination != null && !destination.isEmpty()) {
            destination = destination.replaceAll("[\\W+]", "")
        }
        String lineSCAC = unit.getUnitLineOperator().getBzuScac();

        String ediStartStr = FILE_STATUS + DELIMITER + SENDER_ID + DELIMITER + RECEIVER_ID;
        pierPassMsg += ediStartStr + DELIMITER + dateStr + DELIMITER + timeStr + "***" + FILE_TYPE + DELIMITER + MODE_CD + DELIMITER;
        pierPassMsg += vvd.getCarrierIbVoyNbrOrTrainId() + DELIMITER + lineSCAC + DELIMITER;
        pierPassMsg += TERMINAL_ID + DELIMITER + vessel.getVesLloydsId() + DELIMITER;
        pierPassMsg += vessel.getVesRadioCallSign() + DELIMITER + vessel.getVesName() + DELIMITER;
        pierPassMsg += DISCHARGE_PORT + DELIMITER + destination + DELIMITER + exempStatus + DELIMITER;
        pierPassMsg += unit.getUnitId() + DELIMITER + ISO + DELIMITER;
        pierPassMsg += "L" + DELIMITER + "\r\n";
        annoucFile.append(pierPassMsg);
    }


    public final String DELIMITER = "*";
    public final String SENDER_ID = "APLSPRD";
    public final String RECEIVER_ID = "PPTMF";
    public String FILE_STATUS = "R";
    public final String FILE_TYPE = "BAP-v1";
    public final String MODE_CD = "P";
    public final String TERMINAL_ID = "EMSSP";
    public final String DISCHARGE_PORT = "USLAX";
    public String UNIT_REROUTE = "UNIT_REROUTE";
}
