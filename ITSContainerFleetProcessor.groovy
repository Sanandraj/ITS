/*
 * Copyright (c) 2020 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.ContextHelper
import com.navis.argo.EdiFlexFields
import com.navis.argo.PreadviseTransactionDocument
import com.navis.argo.PreadviseTransactionsDocument
import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.*
import com.navis.edi.business.edimodel.EdiConsts
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.business.Roastery
import com.navis.framework.business.atoms.LifeCycleStateEnum
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.EquipmentState
import com.navis.inventory.business.units.Unit
import com.navis.road.business.util.RoadBizUtil
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject

/**
 * Author: <a href="bgopal@weservetech.com">Gobal B</a>
 *
 * Requirements: This code extension creates or updates or deletes a container from fleet file.
 *
 * Inclusion Location	: Incorporated as a code extension of the type EDI_POST_INTERCEPTOR.
 *
 * Load Code Extension to N4:
 * 1. Go to Administration --> System --> Code Extensions
 * 2. Click Add (+)
 * 3. Enter the values as below:
 *     Code Extension Name:  ITSContainerFleetProcessor
 *     Groovy Code: Copy and paste the contents of groovy code.
 * 4. Click Save button
 *
 * Attach code extension to EDI session:
 * 1. Go to Administration-->EDI-->EDI configuration
 * 2. Select the EDI session and right click on it
 * 3. Click on Edit
 * 4. Select the extension in "Post Code Extension" tab
 * 5. Click on save
 *
 * Modified : @author <ahref="mailto:mmadhavan@weservetech.com">Madhavan M</a>
 */
public class ITSContainerFleetProcessor extends AbstractEdiPostInterceptor {
    private static final Logger LOGGER = Logger.getLogger(ITSContainerFleetProcessor.class)

