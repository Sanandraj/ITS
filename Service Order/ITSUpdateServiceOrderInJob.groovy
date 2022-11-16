/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.ArgoExtractEntity
import com.navis.argo.ArgoExtractField
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.ServiceOrderStatusEnum
import com.navis.argo.business.atoms.ServiceOrderTypeEnum
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.model.GeneralReference
import com.navis.external.argo.AbstractGroovyJobCodeExtension
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.orders.OrdersField
import com.navis.orders.business.serviceorders.ServiceOrder
import com.navis.util.concurrent.NamedThreadFactory
import org.apache.log4j.Level
import org.apache.log4j.Logger

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/*
*
*  @Author <ahref="mailto:mharikumar@weservetech.com"  >  Harikumar M</a>,
*  Date : 17/Oct/2022
*  Requirements : This groovy is used to service order with last draft nbr or invoice number from CUE.
*  @Inclusion Location : Incorporated as a code extension of the type GROOVY_JOB_CODE_EXTENSION. Copy -->Paste this code(ITSUpdateServiceOrderInJob.groovy)
*  @Set up Groovy Job to execute it, and configure this code- ITSUpdateServiceOrderInJob.
*
*/


class ITSUpdateServiceOrderInJob extends AbstractGroovyJobCodeExtension {

    public static Logger LOGGER = Logger.getLogger(ITSUpdateServiceOrderInJob.class)


    @Override
    void execute(Map parameters) throws Exception {

        //LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSUpdateServiceOrderInJob - BEGIN :")
        DomainQuery dq = QueryUtils.createDomainQuery("ServiceOrder")
        dq.addDqPredicate(PredicateFactory.eq(OrdersField.SRVO_SUB_TYPE, ServiceOrderTypeEnum.SRVO))
        dq.addDqPredicate(PredicateFactory.in(OrdersField.SRVO_STATUS, status))
        Serializable[] serviceOrderGkeys = HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq)
        LOGGER.debug("ITSUpdateServiceOrderInJob - serviceOrderGkeys  :" + serviceOrderGkeys)
        if (serviceOrderGkeys != null) {
            int threadCount = 5;
            GeneralReference generalReference = GeneralReference.findUniqueEntryById("SERVICEORDER", "THREADCOUNT")
            if (generalReference != null) {

                threadCount = generalReference != null ? generalReference.getRefValue1().toInteger() : 5;
            }

            ThreadFactory threadFactory = new NamedThreadFactory(Executors.defaultThreadFactory(), "SERVICE_ORDER_JOB");
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount, threadFactory);
            Serializable[] primaryKeyInBatch;
            UserContext userContext = ContextHelper.getThreadUserContext();
            LOGGER.debug("Current User Context :: " + userContext)
            for (int j = 0; j < serviceOrderGkeys.size(); j = j + threadCount) {
                primaryKeyInBatch = (Serializable[]) Arrays.copyOfRange(serviceOrderGkeys, j, j + threadCount);
                LOGGER.debug("Current fetched primary keys : $primaryKeyInBatch :: during run :: $j");

                for (int i = 0; i < threadCount; i++) {

                    LOGGER.debug("Current record for the service order checkinng :: $i the Pkey in the batch :: " + primaryKeyInBatch[i]);
                    if (primaryKeyInBatch[i] != null) {
                        Serializable primaryKey = primaryKeyInBatch[i];
                        executorService.submit(new Callable() {
                            private static final Logger LOGGER1 = Logger.getLogger(this.class)

                            @Override
                            Object call() throws Exception {
                                ServiceOrder serviceOrder
                                PersistenceTemplate persistenceTemplate = new PersistenceTemplate(userContext);
                                persistenceTemplate.invoke(new CarinaPersistenceCallback() {
                                    @Override
                                    protected void doInTransaction() {
                                        LOGGER1.debug("Fetching Service Order for Primary Key :: " + primaryKey); ;
                                        serviceOrder = (ServiceOrder) HibernateApi.getInstance().get(ServiceOrder.class, primaryKey);
                                        String lastInvoiceDraftNbr = null
                                        Boolean cueStatus = false
                                        LOGGER1.debug("Current Service Order :: $serviceOrder");
                                        List<ChargeableUnitEvent> cueList = serviceOrder != null ? findChargeableUnitEventByServiceOrderNbr(serviceOrder?.getSrvoNbr()) : null
                                        LOGGER1.debug("ITSUpdateServiceOrderInJob - cueList  :" + cueList)
                                        if (cueList != null && !cueList.isEmpty()) {
                                            for (ChargeableUnitEvent cue : cueList) {
                                                LOGGER1.debug("cue: " + cue)
                                                if (cue != null) {
                                                    LOGGER1.debug("ITSUpdateServiceOrderInJob - cue.getBexuLastDraftInvNbr()  :" + cue?.getBexuLastDraftInvNbr() + " is updated for Service Order Nbr :" + serviceOrder.getSrvoNbr())
                                                    LOGGER1.debug("CUE status::" + cue?.getStatus())
                                                    if ("INVOICED".equalsIgnoreCase(cue?.getStatus()) || "DRAFT".equalsIgnoreCase(cue?.getStatus())) {
                                                        lastInvoiceDraftNbr = cue?.getBexuLastDraftInvNbr()
                                                        LOGGER1.debug("Invoice recorded" + lastInvoiceDraftNbr)
                                                        if ("INVOICED".equalsIgnoreCase(cue?.getStatus())) {
                                                            cueStatus = true
                                                            LOGGER1.debug("cuestatus::" + cueStatus.toString())
                                                        }
                                                    }
                                                }
                                            }
                                            if (lastInvoiceDraftNbr != null) {
                                                serviceOrder.setFieldValue(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_Invoiced"), lastInvoiceDraftNbr)
                                                if (cueStatus) {
                                                    serviceOrder.setFieldValue(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_InvoiceFinalised"), true)
                                                } else {
                                                    serviceOrder.setFieldValue(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_InvoiceFinalised"), false)
                                                }

                                            } else {
                                                serviceOrder.setFieldValue(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_Invoiced"), lastInvoiceDraftNbr)
                                                serviceOrder.setFieldValue(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_InvoiceFinalised"), false)
                                            }
                                            HibernateApi.getInstance().save(serviceOrder)
                                        }
                                    }
                                })

                                return serviceOrder;
                            }
                        })
                    }
                }
                HibernateApi.getInstance().flush()
            }
        }
        LOGGER.debug("ITSUpdateServiceOrderInJob - END")
    }


    private static List<ChargeableUnitEvent> findChargeableUnitEventByServiceOrderNbr(String inServiceOrderNbr) {

        DomainQuery query = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT)
        query.addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_SERVICE_ORDER, inServiceOrderNbr))
        return HibernateApi.getInstance().findEntitiesByDomainQuery(query)
    }

    private static final List<ServiceOrderStatusEnum> status = new ArrayList()
    static {
        status.add(ServiceOrderStatusEnum.INPROGRESS)
        status.add(ServiceOrderStatusEnum.COMPLETED)
    }

    private static final String INVOICED = "INVOICED"
    private static final String DRAFT = "DRAFT"


}

