import com.navis.ecn4.xmlrdt.states.IStateContext
import com.navis.ecn4.xmlrdt.states.StateId
import com.navis.ecn4.xmlrdt.states.conditions.AbstractStateTransitionCondition
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.NotNull;


/**
 * @author <a href="mailto:sramasamy@weservetech.com"> Ramasamy Sathappan</a>
 * @since 19-Jul-2022
 * To accept the container lift request, even if the associated job is not in the job-list
 * */
class IsCheAcceptNonListedJobCondition extends AbstractStateTransitionCondition {

    @Override
    boolean accept(@NotNull StateId stateId, @NotNull StateId stateId1, @NotNull IStateContext iStateContext) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("IsCheAcceptNonListedJobCondition :: Start")
        return true;
    }

    private static final Logger LOGGER = Logger.getLogger(IsCheAcceptNonListedJobCondition.class);
}
