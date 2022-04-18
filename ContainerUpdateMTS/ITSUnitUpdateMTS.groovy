package ContainerUpdateMTS

import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.LocPosition
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.appointment.model.AppointmentTimeSlot
import com.navis.road.business.appointment.model.GateAppointment
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.spatial.business.model.AbstractBin
import com.navis.xpscache.business.atoms.EquipBasicLengthEnum
import groovy.sql.Sql
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.text.SimpleDateFormat

/**
 *
 * @author <a href="mailto:sramsamy@weservetech.com">Ramasamy S</a>, 14/Apr/2022
 *
 * Following are the genral notices configured
 * UNIT_POSITION_CORRECTION
 * UNIT_YARD_MOVE
 * UNIT_RECEIVE
 * UNIT_DERAMP
 * UNIT_IN_VESSEL
 * UNIT_RAMP
 * UNIT_OUT_GATE
 * UNIT_LOAD
 * UNIT_DISCH
 *
 *
 * This sends the real time container position details to MTS to sync the kalmar system
 * MTS example of updateContainer message
 *
 * <containerUpdate>
 * 	<container id="TEST1234567">
 * 		<length>40</length>
 * 		<weight>%</weight>
 * 		<height>%</height>
 * 		<loadStatus>%</loadStatus>
 * 		<chassisPosition>2</chassisPosition>
 * 	<appointment>
 *    <from>2022-04-11T06:00:00 -0700</from>
 *    <to>2022-04-11T06:29:00 -0700</to>
 *  </appointment>
 * 	</container>
 * 	<position>
 * 		<type>Grounded</type>
 * 		<row>2A</row>
 * 		<bay>06</bay>
 * 		<cell>B</cell>
 * 		<tier>1</tier>
 * 		<slot></slot>
 * 	</position>
 * </containerUpdate>
 *
 * ******************************
 * Wheeled position looks like below
 *
 * 	<position>
 * 		<type>Wheeled</type>
 * 		<row>31192</row>
 * 		<bay></bay>
 * 		<cell></cell>
 * 		<tier></tier>
 * 		<slot>A</slot>
 * 	</position>
 */

class ITSUnitUpdateMTS extends AbstractGeneralNoticeCodeExtension {

