/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/
package ITS

import com.navis.argo.ArgoExtractEntity
import com.navis.argo.ArgoExtractField
import com.navis.argo.ArgoPropertyKeys
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.extract.billing.ConfigurationProperties
import com.navis.argo.portal.BillingWsApiConsts
import com.navis.argo.util.XmlUtil
import com.navis.argo.webservice.types.v1_0.*
import com.navis.external.argo.AbstractGroovyJobCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.Junction
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.www.services.argoservice.ArgoServiceLocator
import com.navis.www.services.argoservice.ArgoServicePort
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jdom.Element
import org.jdom.Text
import org.jdom.output.XMLOutputter

import javax.xml.rpc.ServiceException
import javax.xml.rpc.Stub

/**
 * @Author: mailto:dashokkumar@weservetech.com, Ashok K.D; Date: 29/12/2022
 *
 *  Requirements: Generating Invoice for Import units with Line_Storage CUE event. SPTD set as in Invoice based on conditions
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSGenerateDemurrageInvoicesGroovyJob
 *     Code Extension Type: GROOVY_JOB_CODE_EXTENSION
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup Create the new Groovy Job with Job Target ITSGenerateDemurrageInvoicesGroovyJob
 *
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */

class ITSGenerateDemurrageInvoicesGroovyJob extends AbstractGroovyJobCodeExtension {

    @Override
    void execute(Map<String, Object> inParams) {
        LOGGER.debug("ITSGenerateDemurrageInvoicesGroovyJob Invoke")
        String action = "DRAFT"
        String eventTypeId = "LINE_STORAGE"
        List statusList = new ArrayList<String>()
        statusList.add(QUEUED)
        statusList.add(PARTIAL)
        String invoiceTypeId = "IMPORT PRE-PAY"
        String currencyId = "USD"

        String[] statuses = statusList.toArray(new String[statusList.size()])

        UserContext context = ContextHelper.getThreadUserContext()
        Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone())
        HibernateApi hibernateApi = Roastery.getHibernateApi()
        Date today = ArgoUtils.timeNow()

        List<ChargeableUnitEvent> cueDataList = getLineIdAndGuaranteePartyToBill(eventTypeId, statuses, context)

