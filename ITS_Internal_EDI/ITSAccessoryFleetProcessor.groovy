
import com.navis.argo.*
import com.navis.argo.business.api.ArgoEdiUtils
import com.navis.argo.business.api.IBizUnitManager
import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.*
import com.navis.edi.business.edimodel.EdiConsts
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.business.atoms.LifeCycleStateEnum
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizViolation
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.message.MessageCollectorUtils
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.EquipmentState
import com.navis.inventory.business.units.Unit
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject

/**
 * Author: sanandaraj@weservetech.com 03/08/2022
 *
 * This code extension creates or updates or deletes an Accessory from fleet file.
 *
 * *
 * @Inclusion Location	: Incorporated as a code extension of the type EDI_POST_INTERCEPTOR.
 *
 *  Load Code Extension to N4:
 1. Go to Administration --> System --> Code Extensions
 2. Click Add (+)
 3. Enter the values as below:
 Code Extension Name:  ITSAccessoryFleetProcessor
 Code Extension Type:  EDI_POST_INTERCEPTOR
 Groovy Code: Copy and paste the contents of groovy code.
 4. Click Save button

 Attach code extension to EDI session:
 1. Go to Administration-->EDI-->EDI configuration
 2. Select the EDI session and right click on it
 3. Click on Edit
 4. Select the extension in "Post Code Extension" tab
 5. Click on save
 *
 *
 */

public class ITSAccessoryFleetProcessor extends AbstractEdiPostInterceptor {
    private static final Logger LOGGER = Logger.getLogger(ITSAccessoryFleetProcessor.class)
    final IBizUnitManager bizUnitManager = (IBizUnitManager) Roastery.getBean("bizUnitManager");

    public void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        LOGGER.debug("ITSAccessoryFleetProcessor - Started execution")
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
            eqNbr = eqNbr != null ? eqNbr.replaceAll("\\s", "") : null
            String isoType = flexFields.getUnitFlexString02()
            String lineOp = flexFields.getUnitFlexString03()
            String eqClass = flexFields.getUnitFlexString04()
            String eqLength = flexFields.getUnitFlexString05()
            String tareWt = flexFields.getUnitFlexString06()
            String safeWt = flexFields.getUnitFlexString07()
            String action = flexFields.getUnitFlexString08()
            String eqHeight = flexFields.getUnitFlexString09()
            String yearBuild = flexFields.getUnitFlexString10()
            String operator = flexFields.getUnitFlexString11()

            GeneralReference invalidIso = GeneralReference.findUniqueEntryById("ITS", "NON_ACCESSORY_FLEET", lineOp, "INVALID_ISO")
            Boolean isValidCtr = Boolean.TRUE

            if (invalidIso != null) {
                List<String> invalidIsoList = invalidIso.getRefValue1() != null ? invalidIso.getRefValue1().split(",") : null
                isValidCtr = (isoType != null && invalidIsoList != null && invalidIsoList.contains(isoType)) ? Boolean.FALSE : isValidCtr
            }
            if (eqNbr != null && !isValidChassisNbr(eqNbr)) {
                LOGGER.debug("Requested equipment: " + eqNbr + " has special character(s). Skipping EDI post");
                registerError("Requested equipment: " + eqNbr + " has special character(s). Skipping EDI post");
                inParams.put(EdiConsts.SKIP_POSTER, Boolean.TRUE);
            }