    public void execute(GroovyEvent inGroovyEvent) {
        try {
            LOGGER.setLevel(Level.DEBUG);
            logMsg("ITSUnitUpdateMTS BEGIN");
            Unit unit = (Unit) inGroovyEvent.getEntity();

            Event event = inGroovyEvent.getEvent();
            if ( UNIT_DISCH == event.getEventTypeId() && event.getEvntAppliedBy().startsWith(USER_KALMAR) ) {
                logMsg("If the event performed by PDS, return");
                return;
            }

            if (unit == null) {
                logMsg("Unit not available, return");
                return;
            }

            GeneralReference genRefDbConnection = GeneralReference.findUniqueEntryById(T_KALMAR, T_MTS);
            if (genRefDbConnection == null) {
                logMsg("MTS DB connection details missing, return");
                return;
            }
            String username = genRefDbConnection.getRefValue1();
            String password = genRefDbConnection.getRefValue2();
            String databaseName = T_JDBC_PREFIX + genRefDbConnection.getRefValue3();
            String packageName = genRefDbConnection.getRefValue4();

            String unitID = unit.getUnitId();
            //logMsg("unitID " + unitID);

            String length;
            if (EquipBasicLengthEnum.BASIC40.equals(unit.getBasicLength()))
                length = T_40;
            else
                length = T_20;

            String groupCode = T_PERCENTAGE;
            if (unit.getUnitRouting() && unit.getUnitRouting().getRtgGroup() && unit.getUnitRouting().getRtgGroup().getGrpId()) {
                groupCode = unit.getUnitRouting().getRtgGroup().getGrpId();
            }

            String unitChassisPos = T_PERCENTAGE;
            LocPosition position = unit.findCurrentPosition();
            String positionId = position.toString();
            //String positionType = position.getPosLocId();
            String positionLoc = T_EMPTY;
            if (LocTypeEnum.YARD.equals(position.getPosLocType())) {
                positionLoc = T_Y;
            } else {
                logMsg("not in the yard, return");
                return;
            }

            String posGrounded = T_EMPTY;
            String posBlock = T_EMPTY;
            String posSlot = T_EMPTY;
            String posRow = T_EMPTY;
            String posCell = T_EMPTY;
            String posTier = T_EMPTY;
            String posLocation = T_EMPTY;

            String[] tokens = positionId.split(DELIMITER);
            String token2 = tokens.size() > 2 ? tokens[2] : T_EMPTY;
            if (position.isWheeled()) {
                posGrounded = T_WHEELED;
                if ((token2.length()) < 5) {
                    posLocation = token2; //wheeled
                } else {
                    posBlock = token2.substring(0, (token2.length() - 2));
                    posSlot = token2.substring((token2.length() - 2), (token2.length()));
                }
            }
            if (position.isGrounded()) {
                posGrounded = T_GROUNDED;
                if ((token2.length()) < 5) {
                    posLocation = token2; //grounded
                } else {
                    posBlock = position.getBlockName();
                    AbstractBin abstractStackBin = position.getPosBin();
                    if (abstractStackBin != null) {
                        logMsg("stackBin id : " + abstractStackBin.getAbnBinType().getBtpId())
                        if (abstractStackBin.getAbnBinType() != null && T_ABM_STACK.equalsIgnoreCase(abstractStackBin.getAbnBinType().getBtpId())) {
                            String staBinName = abstractStackBin.getAbnName()
                            AbstractBin sectionBin = abstractStackBin.getAbnParentBin();
                            if (sectionBin != null && T_ABM_SECTION.equalsIgnoreCase(sectionBin.getAbnBinType()?.getBtpId())) {
                                String secBinName = sectionBin.getAbnName()
                                posRow = secBinName;
                                posCell = staBinName.substring(staBinName.indexOf(secBinName) + secBinName.size())
                                long tier = abstractStackBin.getTierIndexFromInternalSlotString(position.getPosSlot());
                                posTier = abstractStackBin.getTierName(tier)
                                AbstractBin blockBin = sectionBin.getAbnParentBin();
                                if (blockBin != null && T_ABM_BLOCK.equalsIgnoreCase(blockBin.getAbnBinType().getBtpId())) {
                                    String bloBinName = blockBin.getAbnName()
                                    posBlock = bloBinName;
                                    posRow = secBinName.substring(secBinName.indexOf(bloBinName) + bloBinName.size());
                                }
                            }
                        } else if (abstractStackBin.getAbnBinType() != null && T_ABM_BLOCK.equalsIgnoreCase(abstractStackBin.getAbnBinType().getBtpId())) {
                            posRow = abstractStackBin.getAbnName()
                            posTier = position.getPosTier() != null ? position.getPosTier() : ""
                        } else {
                            posRow = token2.substring((token2.length() - 4), (token2.length() - 2))
                            posCell = token2.substring((token2.length() - 2), (token2.length() - 1));
                            posTier = position.getPosTier();
                        }
                    }
                }
            }
            if (posGrounded == T_EMPTY) {
                logMsg("not grounded or wheeled, return");
                return;
            }
            String fromDate = T_EMPTY;
            String toDate = T_EMPTY;
            UnitFacilityVisit ufv = unit.getUnitActiveUfvNowActive()
            Long ufvGapptNbr = ufv?.getUfvGapptNbr()
            if (ufvGapptNbr != null && ufv?.getUfvDlvTimeAppntmnt() != null) {
                GateAppointment gateAppointment = GateAppointment.findGateAppointment(ufvGapptNbr)
                AppointmentTimeSlot slot = gateAppointment?.getGapptTimeSlot()
                SimpleDateFormat df = new SimpleDateFormat(DATE_FORMAT);
                Date startDate = slot?.getAtslotStartDate()
                Date endDate = slot?.getAtslotEndDate()
                fromDate = df.format(startDate);
                toDate = df.format(endDate);
            }

            //logMsg("MTS DB connection details :: "+"jdbc_connection=" + databaseName + ", user =" + username + ", package=" + packageName);
            String message = String.format(XML_MESSAGE, unitID, length, unitChassisPos, groupCode, fromDate, toDate, posGrounded, posBlock, posRow, posCell, posTier, posSlot, posLocation);
            //logMsg("message: "+message);

            Sql sql = Sql.newInstance(databaseName, username, password, DRIVER_NAME);
            String sqlStatement = packageName + "(\'$MTS_GROOVY_NAME\' , \'$message\')";
            GString gString = GString.EMPTY + "{call " + sqlStatement + "}";
            logMsg("gString : " + gString.toString());

            int result = sql.call(gString);
            logMsg("result : " + result)

        } catch (Exception e) {
            LOGGER.error("Exception while calling MTS : " + e.getMessage());
        }

        logMsg("ITSUnitUpdateMTS END");
    }


    private void logMsg(Object inMsg) {
        LOGGER.debug(inMsg);
    }

    private static final String UNIT_DISCH = "UNIT_DISCH";
    private static final String USER_KALMAR = "xps:ECN4";

    private static final String DELIMITER = "[-]";
    private static final String T_40 = "40";
    private static final String T_20 = "20";
    private static final String T_Y = "Y";
    private static final String T_PERCENTAGE = "%";
    private static final String T_EMPTY = "";
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    private static final String T_JDBC_PREFIX = "jdbc:";
    private static final String DRIVER_NAME = "net.sourceforge.jtds.jdbc.Driver";
    private static final String T_WHEELED = "Wheeled";
    private static final String T_GROUNDED = "Grounded";
    private static final String T_ABM_STACK = "ABM_STACK";
    private static final String T_ABM_SECTION = "ABM_SECTION";
    private static final String T_ABM_BLOCK = "ABM_BLOCK";

    private static final String T_KALMAR = "KALMAR";
    private static final String T_MTS = "MTS";

    private static final String MTS_GROOVY_NAME = "NavisUnitUpdateMTS";

    private String XML_MESSAGE = "<containerUpdate>\n" +
            "<container id=\"%s\">\n" +
            "<length>%s</length>\n" +
            "<weight>%%</weight>\n" +
            "<height>%%</height>\n" +
            "<loadStatus>%%</loadStatus>\n" +
            "<chassisPosition>%s</chassisPosition>\n" +
            "<qcCustom1>%s</qcCustom1>\n" +
            "<appointment>\n" +
            "<from>%s</from>\n" +
            "<to>%s</to>\n" +
            "</appointment>\n" +
            "</container>\n" +
            "<position>\n" +
            "<type>%s</type>\n" +
            "<row>%s</row>\n" +
            "<bay>%s</bay>\n" +
            "<cell>%s</cell>\n" +
            "<tier>%s</tier>\n" +
            "<slot>%s</slot>\n" +
            "<location>%s</location>\n" +
            "</position>\n" +
            "</containerUpdate>";

    private static final Logger LOGGER = Logger.getLogger(ITSUnitUpdateMTS.class);
}