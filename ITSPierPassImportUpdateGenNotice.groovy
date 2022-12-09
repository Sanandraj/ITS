/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.RoutingPoint
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
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
     * @Author <a href="mailto:sanandaraj@servimostech.com">S Anandaraj</a>, 15/JUL/2022
     *
     * Requirements : 4-13 -- Group release & Unit pier pass Exempt Certain Group codes provide Exemption from TMF –The group code should send EDI request to Pier Pass for 315 EDI release.
     *
     * @Inclusion Location	: Incorporated as a code extension of the type GENERAL_NOTICE_CODE_EXTENSION.
     *
     *  Load Code Extension to N4:
            1. Go to Administration --> System -->  Code Extension
            2. Click Add (+)
            3. Enter the values as below:
                Code Extension Name:  ITSPierPassImportUpdateGenNotice
                Code Extension Type:  GENERAL_NOTICE_CODE_EXTENSION
               Groovy Code: Copy and paste the contents of groovy code.
            4. Click Save button

    * @Set up General Notice for event type "UNIT_REROUTE" on Unit Entity then execute this code extension (ITSPierPassImportUpdateGenNotice).
    *
    *  S.No    Modified Date   Modified By     Jira      Description
    *
 */


public class ITSPierPassImportUpdateGenNotice extends AbstractGeneralNoticeCodeExtension {
    private static Logger LOGGER = Logger.getLogger(ITSPierPassImportUpdateGenNotice.class)

    public void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.INFO)
        String eventType = inGroovyEvent?.getEvent()?.getEventTypeId()
        Unit unit = inGroovyEvent.getEntity()
        if (!unit) {
            return;
        }
        UnitFacilityVisit ufv = unit?.getUnitActiveUfv()
        if (!ufv) {
            return;
        }

        if (!UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()) ||
                FreightKindEnum.MTY.equals(unit.getUnitFreightKind())) {
            return;
        }

        String TMF_EXEMPT = unit.getUnitRouting()?.getRtgGroup()?.getGrpFlexString01()
        GeneralReference genRef = GeneralReference.findUniqueEntryById("PIERPASS", "IMPORT_ANNOUNCEMENT", "FILE_PATH");
        if (!genRef || !genRef.getRefValue1()) {
            return;
        }
        boolean isObCarrierandDestChange = false;
        String filePath = "";

        Set efcs = inGroovyEvent.getEvent()?.getEvntFieldChanges();
        for (EventFieldChange efc : efcs) {
            if (eventType != null && UNIT_REROUTE.equals(eventType) && ((ufv.isTransitStateAtLeast(UfvTransitStateEnum.S40_YARD)))) {
                if ((inGroovyEvent.wasFieldChanged(UnitField.UNIT_RTG_GROUP.getFieldId())) && "YES".equals(TMF_EXEMPT)) {
                    isObCarrierandDestChange = true;
                } else {
                    isObCarrierandDestChange = false;
                    break;
                }

            } else if (UnitField.UNIT_RTG_POD1.getFieldId().equals(efc.getMetafieldId())) {
                RoutingPoint rtg = RoutingPoint.hydrate(efc.getNewVal().toLong());
                String podNew = rtg != null ? rtg.getPointUnlocId() : '';
                if (!"USSPQ".equals(podNew))
                    FILE_STATUS = "D";
            }
        }

        String exempStatus = "";
        if (LocTypeEnum.RAILCAR.equals(ufv.getUfvIntendedObCv()?.getLocType()) || LocTypeEnum.TRAIN.equals(ufv.getUfvIntendedObCv()?.getLocType())) {
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
        if (vvd == null) {
            return;
        }

        Vessel vessel = vvd ? vvd.getVvdVessel() : null;
        String ISO = unit.getPrimaryEq()?.getEqEquipType()?.getEqtypId();
        String destination = unit.getUnitGoods()?.getGdsDestination() != null ? unit.getUnitGoods()?.getGdsDestination() : "";
        if (destination != null && !destination.isEmpty()) {
            destination = destination.replaceAll("[\\W+]", "")
        }
        String lineSCAC = unit.getUnitLineOperator()?.getBzuScac();

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