            if (eqClass != null) {
                 if (eqClass.equalsIgnoreCase("ACC")) {
                    processAccessory(eqNbr, isoType, lineOp, tareWt, safeWt, action, yearBuild)
                }
                 else {
                     LOGGER.debug("Requested equipment: " + eqNbr + " is not a chassis. Skipping EDI post");
                     appendToMessageCollectorAsWarning(PropertyKeyFactory.valueOf("FLEET_NON_ACCESSORY_DATA"), "Requested equipment: " + eqNbr + " is not an Accessory. Skipping EDI post", eqNbr);
                     inParams.put(EdiConsts.SKIP_POSTER, Boolean.TRUE);
                 }

            }
        }
        LOGGER.debug("ITSAccessoryFleetProcessor - Completed execution")
    }

    private void processAccessory(String accNbr, String inISO, String inLine, String inTareWt, String inSafeWt,
                                  String action, String inYearBuild) {
        try {
            if (accNbr != null) {
                ScopedBizUnit lineOperator = LineOperator.findLineOperatorById(inLine);
                Accessory acc = Accessory.findAccessory(accNbr);
                EquipType equipType = EquipType.findEquipType(inISO);

                ScopedBizUnit exLine = acc != null ? acc.getEquipmentOperator() : null
                if (exLine != null) {
                    if (lineOperator != null && !lineOperator.equals(exLine) && LifeCycleStateEnum.ACTIVE.equals(acc.getLifeCycleState()) && action == 'D') {
                        registerError("Requested Accessory belongs to another line, cannot be made Obsolete.");
                        return
                    }
                }



                if (equipType != null) {
                    switch (action) {
                        case 'A':
                            if (acc == null) {
                                acc = Accessory.createAccessory(accNbr, inISO, DataSourceEnum.USER_DBA);
                            }

                            Serializable eqsKey = bizUnitManager.findOrCreateEquipmentState(acc);

                            if (eqsKey != null) {
                                EquipmentState eqs = (EquipmentState) HibernateApi.getInstance().load(EquipmentState.class, eqsKey)
                                updateAccessoryProps(acc, equipType, inISO, inTareWt, inSafeWt, eqs, lineOperator)
                                if (LifeCycleStateEnum.OBSOLETE.equals(acc.getEqLifeCycleState())) {
                                    acc.setLifeCycleState(LifeCycleStateEnum.ACTIVE);
                                }
                            }


                            break;
                        case 'D':
                            if (acc == null) {
                                if (validateObsCreation(accNbr)) {
                                    acc = Accessory.createAccessory(accNbr, inISO, DataSourceEnum.USER_DBA);
                                } else {
                                    registerError("No reference data available for Accessory: " + accNbr)
                                    return
                                }
                            }
                            ScopedBizUnit unkLine = LineOperator.findLineOperatorById("UNK");
                            if (!isValidDelete(accNbr)) {
                                registerError("Cannot delete Accessory ${accNbr} as it has an Active Unit.");
                                return
                            }
                            Serializable eqsKey = bizUnitManager.findOrCreateEquipmentState(acc);
                            if (eqsKey != null) {
                                EquipmentState eqs = (EquipmentState) HibernateApi.getInstance().load(EquipmentState.class, eqsKey)
                                updateAccessoryProps(acc, equipType, inISO, inTareWt, inSafeWt, eqs, unkLine)
                            }

                            //if unit exists throw CAN_NOT_DELETE_CONTAINER else below
                            acc.setLifeCycleState(LifeCycleStateEnum.OBSOLETE);
                            break;
                        default:
                            registerError("Class not valid -" + action);
                            break;

                    }
                    HibernateApi.getInstance().save(acc);
                } else {
                    registerError("Invalid Equipment Type ${inISO}");
                }


            }
        } catch (Exception inEx) {
            LOGGER.error("Exception occurred while posting Accessory " + inEx)
        }
    }


    private void updateAccessoryProps(Accessory acc, EquipType equipType, String inISO, String inTareWt, String inSafeWt, EquipmentState eqs, LineOperator lineOperator) {
        if (equipType) {
            acc.upgradeEqType(inISO, DataSourceEnum.USER_DBA)
        }
        inTareWt = inTareWt != null ? inTareWt : equipType.getTareWeightKg()

        double safeWt = (inSafeWt != null && inSafeWt.length() > 0) ? inSafeWt.toDouble() : equipType.getEqtypSafeWeightKg()
        if(inTareWt != null && inTareWt.length() > 0) {
            acc.updateEqTareWtKg(inTareWt.toDouble())
        }
        acc.updateEqSafeWtKg(safeWt)
        if (eqs != null) {
            if(lineOperator != null){
                eqs.setEqsEqOperator(lineOperator);
                eqs.setEqsEqOwner(lineOperator);
            }
            HibernateApi.getInstance().save(eqs);
        }
    }

    private boolean validateObsCreation(String eqId) {

        String configValue = (String) ArgoEdiUtils.getConfigValue(ContextHelper.getThreadEdiPostingContext(), ArgoConfig.CREATE_EQUIPMENT_OBS_STATUS);
        if ("NOT_ALLOWED".equals(configValue)) {
            registerError("No Equipment definition exists with ID: " + eqId);
            return false
        }
        return true
    }

    public boolean isValidDelete(String ctrNbr) {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                .addDqPredicate(PredicateFactory.eq(InventoryField.UNIT_ID, ctrNbr))
                .addDqPredicate(PredicateFactory.in(UnitField.UNIT_VISIT_STATE, UnitVisitStateEnum.ACTIVE));
        int count = HibernateApi.getInstance().findCountByDomainQuery(dq);
        if (count == 0) {
            return true
        }
        return false
    }

    private static void updateOperator(String operatorId, Serializable inEqsGkey) throws BizViolation {
        IBizUnitManager bizUnitManager = (IBizUnitManager) Roastery.getBean("bizUnitManager");
        if (operatorId != null) {
            ScopedBizUnit operator = ScopedBizUnit.findEquipmentOperatorProxy(operatorId);
            if (operator == null) {
                BizViolation.create(ArgoPropertyKeys.REFERENCED_ENTITY_NOT_FOUND, (BizViolation) null, "Operator", operatorId);
            }

            bizUnitManager.upgradeEqOperator(inEqsGkey, operator, ContextHelper.getThreadDataSource());
        }

    }

    private static void updateOwner(String ownerId, Serializable inEqsGkey) {
        IBizUnitManager bizUnitManager = (IBizUnitManager) Roastery.getBean("bizUnitManager");
        if (ownerId != null) {
            ScopedBizUnit owner = ScopedBizUnit.findEquipmentOwnerProxy(ownerId);
            if (owner == null) {
                BizViolation.create(ArgoPropertyKeys.REFERENCED_ENTITY_NOT_FOUND, (BizViolation) null, "Owner", ownerId);
            }

            bizUnitManager.upgradeEqOwner(inEqsGkey, owner, ContextHelper.getThreadDataSource());
        }

    }

    Boolean isValidChassisNbr(String inChsNbr) {
        boolean result = inChsNbr.matches("[a-zA-Z0-9]+")
        return result;
    }

    private static void appendToMessageCollectorAsWarning(PropertyKey inPropertyKey, String inMessage, String eqNbr) {
        MessageCollectorUtils.getMessageCollector().appendMessage(MessageLevel.WARNING, inPropertyKey, inMessage, eqNbr);
    }
}
