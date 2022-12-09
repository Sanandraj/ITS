import com.navis.argo.business.model.GeneralReference
import com.navis.argo.util.XmlUtil
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.model.GateLane
import com.navis.road.business.model.TruckTransaction
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jdom.Element
import org.jdom.filter.ElementFilter
import wslite.rest.RESTClient
import org.jetbrains.annotations.Nullable

class ITSRoadPrintFormCallBack extends AbstractExtensionPersistenceCallback {

    private static final String MAP_KEY = 'gkeys';
    private final String GEN_REF_TYPE_GOS = "GOS"
    private final String GOS_REPRINT_DOC_URL = "ReprintDocUrl"
    private final String LANE_GKEY = "lanegkey"

    @Override
    void execute(@Nullable Map inParms, @Nullable Map inOutResults) {
        LOG.setLevel(Level.DEBUG)
        LOG.debug("Entered ITSRoadPrintFormCallBack")
        if (inParms["lane"] != null) {
            //LOG.info("Entered customBeanITSTranCancelFormController if")
            String laneKey = inParms.get("lane");
            String xmlDocument = inParms.get("document");
            String tranNbr = inParms.get("tranNbr");
            String docType = inParms.get("docType");
            LOG.debug("Lane value inside RoadPrintFormCallBack - " + laneKey)
            LOG.debug("Document inside RoadPrintFormCallBack - " + xmlDocument)
            LOG.debug("TranNbr inside RoadPrintFormCallBack - " + tranNbr)
            PersistenceTemplate pt = new PersistenceTemplate(getUserContext());

            pt.invoke(new CarinaPersistenceCallback() {
                @Override
                protected void doInTransaction() {
                    def url = ""
                    GeneralReference genRef = GeneralReference.findUniqueEntryById(GEN_REF_TYPE_GOS,GOS_REPRINT_DOC_URL)
                    if(genRef != null){
                        url = genRef.getRefValue1()
                    }
                    if(StringUtils.isBlank(url)){
                        LOG.error("Reprint Transaction Url is null")
                        return
                    }
                    try{
                        if(StringUtils.isNotBlank(xmlDocument)){
                            //Serializable laneKey = inParms.get(LANE_GKEY)
                            GateLane gateLane = laneKey == null ? null : GateLane.loadLaneByPrimaryKey(laneKey)
                            String lane = gateLane == null ? "" : gateLane.getLaneId()
                            xmlDocument = xmlDocument.replace("\"","\\\"")
                            //String printTicket = "y"
                            //url = StringUtils.replace(url,"{tranNbr}", ttran.getTranNbr())
                            url = url.replace("{tranNbr}",tranNbr)
                            LOG.info("Reprint Transaction Url - " + url)
                            LOG.debug("Reprint json request - " + "{\"document\":\""+ xmlDocument +"\",\"lane\":\""+lane+"\",\"docType\":\""+ docType +"\"}")
                            def client = new RESTClient(url)
                            client.setDefaultContentTypeHeader("application/json")
                            def response = client.post(connectTimeout: 5000,
                                    readTimeout: 20000){
                                text "{\"document\":\""+ xmlDocument +"\",\"lane\":\""+lane+"\",\"docType\":\""+ docType +"\"}"

                            }
                            if(response != null && response.getStatusCode() == 204){
                                LOG.info("Reprint request to GOS Sent successfully")
                            }
                            else{
                                LOG.error("Error received for tran- "+tranNbr+"StatusCode-"+response.getStatusCode())
                            }
                        }
                    }
                    catch(Exception ex){
                        LOG.error("Error occured sending Reprint document request to GOS",ex)
                    }
                }
            });

        }

    }
    private static final Logger LOG = Logger.getLogger(this.class)
}