        if (cueDataList.size() == 0) {
            registerError("No records to be processed")
        }
        try {
            List<Object> unitIdsProcessed = new ArrayList<Object>()

            for (ChargeableUnitEvent lineIdAndGuranteeParty : cueDataList) {

                String eqId
                String lineId
                Date gtd
                Serializable ufvGkey
                String payee
                String guranteePartyRole
                Date timeOut
                Date date


                eqId = lineIdAndGuranteeParty?.getBexuEqId()
                lineId = lineIdAndGuranteeParty?.getBexuLineOperatorId()
                gtd = lineIdAndGuranteeParty?.getBexuGuaranteeThruDay()
                ufvGkey = lineIdAndGuranteeParty?.getBexuUfvGkey()
                guranteePartyRole = lineIdAndGuranteeParty?.getBexuGuaranteeParty()
                payee = guranteePartyRole
                timeOut = lineIdAndGuranteeParty?.getBexuUfvTimeOut()


                if (gtd != null) {
                    if (today != null && gtd.after(today)) {
                        if (timeOut != null && gtd.after(timeOut)) {
                            date = timeOut
                        } else {
                            date = today
                        }

                    } else {
                        date = gtd
                        if (timeOut != null && timeOut.before(gtd)) {
                            date = timeOut
                        }
                    }
                }


                if (eqId == null || lineId == null || date == null || ufvGkey == null) {
                    LOGGER.debug("Either one is NULL for Unit : $eqId, Line: $lineId, GTD: $gtd, Timeout $timeOut, UfvGkey: $ufvGkey, Timeout: $timeOut")
                    continue
                }


                LOGGER.warn("Processing for Unit : $eqId, Line: $lineId, with ufvGkey : $ufvGkey")
                UnitFacilityVisit ufv = null
                try {
                    ufv = null
                    ufv = (UnitFacilityVisit) hibernateApi.get(UnitFacilityVisit.class, ufvGkey)

                } catch (Exception inUfvEx) {
                    continue
                }
                LOGGER.debug(" UFV founf for Gkey : " + ufv)
                if (ufv == null) {
                    LOGGER.debug("No UFV founf for Gkey : " + ufvGkey)
                    continue
                }


                Element element

                if (!unitIdsProcessed.contains(eqId)) {

                    element =
                            buildGetInvoiceByInvTypeIdForUnitElement(eqId, invoiceTypeId, action, payee, guranteePartyRole, currencyId, timeNow, date, lineId)
                    if (element != null) {
                        unitIdsProcessed.add(eqId)
                        LOGGER.debug("The XML request string : \n ${elementToString(element)}")
                        try {
                            processInvoiceAndPrint(element)
                        } catch (Exception ex) {
                            LOGGER.debug("The process failed due to ${ex.getMessage()}")
                            registerError("The process failed in due to ${ex.getMessage()} for ${eqId}")
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug(e, e)
            LOGGER.debug("The process failed due to ${e.getMessage()}")
            registerError("The process failed due to ${e.getMessage()}")
        }
    }

    private List<ChargeableUnitEvent> getLineIdAndGuaranteePartyToBill(String eventTypeId, String[] statuses, UserContext context) {
        LOGGER.setLevel(Level.DEBUG)


        DomainQuery query = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT)
                .addDqField(ArgoExtractField.BEXU_EQ_ID)
                .addDqField(ArgoExtractField.BEXU_PAYEE_ROLE)
                .addDqField(ArgoExtractField.BEXU_GUARANTEE_THRU_DAY)
                .addDqField(ArgoExtractField.BEXU_UFV_GKEY)
                .addDqField(ArgoExtractField.BEXU_UFV_TIME_OUT)
                .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_EVENT_TYPE, eventTypeId))
                .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_CATEGORY, UnitCategoryEnum.IMPORT.getKey()))
                .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_STATUS, statuses))
                .addDqPredicate(PredicateFactory.isNotNull(ArgoExtractField.BEXU_GUARANTEE_THRU_DAY))


        Junction notInvoiced = PredicateFactory.disjunction()
                .add(PredicateFactory.ltProperty(ArgoExtractField.BEXU_PAID_THRU_DAY, ArgoExtractField.BEXU_GUARANTEE_THRU_DAY))
                .add(PredicateFactory.isNull(ArgoExtractField.BEXU_PAID_THRU_DAY))

        DomainQuery dq = query.addDqPredicate(notInvoiced)


        List<ChargeableUnitEvent> result = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)


        return result
    }

    private Element buildGetInvoiceByInvTypeIdForUnitElement(String equipId, String invoiceTypeId, String inAction, String inPayee,
                                                             String guranteePartyRole, String inCurrencyId, Date inContractEffectiveDate,
                                                             Date inPaidThruDate, String inLineId) {
        LOGGER.setLevel(Level.DEBUG)
        Element rootElem = new Element(BillingWsApiConsts.BILLING_ROOT, XmlUtil.ARGO_NAMESPACE)
        Element elem = new Element(BillingWsApiConsts.GENERATE_INVOICE_REQUEST, XmlUtil.ARGO_NAMESPACE)

        rootElem.addContent(elem)
        addChildTextElement(BillingWsApiConsts.ACTION, inAction, elem)
        addChildTextElement(BillingWsApiConsts.INVOICE_TYPE_ID, invoiceTypeId, elem)
        if (inPayee != null) {
            addChildTextElement(BillingWsApiConsts.PAYEE_CUSTOMER_ID, inPayee, elem)
        }
        if (guranteePartyRole != null) {
            addChildTextElement(BillingWsApiConsts.PAYEE_CUSTOMER_BIZ_ROLE, guranteePartyRole, elem)
        }


        addChildTextElement(BillingWsApiConsts.CURRENCY_ID, inCurrencyId, elem)

        String effectiveDateStr = null
        if (inContractEffectiveDate != null) {
            effectiveDateStr = BillingWsApiConsts.XML_DATE_TIME_ZONE_FORMAT.format(inContractEffectiveDate)
        }
        addChildTextElement(BillingWsApiConsts.CONTRACT_EFFECTIVE_DATE, effectiveDateStr, elem)
        addChildTextElement(BillingWsApiConsts.IS_INVOICE_FINAL, IS_INVOICE_FINAL, elem)

        Element paramsElem = new Element(BillingWsApiConsts.INVOICE_PARAMETERS, XmlUtil.ARGO_NAMESPACE)
        Element paramElem = new Element(BillingWsApiConsts.INVOICE_PARAMETER, XmlUtil.ARGO_NAMESPACE)
        String paidThruDayStr = null
        if (inPaidThruDate != null) {
            paidThruDayStr = BillingWsApiConsts.XML_DATE_TIME_ZONE_FORMAT.format(inPaidThruDate)
        }

        addChildTextElement(BillingWsApiConsts.PAID_THRU_DAY, paidThruDayStr, paramElem)
        addChildTextElement(BillingWsApiConsts.EQUIPMENT_ID, equipId, paramElem)
        if (inLineId != null) {
            addChildTextElement("LineOperatorId", inLineId, paramElem)
        }
        paramsElem.addContent(paramElem)
        elem.addContent(paramsElem)
        return rootElem
    }

    private void addChildTextElement(String inElementName, String inElementText, Element inParentElement) {
        Element childElement = new Element(inElementName, XmlUtil.ARGO_NAMESPACE)
        Text childText = new Text(inElementText)
        childElement.addContent(childText)
        inParentElement.addContent(childElement)
    }

    private String elementToString(Element node) {
        XMLOutputter outputter = new XMLOutputter()
        return outputter.outputString(node)
    }

    private ArgoServicePort getWsStub() throws ServiceException {
        ArgoServiceLocator locator = new ArgoServiceLocator()
        ArgoServicePort port = locator.getArgoServicePort(ConfigurationProperties.getBillingServiceURL())
        Stub stub = (Stub) port
        stub._setProperty(Stub.USERNAME_PROPERTY, ConfigurationProperties.getBillingWebServiceUserId())
        stub._setProperty(Stub.PASSWORD_PROPERTY, ConfigurationProperties.getBillingWebServicePassWord())
        return port
    }

    private ScopeCoordinateIdsWsType getScopeCoordenatesForWs() {
        ScopeCoordinateIdsWsType scopeCoordinates = new ScopeCoordinateIdsWsType()
        scopeCoordinates.setOperatorId(ContextHelper.getThreadOperator() != null ? ContextHelper.getThreadOperator().getId() : null)
        scopeCoordinates.setComplexId(ContextHelper.getThreadComplex() != null ? ContextHelper.getThreadComplex().getCpxId() : null)
        scopeCoordinates.setFacilityId(ContextHelper.getThreadFacility() != null ? ContextHelper.getThreadFacility().getFcyId() : null)
        scopeCoordinates.setYardId(ContextHelper.getThreadYard() != null ? ContextHelper.getThreadYard().getYrdId() : null)
        return scopeCoordinates
    }

    private void processInvoiceAndPrint(Element element) {
        getInvoiceByInvTypeIdForUnit(element)
    }

    public getInvoiceByInvTypeIdForUnit(Element inElement) throws BizViolation {
        LOGGER.setLevel(Level.DEBUG)
        ArgoServicePort port = getWsStub()
        ScopeCoordinateIdsWsType scopeCoordinates = getScopeCoordenatesForWs()
        GenericInvokeResponseWsType invokeResponseWsType = port.genericInvoke(scopeCoordinates, XmlUtil.toString(inElement, false));
        ResponseType response = invokeResponseWsType.getCommonResponse()
        QueryResultType[] queryResultTypes = response.getQueryResults()
        if (queryResultTypes == null || queryResultTypes.length != 1) {
            if (response.getMessageCollector() != null && response.getMessageCollector().getMessages(0) != null) {
                MessageType type = response.getMessageCollector().getMessages(0)
                String message = type.getMessage()
                throw BizFailure.create("Error from Billing Webservice - " + message)
            } else {
                throw BizFailure.create(ArgoPropertyKeys.BILLING_WEBSERVICE_SERVICES_URL, null, null);
            }
        }
    }

    private static final String QUEUED = "QUEUED"
    private static final String PARTIAL = "PARTIAL"
    private static String IS_INVOICE_FINAL = "True"
    private static Logger LOGGER = Logger.getLogger(ITSGenerateDemurrageInvoicesGroovyJob.class)
}
