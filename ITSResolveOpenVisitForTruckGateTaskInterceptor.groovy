/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.reference.Equipment
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InvField
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.RoadConfig
import com.navis.road.RoadEntity
import com.navis.road.RoadField
import com.navis.road.business.api.RoadManager
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.atoms.TruckVisitStatusEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.reference.CancelReason
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger

import java.util.concurrent.TimeUnit

/**
 * @Author: uaarthi@weservetech.com, Aarthi U; Date: 31-10-2022
 *
 *  Requirements: IP-15 - Gate 4-24 Manage Open Truck Visit for Revisiting Trucks
 *
 * @Inclusion Location: Incorporated as a code extension of the type GATE_TASK_INTERCEPTOR
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSResolveOpenVisitForTruckGateTaskInterceptor
 *     Code Extension Type: GATE_TASK_INTERCEPTOR
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 *
 */

class ITSResolveOpenVisitForTruckGateTaskInterceptor extends AbstractGateTaskInterceptor {
    @Override
    void execute(TransactionAndVisitHolder inWfCtx) {
        TruckVisitDetails truckVisitDetails = inWfCtx.getTv()
        if (truckVisitDetails != null) {

            DomainQuery domainQuery = QueryUtils.createDomainQuery(RoadEntity.TRUCK_VISIT_DETAILS)
                    .addDqPredicate(PredicateFactory.eq(RoadField.TVDTLS_TRUCK_LICENSE_NBR, truckVisitDetails.getTruckLicenseNbr()))
                    .addDqPredicate(PredicateFactory.in(RoadField.TVDTLS_STATUS, [TranStatusEnum.OK, TranStatusEnum.TROUBLE]))
            List<TruckVisitDetails> outputList = HibernateApi.getInstance().findEntitiesByDomainQuery(domainQuery)
            String settingValue = RoadConfig.ACTIVE_TRUCK_VISIT.getValue(getUserContext())
            if (StringUtils.isNotEmpty(settingValue)) {
                try {
                    for (TruckVisitDetails tvd : outputList as List<TruckVisitDetails>) {
                        Date date = tvd.getTvdtlsCreated();
                        long diff = ArgoUtils.timeNow().getTime() - date.getTime()

                        long duration = TimeUnit.MILLISECONDS.toMinutes(diff);
                        if (duration > settingValue.toInteger()) {
                            Set<TruckTransaction> transactions = tvd.getTvdtlsTruckTrans()

                            for (TruckTransaction tran : transactions) {
                                if (tran != null) {
                                    boolean isReceival = tran.isReceival();
                                    boolean isDelivery = tran.isDelivery();
                                    if (tran.getTranStatus() == TranStatusEnum.OK) {
                                        tran.setTranStatus(TranStatusEnum.CLOSED)
                                    } else if (tran.getTranStatus() == TranStatusEnum.TROUBLE) {
                                        UnitFacilityVisit ufv = null
                                        if (tran.getTranUfv() != null) {
                                            ufv = tran.getTranUfv();
                                        } else {
                                            String ctrNbr = tran.getTranCtrNbr() != null ? tran.getTranCtrNbr() : tran.getTranCtrNbrAssigned()
                                            if (ctrNbr != null) {
                                                Equipment equipment = Equipment.findEquipment(ctrNbr)
                                                if (equipment != null) {
                                                    Unit unit = unitFinder.findActiveUnit(ContextHelper.getThreadComplex(), equipment, tran.getTranUnitCategory())
                                                    if (unit != null) {
                                                        ufv = unit.getUnitActiveUfvNowActive()
                                                    } else {
                                                        Unit departedUnit = unitFinder.findDepartedUnit(ContextHelper.getThreadComplex(), equipment)
                                                        if (departedUnit != null) {
                                                            ufv = departedUnit.getUfvForFacilityCompletedOnly(ContextHelper.getThreadFacility());
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (ufv == null && tran.getTranStatus().equals(TranStatusEnum.TROUBLE)) {
                                            tran.setTranStatus(TranStatusEnum.CANCEL)
                                        } else {
                                            CancelReason reason = CancelReason.findOrCreate("AUTO_CLOSED", "Auto Closed at Ingate.")
                                            LocPosition ufvPosition = ufv.getUfvLastKnownPosition();
                                            if (isReceival) {
                                                if (UfvTransitStateEnum.S30_ECIN.equals(ufv.getUfvTransitState()) || UfvTransitStateEnum.S40_YARD.equals(ufv.getUfvTransitState())) {
                                                    if (ufv.getFinalPlannedPosition() != null) {
                                                        ufv.move(ufv.getFinalPlannedPosition(), null);
                                                        ufv.setFieldValue(InvField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S20_INBOUND)
                                                        tran.cancelDropoffWorkInstructions();
                                                        roadManager.cancelTruckTransaction(tran, reason, false)
                                                    }
                                                } else {
                                                    tran.setTranStatus(TranStatusEnum.CANCEL)
                                                }
                                            }
                                            if (isDelivery) {
                                                if (ufv.isTransitStateBeyond(UfvTransitStateEnum.S20_INBOUND)) {
                                                    if ((UfvTransitStateEnum.S50_ECOUT.equals(ufv.getUfvTransitState()) || UfvTransitStateEnum.S60_LOADED.equals(ufv.getUfvTransitState()))
                                                            && ufvPosition != null) {
                                                        ufv.move(ufvPosition, null);
                                                        ufv.setFieldValue(InvField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD)
                                                        tran.cancelPickupWorkInstruction();
                                                        roadManager.cancelTruckTransaction(tran, reason, false)
                                                    }
                                                    tran.setTranStatus(TranStatusEnum.CANCEL)
                                                }
                                            }
                                        }

                                    }
                                    HibernateApi.getInstance().save(tran)
                                }
                            }
                            if (!tvd.hasPendingTransaction()) {
                                tvd.setTvdtlsWasAutoClosed(true)
                                tvd.setTvdtlsStatus(TruckVisitStatusEnum.CLOSED)
                                HibernateApi.getInstance().save(tvd)
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Exception occurred while Closing the visit/Transaction " + e)
                }
            }
        }
    }

    private UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
    private RoadManager roadManager = (RoadManager) Roastery.getBean(RoadManager.BEAN_ID)
    private static Logger LOGGER = Logger.getLogger(ITSResolveOpenVisitForTruckGateTaskInterceptor.class)
}