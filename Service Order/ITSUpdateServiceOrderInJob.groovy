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
import com.navis.framework.util.DateUtil
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
*@Author: mailto:mharikumar@weservetech.com, Harikumar M; Date: 17/10/2022
*
*  Requirements: This groovy is used to service order with last draft nbr or invoice number from CUE.
*
*  @Inclusion Location: Incorporated as a code extension of the type
*
*  Load Code Extension to N4:
*  1. Go to Administration --> System --> Code Extensions
*  2. Click Add (+)
*  3. Enter the values as below:
*     Code Extension Name: ITSUpdateServiceOrderInJob
*     Code Extension Type: GROOVY_JOB_CODE_EXTENSION
*     Groovy Code: Copy and paste the contents of groovy code.
*  4. Click Save button
*
* @Set up Groovy Job to execute it, and configure this code- ITSUpdateServiceOrderInJob.
*
*  S.No    Modified Date   Modified By     Jira      Description
*
*/


class ITSUpdateServiceOrderInJob extends AbstractGroovyJobCodeExtension {

    public static Logger LOGGER = Logger.getLogger(ITSUpdateServiceOrderInJob.class)


    @Override
    void execute(Map parameters) throws Exception {
        //LOGGER.setLevel(Level.WARN)
        LOGGER.warn("ITSUpdateServiceOrderInJob - BEGIN :")
        DomainQuery dq = QueryUtils.createDomainQuery("ServiceOrder")
        dq.addDqPredicate(PredicateFactory.eq(OrdersField.SRVO_SUB_TYPE, ServiceOrderTypeEnum.SRVO))
        dq.addDqPredicate(PredicateFactory.in(OrdersField.SRVO_STATUS, status))
        //.addDqPredicate(PredicateFactory.eq(OrdersField.SRVO_CREATED,getTodayDate()))
        Serializable[] serviceOrderGkeys = HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq)
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

            for (int j = 0; j < serviceOrderGkeys.size(); j = j + threadCount) {
                primaryKeyInBatch = (Serializable[]) Arrays.copyOfRange(serviceOrderGkeys, j, j + threadCount);

                for (int i = 0; i < threadCount; i++) {

                    if (primaryKeyInBatch[i] != null) {
                        Serializable primaryKey = primaryKeyInBatch[i];
                        executorService.submit(new Callable() {


                            @Override
                            Object call() throws Exception {
                                ServiceOrder serviceOrder
                                PersistenceTemplate persistenceTemplate = new PersistenceTemplate(userContext);
                                persistenceTemplate.invoke(new CarinaPersistenceCallback() {

                                    @Override
                                    protected void doInTransaction() {

                                        serviceOrder = (ServiceOrder) HibernateApi.getInstance().get(ServiceOrder.class, primaryKey);
                                        String lastInvoiceDraftNbr = null
                                        String finalInvNbr = null
                                        Boolean cueStatus = false

                                        List<ChargeableUnitEvent> cueList = serviceOrder != null ? findChargeableUnitEventByServiceOrderNbr(serviceOrder?.getSrvoNbr()) : null

                                        if (cueList != null && !cueList.isEmpty()) {
                                            for (ChargeableUnitEvent cue : cueList) {

                                                if (cue != null) {
                                                    if ("INVOICED".equalsIgnoreCase(cue?.getStatus()) || "DRAFT".equalsIgnoreCase(cue?.getStatus())) {
                                                        lastInvoiceDraftNbr = cue?.getBexuLastDraftInvNbr()

                                                        if ("INVOICED".equalsIgnoreCase(cue?.getStatus()) && cue?.getBexuFlexString04() != null) {
                                                            cueStatus = true
                                                            finalInvNbr = cue?.getBexuFlexString04()

                                                        }
                                                    }
                                                }
                                            }

                                            if(cueStatus){
                                                serviceOrder.setFieldValue(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_Invoiced"), lastInvoiceDraftNbr)
                                                serviceOrder.setFieldValue(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_FinalInvNbr"), finalInvNbr)
                                                serviceOrder.setFieldValue(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_InvoiceFinalised"), true)
                                            } else {
                                                if (lastInvoiceDraftNbr != null){
                                                    serviceOrder.setFieldValue(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_Invoiced"), lastInvoiceDraftNbr)
                                                }else {
                                                    serviceOrder.setFieldValue(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_Invoiced"), null)
                                                }
                                                serviceOrder.setFieldValue(MetafieldIdFactory.valueOf("srvoCustomFlexFields.srvoCustomDFF_FinalInvNbr"), null)
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
        LOGGER.warn("ITSUpdateServiceOrderInJob - END")
    }


    private static List<ChargeableUnitEvent> findChargeableUnitEventByServiceOrderNbr(String inServiceOrderNbr) {

        DomainQuery query = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT)
        query.addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_SERVICE_ORDER, inServiceOrderNbr))
        return HibernateApi.getInstance().findEntitiesByDomainQuery(query)
    }

    private Date getTodayDate() {
        TimeZone tz = ContextHelper.getThreadUserTimezone();
        LOGGER.debug("timezone: "+tz+"date: "+ DateUtil.getTodaysDate(tz))
        return DateUtil.getTodaysDate(tz)
    }

    private static final List<ServiceOrderStatusEnum> status = new ArrayList()
    static {
        status.add(ServiceOrderStatusEnum.INPROGRESS)
        status.add(ServiceOrderStatusEnum.COMPLETED)
    }

    private static final String INVOICED = "INVOICED"
    private static final String DRAFT = "DRAFT"


}