    public void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSContainerFleetProcessor - Started execution")
        inParams.put(EdiConsts.SKIP_POSTER, true);
        PreadviseTransactionsDocument preAdviseDocument = (PreadviseTransactionsDocument) inXmlTransactionDocument;
        PreadviseTransactionsDocument.PreadviseTransactions preAdviseTransactions = preAdviseDocument.getPreadviseTransactions();
        List<PreadviseTransactionDocument.PreadviseTransaction> list = preAdviseTransactions.getPreadviseTransactionList();
        if (list.isEmpty()) {
            registerError("There is no transaction in the batch")
        }
        PreadviseTransactionDocument.PreadviseTransaction preadviseTransaction = list.get(0);
        EdiFlexFields flexFields = preadviseTransaction.getEdiFlexFields();
        if (flexFields != null) {
            String eqNbr = flexFields.getUnitFlexString01()
            eqNbr = eqNbr.replaceAll("\\s", "")
            String isoType = flexFields.getUnitFlexString02()
            String lineOp = flexFields.getUnitFlexString03()
            String eqClass = flexFields.getUnitFlexString04()
//            String eqLength = flexFields.getUnitFlexString05()
            String tareWt = flexFields.getUnitFlexString06()
            String safeWt = flexFields.getUnitFlexString07()
            String action = flexFields.getUnitFlexString08()
//            String eqHeight = flexFields.getUnitFlexString09()
//            String yearBuild = flexFields.getUnitFlexString10()
//            String eqGrade = flexFields.getUnitFlexString11()

            GeneralReference invalidIso = GeneralReference.findUniqueEntryById("ITS", "NON_CONTAINER_FLEET", lineOp, "INVALID_ISO")
            Boolean isValidCtr = Boolean.TRUE

            if (invalidIso != null) {
                List<String> invalidIsoList = invalidIso.getRefValue1() != null ? invalidIso.getRefValue1().split(",") : null
                isValidCtr = (isoType != null && invalidIsoList != null && invalidIsoList.contains(isoType)) ? Boolean.FALSE : isValidCtr
            }
            if (eqClass != null && eqClass.equalsIgnoreCase("CT") && isValidCtr) {
                processContainer(eqNbr, isoType, lineOp, tareWt, safeWt, action)
            } else {
                LOGGER.debug("Requested equipment: " + eqNbr + " is not a container. Skipping EDI post");
                registerWarning("Requested equipment: " + eqNbr + " is not a container. Skipping EDI post");
            }
        }
        LOGGER.debug("ITSContainerFleetProcessor - Completed execution")
    }

    private void processContainer(String inCtrNbr, String inISO, String inLine, String inTareWt, String inSafeWt, String action) {
        try {
            ScopedBizUnit lineOperator = inLine != null ? LineOperator.findLineOperatorById(inLine) : null
            LOGGER.debug("ITSContainerFleetProcessor - EDI Line Operator: " + lineOperator)
            if (inCtrNbr == null || (inCtrNbr != null && StringUtils.isEmpty(inCtrNbr))) {
                LOGGER.debug("ITSContainerFleetProcessor - Invalid container number.")
                return
            } else if (lineOperator == null) {
                registerError("Fleet record has incorrect Line Operator " + inLine + ", cannot process EDI.")
            }

            Container ctr = Container.findContainer(inCtrNbr);
            if(inISO.isEmpty()){
                registerError("Fleet record received for " + inCtrNbr + " with Null ISO, cannot process EDI.")
                return
            }
            EquipType ediEqType = inISO != null ? EquipType.findEquipType(inISO) : null
            if (ediEqType == null && !StringUtils.isEmpty(action) && ("A".equalsIgnoreCase(action)||"D".equalsIgnoreCase(action))) {
                registerError("Fleet record received for " + inCtrNbr + " with invalid ISO " + inISO + ", cannot process EDI.")
                return
            }
            if (ctr == null) {
                if (action.equalsIgnoreCase("A")) {
                    ctr = Container.findOrCreateContainer(inCtrNbr, inISO, DataSourceEnum.USER_DBA);
                    EquipmentState eqs = EquipmentState.findOrCreateEquipmentState(ctr, ContextHelper.getThreadOperator(), lineOperator);
                    if (eqs != null) {
                        eqs.setEqsEqOperator(lineOperator);
                        eqs.setEqsEqOwner(lineOperator);
                        HibernateApi.getInstance().save(eqs);
                    }
                }
                if (action.equalsIgnoreCase("D")) {
                    registerWarning("No reference data available for container: " + inCtrNbr);
                }
            }
            LOGGER.debug("ITSContainerFleetProcessor - Container requested: " + ctr)
            Boolean hasDifferentLine = ctr?.getEquipmentOperator()?.getBzuId()?.equalsIgnoreCase(lineOperator.getBzuId())? Boolean.FALSE : Boolean.TRUE
            GeneralReference onHireGenRef = null
            if (hasDifferentLine) {
                onHireGenRef = findOnHireGenRef(lineOperator.getBzuId(), ctr?.getEquipmentOperator()?.getBzuId())
            }

            UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
            Unit unit = unitFinder.findActiveUnit(ContextHelper.getThreadComplex(), ctr);
            LOGGER.debug("ITSContainerFleetProcessor - Active unit: " + unit)
            if (unit != null && hasDifferentLine && onHireGenRef != null ) {
                registerError(unit.getUnitId() + " has active unit visit for " + unit.getUnitLineOperator().getBzuId() + ", cannot on-hired with " + inLine + ".")
                return
            }
            if (action != null && "A".equalsIgnoreCase(action)) {
                if (hasDifferentLine) {
                    if (onHireGenRef != null) {
                        EquipmentState eqs = EquipmentState.findOrCreateEquipmentState(ctr, ContextHelper.getThreadOperator(), lineOperator);
                        if (eqs != null) {
                            eqs.setEqsEqOperator(lineOperator);
                            eqs.setEqsEqOwner(lineOperator);
                            HibernateApi.getInstance().save(eqs);
                        }
                    } else{
                        registerError(ctr.getEqIdFull() + " is owned by " + ctr.getEquipmentOperator().getBzuId() + ", cannot on-hired with " + inLine + ".")
                        return
                    }
                }
                Double inTareWtKg = inTareWt != null ? Double.parseDouble(inTareWt) : null
                Double inSafeWtKg = inSafeWt != null ? Double.parseDouble(inSafeWt) : null
                ctr = updateEqProperties(ctr, ediEqType, inTareWtKg, inSafeWtKg)
                ctr.updateLifeCycleState(LifeCycleStateEnum.ACTIVE)
                HibernateApi.getInstance().save(ctr)
            } else if (action != null && "D".equalsIgnoreCase(action)) {
                if (hasDifferentLine && !ScopedBizUnit.UNKNOWN_BIZ_UNIT.equalsIgnoreCase(ctr.getEquipmentOperator().getBzuId())) {
                    registerWarning(ctr.getEqIdFull() + " is owned by " + ctr.getEquipmentOperator().getBzuId() + ", cannot off-hired with " + inLine + ".")
                } else {
                    EquipmentState eqs = EquipmentState.findOrCreateEquipmentState(ctr, ContextHelper.getThreadOperator(), lineOperator);
                    ScopedBizUnit unkLineOperator = LineOperator.findLineOperatorById(ScopedBizUnit.UNKNOWN_BIZ_UNIT);
                    if (eqs != null) {
                        eqs.setEqsEqOperator(unkLineOperator);
                        eqs.setEqsEqOwner(unkLineOperator);
                        HibernateApi.getInstance().save(eqs);
                        ctr.updateLifeCycleState(LifeCycleStateEnum.ACTIVE)
                    }
                }
            }

        } catch (Exception inEx) {

        }
    }

    private GeneralReference findOnHireGenRef(String inEdiLine, String inEqLine) {
        GeneralReference onHireGenRef = GeneralReference.findUniqueEntryById("ITS", "ONHIRE", "ALL", "ALL")
        LOGGER.debug("ITSContainerFleetProcessor - onHireGenRef All to ALL " + onHireGenRef)
        if (onHireGenRef == null) {
            onHireGenRef = GeneralReference.findUniqueEntryById("ITS", "ONHIRE", "ALL", inEdiLine)
        }
        LOGGER.debug("ITSContainerFleetProcessor - onHireGenRef All to one " + onHireGenRef)
        if (onHireGenRef == null) {
            onHireGenRef = GeneralReference.findUniqueEntryById("ITS", "ONHIRE", inEqLine, inEdiLine)
        }
        return onHireGenRef
    }

    private Equipment updateEqProperties(Container inCtr, EquipType inEqType, Double inTareWt, Double inSafeWt) {
        if (inCtr != null) {
            if (!inCtr.getEqEquipType().equals(inEqType)) {
                inCtr.setEqEquipType(inEqType);
            }
            if (inTareWt != null) {
                inCtr.setEqTareWeightKg(inTareWt)
            }
            if (inSafeWt != null) {
                inCtr.updateEqSafeWtKg(inSafeWt)
            }
        }
        return inCtr
    }

    private void registerWarning(String inWarningMessage) {
        RoadBizUtil.messageCollector.appendMessage(MessageLevel.WARNING, AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, inWarningMessage)
    }

}
