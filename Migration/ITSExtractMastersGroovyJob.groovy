import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.external.argo.AbstractGroovyJobCodeExtension
import com.navis.external.framework.util.ExtensionUtils
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import org.apache.log4j.Logger

/**
 * Extractor Job base code
 */
class ITSExtractMastersGroovyJob extends AbstractGroovyJobCodeExtension {

    @Override
    void execute(Map<String, Object> inParams) {

        try {
            PersistenceTemplate pt = new PersistenceTemplate(getUserContext())
            pt.invoke(new CarinaPersistenceCallback() {
                protected void doInTransaction() {
                  /*  def extractor = new GroovyApi().getGroovyClassInstance("ITSVesselVisitExtractor")
                    extractor.execute()*/

                    def extension = ExtensionUtils.getLibrary(ContextHelper.getThreadUserContext(), "ITSVesselVisitExtractor");
                    extension.execute()
                }
            })
        } catch (Exception e) {
            log.warn("Exception occurred while executing ITSExtractMastersGroovyJob")
        }

    }

    private static final Logger log = Logger.getLogger(this.class)
}
