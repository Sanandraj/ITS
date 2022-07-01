package ITSIntegration

import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.EdiMessageClassEnum
import com.navis.argo.business.atoms.LogicalEntityEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.edi.EdiEntity
import com.navis.edi.EdiField
import com.navis.edi.business.api.EdiExtractManager
import com.navis.edi.business.api.EdiFinder
import com.navis.edi.business.atoms.EdiMessageDirectionEnum
import com.navis.edi.business.entity.EdiMailbox
import com.navis.edi.business.entity.EdiSession
import com.navis.edi.business.entity.EdiTradingPartner
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.external.framework.util.ExtensionUtils
import com.navis.framework.business.Roastery
import com.navis.framework.business.atoms.LifeCycleStateEnum
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizViolation
import com.navis.inventory.business.units.Unit
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.reference.CancelReason
import com.navis.services.business.rules.EventType
import org.apache.commons.lang.StringUtils
import org.apache.commons.logging.Log
import org.apache.log4j.Level
import org.apache.log4j.Logger
import wslite.rest.ContentType
import wslite.rest.RESTClient
import org.jetbrains.annotations.Nullable

class ITSGateTranCancelFormCallback extends AbstractExtensionPersistenceCallback {

    private static final String MAP_KEY = 'gkeys';
    private final String GEN_REF_TYPE_GOS = "GOS"
    private final String GOS_CANCEL_TRAN_URL = "CancelTranUrl"

    @Override
    void execute(@Nullable Map inParms, @Nullable Map inOutResults) {
        LOG.setLevel(Level.DEBUG)
        LOG.info("Entered customBeanITSTranCancelFormController")
        if (inParms[MAP_KEY] != null) {
            LOG.info("Entered customBeanITSTranCancelFormController if")
            List<Serializable> gkeyList = inParms.get(MAP_KEY) as List;
            PersistenceTemplate pt = new PersistenceTemplate(getUserContext());

            pt.invoke(new CarinaPersistenceCallback() {
                @Override
                protected void doInTransaction() {
                    def url = ""
                    GeneralReference genRef = GeneralReference.findUniqueEntryById(GEN_REF_TYPE_GOS,GOS_CANCEL_TRAN_URL)
                    if(genRef != null){
                        url = genRef.getRefValue1()
                    }
                    if(StringUtils.isBlank(url)){
                        LOG.error("GOS Cancel Transaction Url is null")
                        return
                    }
                    for (Serializable evntGkey : gkeyList) {
                        try{
                            TruckTransaction ttran = (TruckTransaction) Roastery.getHibernateApi().load(TruckTransaction.class, evntGkey);
                            if(ttran != null && ttran.getTranStatus() == TranStatusEnum.CANCEL){
                                String cancelReason = ttran.getTranCancelReason() == null ? null : ttran.getTranCancelReason().getCrCode()
                                //url = StringUtils.replace(url,"{tranNbr}", ttran.getTranNbr())
                                url = url.replace("{tranNbr}",ttran.getTranNbr().toString())
                                LOG.info("GOS Cancel Transaction Url - " + url)
                                def client = new RESTClient(url)
                                client.setDefaultContentTypeHeader("application/json")
                                def response = client.put(connectTimeout: 5000,
                                        readTimeout: 20000){
                                    text "{\"status\":\"CL\",\"reason\":\""+ cancelReason +"\"}"

                                }
                                if(response != null && response.getStatusCode() == 204){
                                    LOG.info("Cancel Request to GOS Sent successfully")
                                }
                                else{
                                    LOG.error("Error received for tran- "+ttran.getTranNbr()+"StatusCode-"+response.getStatusCode())
                                }
                            }
                        }
                        catch(Exception ex){
                            LOG.error("Error occured sending cancel transaction to GOS",ex)
                        }

                    }
                }
            });
        }
    }
    private static final Logger LOG = Logger.getLogger(this.class)
}
