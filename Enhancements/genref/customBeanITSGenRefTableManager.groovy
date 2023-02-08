
import com.navis.argo.ArgoEntity
import com.navis.argo.ArgoField
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.security.ArgoSecRole
import com.navis.external.framework.beans.EBean
import com.navis.framework.SecurityField
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.*
import com.navis.framework.portal.query.DataQuery
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.NullDataQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.framework.presentation.view.DefaultSharedUiTableManager
import com.navis.framework.query.common.api.QueryResult
import com.navis.framework.util.internationalization.TranslationUtils
import org.apache.log4j.Logger

/**
 * @Author: uaarthi@weservetech.com; Date: 02-02-2023
 *
 *  Requirements:
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: customBeanITSGenRefTableManager
 *     Code Extension Type:
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 */

class customBeanITSGenRefTableManager extends DefaultSharedUiTableManager implements EBean {
    @Override
    String getDetailedDiagnostics() {
        return "customBeanITSGenRefTableManager"
    }


    @Override
    DataQuery createQuery() {
        UserContext context = FrameworkPresentationUtils.getUserContext()

        DomainQuery dq = QueryUtils.createDomainQuery(ArgoEntity.GENERAL_REFERENCE)
        if (context != null && context.getUserGkey() != null) {
            Serializable key = context.getUserGkey()
            if (key == 1) {//admin
                return dq
            }
            QueryResult result = getUserRoleList(context, key)
            if (result.getTotalResultCount() > 0) {
                List<Long> roleList = (List<Long>) result.getValue(0, MetafieldIdFactory.valueOf("buserRoleList"))
                List<GeneralReference> genRefList = getGeneralReferenceList(true, roleList)
                List<String> argoSecRoleList = getGeneralReferenceList(false, roleList)

                if (argoSecRoleList != null && !argoSecRoleList.isEmpty() && genRefList != null && !genRefList.isEmpty()) {

                    if (argoSecRoleList != null && !argoSecRoleList.isEmpty()) {
                        List<String> approvedList = new ArrayList<>()
                        for (GeneralReference genRef : genRefList) {
                            if (doesRoleMatch(genRef, argoSecRoleList)) {
                                approvedList.add(genRef.getRefId2())

                            }
                        }
                        if (approvedList != null && !approvedList.isEmpty()) {
                            dq.addDqPredicate(PredicateFactory.in(ArgoField.REF_ID1, approvedList))
                            return dq
                        }

                    }
                }

            }
        }
        logger.warn("dq " + dq)
        return NullDataQuery.create();
    }

    List getGeneralReferenceList(boolean isGenRef, existRoleList) {
        List<GeneralReference> generalReferenceList = new ArrayList<>()
        List<String> argoSecRoleList = new ArrayList<>()

        DomainQuery query = QueryUtils.createDomainQuery(ArgoEntity.GENERAL_REFERENCE)
                .addDqPredicate(PredicateFactory.eq(ArgoField.REF_ID1, "GEN_REF_MANAGEMENT"))

        DomainQuery roleQuery = QueryUtils.createDomainQuery("ArgoSecRole")
                .addDqPredicate(PredicateFactory.in(SecurityField.ROLE_GKEY, existRoleList))

        PersistenceTemplate persistenceTemplate = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext());
        persistenceTemplate.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                if (isGenRef) {
                    generalReferenceList = HibernateApi.getInstance().findEntitiesByDomainQuery(query)
                }


                List<ArgoSecRole> roleList = (List<ArgoSecRole>) HibernateApi.getInstance().findEntitiesByDomainQuery(roleQuery)
                logger.warn("roleList " + roleList)
                argoSecRoleList = roleList.stream().collect { it.getRoleSecName() }
                logger.warn("argoSecRoleList " + argoSecRoleList)

            }
        })
        if (isGenRef) {
            return generalReferenceList
        } else {
            return argoSecRoleList
        }
    }

    boolean doesRoleMatch(genRef, List<String> userRoleList) {
        List<String> refList = new ArrayList<>()
        if (genRef != null) {
            if (genRef.getRefValue1() != null) {
                refList.addAll(genRef.getRefValue1().split(','))
            }
            if (genRef.getRefValue2() != null) {
                refList.addAll(genRef.getRefValue2().split(','))
            }
            if (genRef.getRefValue3() != null) {
                refList.addAll(genRef.getRefValue3().split(','))
            }
            if (genRef.getRefValue4() != null) {
                refList.addAll(genRef.getRefValue4().split(','))
            }
            if (genRef.getRefValue5() != null) {
                refList.addAll(genRef.getRefValue5().split(','))
            }
            if (genRef.getRefValue6() != null) {
                refList.addAll(genRef.getRefValue6().split(','))
            }
            if (refList != null && !refList.isEmpty()) {
                if (!Collections.disjoint(refList, userRoleList)) {
                    return true
                }
            }
        }
        return false
    }

    private QueryResult getUserRoleList(UserContext userContext, Serializable userGkey) {
        logger.info("$this started at ${new Date()}")

        // set up the domain query request
        CrudBizDelegate request = new BizPortalBizDelegate()
        DomainQuery query = QueryUtils.createDomainQuery("ArgoUser")
        query.addDqPredicate(PredicateFactory.pkEq(userGkey))
        query.addDqField(MetafieldIdFactory.valueOf("buserRoleList"))
        query.setScopingEnabled(false)

        // get the response
        BizResponse response =
                request.processQuery(TranslationUtils.getTranslationContext(userContext), query)

        logger.info("$this ended  at ${new Date()}")

        // return the result
        return response.getQueryResult()
    }

    private static final Logger logger = Logger.getLogger(this.class)
}

