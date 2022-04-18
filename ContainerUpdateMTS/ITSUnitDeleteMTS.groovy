package ContainerUpdateMTS

import com.navis.argo.business.model.GeneralReference
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.inventory.business.units.Unit
import com.navis.services.business.event.GroovyEvent
import groovy.sql.Sql
import org.apache.log4j.Logger

/**
 * @author <a href="mailto:sramsamy@weservetech.com">Ramasamy S</a>, 14/Apr/2022
 *
 * This sends the real time container position details to MTS to sync the kalmar system
 *
 * Following are the genral notices configured
 * UNIT_RAMP
 * UNIT_LOAD
 * UNIT_OUT_GATE
 *
 * DeleteContainer MTS message
 * <containerDelete id="TEST1234567"/>
 *
 */
class ITSUnitDeleteMTS extends AbstractGeneralNoticeCodeExtension {

    public void execute(GroovyEvent paramGroovyEvent) {
        logMsg("ITSUnitDeleteMTS BEGIN");
        Unit unit = (Unit) paramGroovyEvent.getEntity();
        GeneralReference genRefDbConnection = GeneralReference.findUniqueEntryById(T_KALMAR, T_MTS);
        if (genRefDbConnection == null) {
            logMsg("MTS DB connection details missing, return");
            return;
        }
        String username = genRefDbConnection.getRefValue1();
        String password = genRefDbConnection.getRefValue2();
        String databaseName = T_JDBC_PREFIX + genRefDbConnection.getRefValue3();
        String packageName = genRefDbConnection.getRefValue4();

        try {
            String message = String.format(XML_MESSAGE, unit.getUnitId());

            Sql sql = Sql.newInstance(databaseName, username, password, DRIVER_NAME);
            String sqlstring = packageName + "(\'$MTS_GROOVY_NAME\' , \'$message\')";
            GString gsqlstring = GString.EMPTY + "{call " + sqlstring + "}";

            logMsg("MTS call: \n" + gsqlstring.toString());
            int sqlResult = sql.call(gsqlstring);
            if (sqlResult == 0) {
                logMsg("MTS SUCCESS : " + sqlResult)
            } else {
                logMsg("MTS FAILURE " + sqlResult)
            }

        } catch (Exception e) {
            logMsg("Exception while calling MTS : " + e.getMessage());
        }

        logMsg("ITSUnitDeleteMTS END");
    }


    private void logMsg(Object inMsg) {
        LOGGER.debug(inMsg);
    }

    private static final String T_JDBC_PREFIX = "jdbc:";
    private static final String DRIVER_NAME = "net.sourceforge.jtds.jdbc.Driver";
    private static final String T_KALMAR = "KALMAR";
    private static final String T_MTS = "MTS";

    private static final String MTS_GROOVY_NAME = "NavisUnitDeleteMTS";
    private String XML_MESSAGE = "<containerDelete id=\"%s\"/>\n";

    private static final Logger LOGGER = Logger.getLogger(ITSUnitDeleteMTS.class);
}
