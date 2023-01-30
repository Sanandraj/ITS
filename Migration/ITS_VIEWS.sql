CREATE OR ALTER VIEW LT_LineOperator_VW
 AS
SELECT ShippingLineCd AS ID, ShippingLineDsc AS NAME, SCACCd AS SCAC, NULL AS BIC, NULL AS ADR1, NULL AS ADR2, NULL AS ADR3, NULL AS CITY, NULL AS STATE, NULL AS COUNTRY, NULL AS ZIPCODE, NULL AS FAX, NULL
                  AS TELNBR, NULL AS EMAIL, NULL AS CONTACT, NULL AS NOTES, NULL AS CREATED, 'MIGRATION' AS CREATOR, 'MIGRATION' AS CHANGER
FROM     centraldb.dbo.GRef_ShippingLine
WHERE  (ActiveFlg = 'Y')



  CREATE OR ALTER VIEW LT_TruckingCompany_VW
     AS
SELECT rtt.TruckerCd AS ID, rtt.TruckerDsc AS NAME, rtt.TruckerCd AS SCAC, rta.ContactNm AS CONTACT, rta.AddrLine_1 AS ADR1, rta.AddrLine_2 AS ADR2, rta.CityNm AS CITY, rta.StateCd AS STATE, CASE WHEN rta.StateCd IS NOT NULL
                  AND rta.CountryCd IS NULL THEN 'US' ELSE rta.CountryCd END AS COUNTRY, rta.ZipCd AS ZIPCODE, rta.FaxNum AS FAX, rta.TelephoneNum_1 AS TELNBR, rta.EMailAddr AS EMAIL, CAST(rta.RemarksTxt AS varchar(100)) AS NOTES,
                  'MIGRATION' AS CREATOR, 'MIGRATION' AS CHANGER, NULL AS CREATED, NULL AS CHANGED
FROM     dbo.LRef_TerminalTrucker AS rtt INNER JOIN
                  dbo.LRef_TerminalTruckerAddr AS rta ON rtt.TerminalTruckerId = rta.TerminalTruckerId
WHERE  (rta.AddrTypeCd = 'O') AND (LEN(rtt.TruckerCd) = 4) AND (rtt.StatusCd = 'A')


CREATE OR ALTER VIEW LT_Users_VW
     AS
SELECT LoginNm AS ID, 'N4User' AS ROLE, LastNm AS LAST_NAME, FirstNm AS FIRST_NAME, TelephoneNum AS PHONENBR, 'TERM' AS EMPLOYER_CLASS, 'ITS' AS EMPLOYER_ID, EMailAddr AS EMAIL, TelephoneNum AS FAX, NULL
                  AS CREATED, 'MIGRATION' AS CREATOR, 'MIGRATION' AS CHANGER
FROM     centraldb.dbo.GRef_User AS ru
WHERE  (DisableFlg = 'N') AND (UserTypeCd IN ('D', 'L'))


 CREATE OR ALTER VIEW LT_ShipClass_VW
  AS
 SELECT VslClassCd AS ID, VslClassDsc AS NAME, 'X' AS ACTIVE, 'X' AS ACTIVE_SPARCS, NULL AS SELF_SUSTAINING, NULL AS LOA, 'M' AS LOA_UNITS, NULL AS BEAM, 'M' AS BEAM_UNITS, NULL AS BAYS_FORWARD, NULL
                   AS BAYS_AFT, NULL AS BOW_OVERHANG, NULL AS BOW_UNITS, NULL AS STERN_OVERHANG, NULL AS STERN_UNITS, NULL AS NOTES, NULL AS CREATED, 'MIGRATION' AS CREATOR, NULL AS CHANGED,
                   'MIGRATION' AS CHANGER, 'LOLO' AS SHIP_TYPE, NULL AS BRIDGE_TO_BOW, NULL AS BRIDGE_TO_BOW_UNITS, NULL AS GRT, NULL AS NRT
 FROM     centraldb.dbo.GRef_VslClass


CREATE OR ALTER VIEW LT_ServiceCalls_VW
 AS
SELECT vs.VslSvcCd AS SRVC_ID, vs.VslSvcDsc AS SRVC_NAME, vpr.PortCd AS POINT_ID, vpr.RotationSeq AS SEQ, 'MIGRATION' AS CREATOR, 'MIGRATION' AS CHANGER
FROM     dbo.LT_VslSvc AS vs CROSS JOIN
                  dbo.LT_PortRotation AS vpr


  CREATE OR ALTER VIEW LT_Ships_VW
   AS
 SELECT VslCd AS ID, LloydsCd AS LLOYDS_ID, VslDsc AS NAME,
                       (SELECT VM.VslClassCd
                        FROM      centraldb.dbo.GRef_VslClass AS VC
                        WHERE   (VslClassCd = VM.VslClassCd)) AS SCLASS_ID,
                       (SELECT ShippingLineCd
                        FROM      centraldb.dbo.GRef_ShippingLine AS GS
                        WHERE   (VM.OperatorLineId = ShippingLineId)) AS LINE_ID, NULL AS CAPTAIN, CallSign AS RADIO_CALL_SIGN, FlagCountryCd AS COUNTRY_ID, 'X' AS ACTIVE, 'X' AS ACTIVE_SPARCS, NULL AS NOTES, 'MIGRATION' AS CREATOR,
                   'MIGRATION' AS CHANGER, NULL AS DOCUMENTATION_NBR
 FROM     centraldb.dbo.GRef_VslMaster AS VM
 WHERE  (ActiveFlg = 'Y')

CREATE OR ALTER VIEW LT_ShipVisit_VW
 AS
 SELECT DISTINCT
                   vs.VslCd + vs.VoyNum AS VESSEL_VISIT_ID, sl.ShippingLineCd AS LINE_ID, vsr.VslSvcCd AS SERVICE_ID, vm.VslCd AS SHIP_ID, vs.InboundVoyNum AS IN_VOY_NBR, vs.CallSeq AS IN_CALL_NBR,
                   vs.OutboundVoyNum AS OUT_VOY_NBR, vs.CallSeq AS OUT_CALL_NBR, 'X' AS ACTIVE, 'X' AS ACTIVE_SPARCS, vs.ArrDtTm AS ETA, CASE WHEN vs.ArrDtTmTypeCd = 'A' THEN ArrDtTm ELSE NULL END AS ATA,
                   CASE WHEN vs.DepDtTmTypeCd = 'A' THEN DepDtTm ELSE NULL END AS ATD, vs.DepDtTm AS ETD, CASE WHEN vs.DischargeCommencedDtTm IS NOT NULL
                   THEN vs.DischargeCommencedDtTm ELSE vs.LoadingCommencedDtTm END AS ARRIVED, vs.DischargeFinishedDtTm AS DISCHARGED, CASE WHEN vs.DischargeCommencedDtTm IS NOT NULL
                   THEN vs.DischargeCommencedDtTm ELSE vs.LoadingCommencedDtTm END AS WORK_START,
                   CASE WHEN vs.DischargeFinishedDtTm > vs.LoadingFinishedDtTm THEN vs.DischargeFinishedDtTm ELSE vs.LoadingFinishedDtTm END AS WORK_COMPLETE, vs.BerthCd AS BERTH, vs.VslSideCd AS SHIP_SIDE_TO, NULL AS NOTES,
                   vs.CreateDtTm AS CREATED, 'MIGRATION' AS CREATOR, NULL AS CHANGED, 'MIGRATION' AS CHANGER, NULL AS EMPTY_PICKUP, vs.LastRcvgCutOffDtTm AS CARGO_CUTOFF, vs.LastRcvgCutOffDtTm AS REEFER_CUTOFF,
                   CASE WHEN vs.DischargeCompletedDtTm > vs.LoadingCompletedDtTm THEN vs.DischargeCompletedDtTm ELSE vs.LoadingCompletedDtTm END AS COMPLETED, vs.ArrDtTm AS PUBLISHED_ETA, vs.DepDtTm AS PUBLISHED_ETD,
                   vs.DischargeCompletedDtTm AS DISCHARGE_COMPLETED, vs.LastRcvgCutOffDtTm AS HAZARDOUS_CUTOFF, vs.VoyNum AS CALL_YEAR, vs.VslScheduleId, vs.CallSeq, vs.VslCd, vs.VoyNum, vs.OutboundVoyNum,
                   vs.Export1STRcvDtTm AS BEGIN_RECEIVE
 FROM     dbo.LT_VslSchedule AS vs INNER JOIN
                   centraldb.dbo.GRef_ShippingLine AS sl ON vs.OperatorLineId = sl.ShippingLineId INNER JOIN
                   dbo.LT_VslSvc AS vsr ON vs.InboundVslSvcId = vsr.VslSvcId INNER JOIN
                   centraldb.dbo.GRef_VslMaster AS vm ON vs.VslCd = vm.VslCd
 WHERE  (vs.VslStatusCd <> 'X') AND (vs.VslCd + vs.VoyNum <> 'GOVT21') AND (vs.VslCd + vs.VoyNum <> 'HOLD21')

CREATE OR ALTER VIEW LT_VesselVisitLine_VW
     AS
SELECT sl.ShippingLineCd AS LINE_ID, NULL AS CARGO_CUTOFF, NULL AS REEFER_CUTOFF, NULL AS HAZARDOUS_CUTOFF, NULL AS EMPTY_PICKUP, vsc.InboundVoyNum AS LINE_IN_VOY_NBR,
                  vsc.OutboundVoyNum AS LINE_OUT_VOY_NBR, NULL AS CREATED, 'MIGRATION' AS CREATOR, NULL AS CHANGED, 'MIGRATION' AS CHANGER, vm.VslCd AS VSL_CD, vs.VoyNum AS CALL_YEAR, vs.CallSeq AS CALL_SEQ
FROM     dbo.LT_VslScheduleCustomer AS vsc INNER JOIN
                  dbo.LT_ShipVisit_VW AS vs ON vsc.VslScheduleId = vs.VslScheduleId INNER JOIN
                  centraldb.dbo.GRef_ShippingLine AS sl ON vsc.CustomerLineId = sl.ShippingLineId INNER JOIN
                  centraldb.dbo.GRef_VslMaster AS vm ON vs.VslCd = vm.VslCd


CREATE  or alter view LT_BillOfLading_VW
AS
SELECT lbl.BLId AS GKEY, lbl.BLNum AS NBR, gsl.ShippingLineCd AS LINE_ID, lvs.SHIP_ID, lvs.VoyNum AS VOY_NBR, lvs.VESSEL_VISIT_ID, 'I' AS CATEGORY, lbl.POLCd AS LOAD_POINT_ID, lbl.PODCd AS DISCHARGE_POINT_ID,
                  lbl.PORCd AS ORIGIN, lbl.FDSTCd AS DESTINATION, lbl.CustomsRelStatusCd AS RELEASE_STATUS, lbl.FrtRelStatusCd AS LINE_STATUS, NULL AS USDA_STATUS, NULL AS RELEASE_NBR, NULL AS RELEASE_DATE,
                  lbl.LadingQty AS MANIFEST_QTY, NULL AS CREATED, 'MIGRATION' AS CREATOR, NULL AS CHANGED, 'MIGRATION' AS CHANGER
FROM     dbo.LT_BL AS lbl INNER JOIN
                  dbo.LT_ShipVisit_VW AS lvs ON lbl.VslScheduleId = lvs.VslScheduleId INNER JOIN
                  centraldb.dbo.GRef_ShippingLine AS gsl ON lbl.ShippingLineId = gsl.ShippingLineId
WHERE  (lbl.BLStatusCd = 'A') AND (gsl.ActiveFlg = 'Y') AND (lbl.DeletedFlg = 'N') AND (lvs.ETA IS NULL OR
                  lvs.ETA > GETDATE() - 30) OR
                  (lbl.BLStatusCd = 'A') AND (gsl.ActiveFlg = 'Y') AND (lbl.DeletedFlg = 'N') AND (lbl.BLId IN
                      (SELECT DISTINCT ltbl.BLId
                       FROM      dbo.LT_BLContainer AS ltbl INNER JOIN
                                         dbo.LT_ContainerUses_VW AS dmu ON ltbl.ContainerVisitId = dmu.gkey))

CREATE OR ALTER VIEW LT_BillOfLadingItems_VW
AS
SELECT lbc.BLCmdtyId AS GKEY, lbl.BLNum AS NBR, gsl.ShippingLineCd AS LINE_ID, lbc.CmdtyItemNbr AS ITEMNBR, lbc.CmdtyCd, lbc.CmdtyQty AS QTY, lbc.PkgTypeTxt AS PKGTYPE, lbc.CmdtyWgt AS CMDT_WT, NULL AS CREATED,
                  'MIGRATION' AS CREATOR, NULL AS CHANGED, 'MIGRATION' AS CHANGER
FROM     dbo.LT_BLCmdty AS lbc INNER JOIN
                  dbo.LT_BL AS lbl ON lbc.BLId = lbl.BLId INNER JOIN
                  dbo.LT_ShipVisit_VW AS lvs ON lbl.VslScheduleId = lvs.VslScheduleId INNER JOIN
                  centraldb.dbo.GRef_ShippingLine AS gsl ON lbl.ShippingLineId = gsl.ShippingLineId
WHERE  (lbl.BLStatusCd = 'A') AND (gsl.ActiveFlg = 'Y') AND (lbl.DeletedFlg = 'N')



CREATE OR ALTER VIEW LT_BillOfLadingEvents_VW
AS
SELECT lbl.BLNum AS bl_no, lcm.CustomsDispositionCd AS disp_code, lcm.AssocQty AS quantity_provided, CAST(lcm.CustomsRemarksTxt AS nvarchar(MAX)) AS notes, lcm.CustomsActionDtTm AS performed
FROM     dbo.LT_BL AS lbl INNER JOIN
                  dbo.LT_CustomsBillStatus AS lcb ON lcb.BLId = lbl.BLId AND lcb.BLNum = lbl.BLNum INNER JOIN
                  dbo.LT_CustomsMsg AS lcm ON lcm.CustomsBillStatusId = lcb.CustomsBillStatusId AND lcm.PlaceOfTransaction IN ('USLGB', 'USLAX')


CREATE OR ALTER VIEW LT_ChassisUses_VW
AS
SELECT lcv.ChassisVisitId AS GKEY, 'CHS' AS EQ_CLASS, 'X' AS ACTIVE_SPARCS, 'M' AS CATEGORY, lcv.ChassisPrefixCd + lcv.ChassisNum + CAST(ISNULL(lcv.ChassisChkDigit, '') AS varchar) AS EQUIPMENT_ID, 'E' AS STATUS, NULL
                  AS EQUIPMENT_LENGTH, NULL AS EQUIPMENT_TYPE, 'NA' AS EQUIPMENT_HEIGHT, lcv.ChassisSzCd + lcv.ChassisTypeCd AS iso_code, gsl.ShippingLineCd AS owner_id, 'Y' AS loc_type, 'T' AS ARR_LOC_TYPE, NULL
                  AS ARR_LOC_ID, NULL AS ARR_POS_ID, ltta.TruckerCd AS ARR_VISIT_ID, 'T' AS DEP_LOC_TYPE, NULL AS ACCESSORY_NUMBER, 'T' AS IN_LOC_TYPE, NULL AS IN_LOC_ID, NULL AS IN_POS_ID, NULL AS IN_VISIT_ID,
                  lcseh.SvcEventDtTm AS IN_TIME, NULL AS loc_id, LCP.SlotNum AS pos_id
FROM     dbo.LT_ChassisVisit AS lcv INNER JOIN
                  dbo.LT_ChassisPos AS LCPA ON LCPA.ChassisVisitId = lcv.ChassisVisitId AND LCPA.ChassisPosTypeCd = 'A' LEFT OUTER JOIN
                  dbo.LRef_TerminalTrucker AS ltta ON ltta.TerminalTruckerId = LCPA.TerminalTruckerId INNER JOIN
                  centraldb.dbo.GRef_ShippingLine AS gsl ON gsl.ShippingLineId = lcv.OwnerLineId INNER JOIN
                  dbo.LT_ChassisArrDepLog AS lcadlog ON lcadlog.ChassisVisitId = lcv.ChassisVisitId INNER JOIN
                  dbo.LT_ChassisSvcEventHistory AS lcseh ON lcseh.ChassisSvcEventHistoryId = lcadlog.ArrSvcEventHistoryId INNER JOIN
                  dbo.LT_ChassisPos AS LCP ON LCP.ChassisPosId = lcv.CurrPosId LEFT OUTER JOIN
                  dbo.LT_ChassisAssocInfo AS LCA ON LCA.ChassisVisitId = lcv.ChassisVisitId
WHERE  (lcv.ChassisPrefixCd IS NOT NULL) AND (lcv.VisitStatusCd = 'O') AND (NOT EXISTS
                      (SELECT 1 AS Expr1
                       FROM      dbo.LT_ChassisAssocInfo AS LCA INNER JOIN
                                         dbo.LT_ChassisAssocDetail AS LCAD ON LCAD.ChassisAssocInfoId = LCA.ChassisAssocInfoId AND LCAD.EquipCatCd = 'CN'
                       WHERE   (LCA.ChassisVisitId = lcv.ChassisVisitId)))


CREATE OR ALTER VIEW LT_RailCars_VW
 AS
SELECT RailcarTypeCd AS TYPE_ID, RailcarNum AS NBR, NULL AS OWNER_ID, CarLoadLimit AS SAFE_WEIGHT, CASE WHEN lrrm.CarLoadLimitUOM = 2 THEN 'LB' ELSE 'KG' END AS SAFE_UNIT, NULL AS STATUS, NULL AS LENGTH, NULL
                  AS LENGTH_UNIT, NULL AS CREATED, NULL AS CHANGED
FROM     dbo.LRef_RailcarMaster AS LRRM


CREATE OR ALTER VIEW LT_RailcarTypes_VW
 AS
SELECT RailcarTypeCd AS ID, RailcarTypeCd AS NAME, 'A' AS STATUS, RailcarTypeCd AS DESCRIPTION, NULL AS CAR_TYPE, 4 AS MAX_20S, 2 AS MAX_TIER, NULL AS FLOOR_HEIGHT, NULL AS HEIGHT_UNIT, NULL AS FLOOR_LENGTH, NULL
                  AS LENGTH_UNIT, NULL AS TARE_WEIGHT, NULL AS TARE_UNIT, CarLoadLimit AS SAFE_WEIGHT, CASE WHEN lrrc.CarLoadLimitUOM = 2 THEN 'LB' ELSE 'KG' END AS SAFE_UNIT, NULL AS HIGH_SIDE, NULL AS CREATED,
                  'MIGRATION' AS CREATOR, NULL AS CHANGED, 'MIGRATION' AS CHANGER
FROM     dbo.LRef_RailcarType AS lrrc


CREATE OR ALTER VIEW LT_RailcarVisits_VW
 AS
SELECT lrc.RailcarNum AS RCAR_NBR, grr.RRCompanyCd AS IN_RR_ID, lrc.RailcarOrderNbr AS IN_SEQ, lrc.OriginHubCd AS LOAD_POINT, lrc.DestHubCd AS DISCHARGE_POINT, NULL AS POINT_ID, NULL AS CREATED, NULL
                  AS CREATOR, NULL AS CHANGED, NULL AS CHANGER, lts.TrainScheduleId AS GKEY, NULL AS SEAL_NBR1, NULL AS SEAL_NBR2
FROM     dbo.LT_Railcar AS lrc INNER JOIN
                  dbo.LT_TrainSchedule AS lts ON lts.TrainScheduleId = lrc.TrainScheduleId INNER JOIN
                  dbo.LT_TrainSvc AS ltsvc ON ltsvc.TrainSvcId = lts.TrainSvcId INNER JOIN
                  centraldb.dbo.GRef_RRCompany AS grr ON grr.RRCompanyId = ltsvc.RRCompanyId

  CREATE OR ALTER VIEW LT_TrainVisits_VW
    AS
 SELECT ts.TrainNum + '_' + CAST(ts.TrainScheduleId AS varchar) AS TRAIN_ID, 'Y' AS ACTIVE, 'Y' AS ACTIVE_SPARCS, grr.RRCompanyCd AS RR_ID, NULL AS TRACK, CASE WHEN tsvc.BoundInd = 'I' THEN 'IN' ELSE 'OUT' END AS DIRECTION,
                   tar.StartDtTm AS ETA, tar.EndDtTm AS ETD, CASE WHEN ts .ArrRelDtTmTypeCd = 'A' THEN tar.StartDtTm ELSE NULL END AS ATA, CASE WHEN ts .ArrRelDtTmTypeCd = 'A' THEN tar.EndDtTm ELSE NULL END AS ATD,
                   CASE WHEN tsvc.BoundInd = 'I' THEN tsec.LiftFinishDtTm ELSE NULL END AS DISCHARGED, NULL AS NOTES, NULL AS CREATED, NULL AS CREATOR, NULL AS CHANGED, NULL AS CHANGER, ts.RRTrainNum AS RR_TRAIN_ID, NULL
                   AS LINE_ID, 'TRAIN_SRVC' AS RAIL_SRVC_ID, ts.TrainScheduleId
 FROM     dbo.LT_TrainSchedule AS ts INNER JOIN
                   dbo.LT_TrainSvc AS tsvc ON tsvc.TrainSvcId = ts.TrainSvcId INNER JOIN
                   centraldb.dbo.GRef_RRCompany AS grr ON grr.RRCompanyId = tsvc.RRCompanyId INNER JOIN
                       (SELECT MIN(StartDtTm) AS StartDtTm, MAX(EndDtTm) AS EndDtTm, TrainScheduleId
                        FROM      dbo.LT_TrainArrRelDtTm
                        GROUP BY TrainScheduleId) AS tar ON tar.TrainScheduleId = ts.TrainScheduleId LEFT OUTER JOIN
                       (SELECT MIN(LiftStartDtTm) AS LiftStartDtTm, MAX(LiftFinishDtTm) AS LiftFinishDtTm, TrainScheduleId
                        FROM      dbo.LT_TrainSection
                        GROUP BY TrainScheduleId) AS tsec ON tsec.TrainScheduleId = ts.TrainScheduleId
 WHERE  (ts.TrainStatusCd <> 'X')


CREATE OR ALTER VIEW LT_Equipment_VW
AS
SELECT 'CTR' AS EQ_CLASS, lcm.ContainerPrefixCd + lcm.ContainerNum + lcm.ContainerChkDigit AS EQ_NUMBER, gsl.ShippingLineCd AS OWNER, gsl.ShippingLineCd AS OPERATOR, gcc.ISOCd AS ISO_CODE, gcc.ContainerSzCd AS EQSZ_ID,
                  gcc.ContainerTypeCd AS EQTP_ID, CASE WHEN lcm.MaxGrossWgtUOM = 1 THEN lcm.MaxGrossWgt WHEN lcm.MaxGrossWgtUOM = 2 THEN lcm.MaxGrossWgt / 2.2046 ELSE NULL END AS SAFE_WEIGHT, 'KG' AS SAFE_UNITS,
                  CASE WHEN lcm.TareWgtUOM = 1 THEN lcm.TareWgt WHEN lcm.TareWgtUOM = 2 THEN lcm.TareWgt / 2.2046 ELSE NULL END AS TARE_WEIGHT, 'KG' AS TARE_UNITS, NULL AS service_code, NULL AS CREATED,
                  'MIGRATION' AS CREATOR, NULL AS CHANGED, 'MIGRATION' AS CHANGER
FROM     dbo.LRef_ContainerMaster AS lcm INNER JOIN
                  dbo.LT_ContainerVisit AS lcv ON ISNULL(lcv.ContainerPrefixCd, 'X') = ISNULL(lcm.ContainerPrefixCd, 'X') AND lcv.ContainerNum = lcm.ContainerNum INNER JOIN
                  centraldb.dbo.GRef_ShippingLine AS gsl ON gsl.ShippingLineId = lcv.OwnerLineId INNER JOIN
                  centraldb.dbo.GRef_ContainerCode AS gcc ON gcc.ContainerSzCd = lcv.ContainerSzCd AND gcc.ContainerTypeCd = lcv.ContainerTypeCd AND gcc.ContainerHgtCd = lcv.ContainerHgtCd
WHERE  (lcv.VisitStatusCd = 'O')

CREATE OR ALTER VIEW LT_ContainerUseHaz_VW
AS
SELECT lcv.ContainerPrefixCd + lcv.ContainerNum + lcv.ContainerChkDigit AS ContainerNum, lcv.ContainerVisitId, NULL AS SEQ, LTCH.EmergencyContactNum AS CONTACT_PHONE, LTCH.IMOClassCd AS IMDG_ID, NULL AS DESCRIPTION,
                  LTCH.EmergencyContactNm AS EMERGENCY_CONTACT, LTCH.EMSNum AS EMS_NBR, LTCH.FlashPoint AS FLASH_POINT, CASE WHEN FlashPointUOM = 6 THEN 'C' WHEN FlashPointUOM = 5 THEN 'F' ELSE NULL
                  END AS FLASH_POINT_UNITS, LTCH.IMOCdPageNum AS IMDG_PAGE, NULL AS INHALATION_ZONE, LTCH.LimitedQtyFlg AS LIMITED_QTY_FLAG, LTCH.MarinePollutantFlg AS MARINE_POLLUTANT, LTCH.MFAGNum AS MFAG_NBR,
                  LTCH.PkgTypeTxt AS PACKAGE_TYPE, LTCH.PackingGrpCd AS PACKING_GROUP, LTCH.ProperShippingNm AS PROPER_NAME, LTCH.HazmatQty AS QTY, NULL AS TECHNICAL_NAME, LTCH.UNNum AS UNDG_NBR,
                  LTCH.HazmatNetWgt AS WEIGHT, 'KG' AS WEIGHT_UNITS, NULL AS CREATED, 'MIGRATION' AS CREATOR, NULL AS CHANGED, 'MIGRATION' AS CHANGER, NULL AS APPROVED
FROM     dbo.LT_ContainerHazmat AS LTCH LEFT OUTER JOIN
                  dbo.LT_ContainerVisit AS lcv ON LTCH.ContainerVisitId = lcv.ContainerVisitId
WHERE  (lcv.VisitStatusCd IN ('O', 'P'))


CREATE OR ALTER VIEW LT_ContainerUses_VW
AS
SELECT lcv.ContainerVisitId AS gkey, 'X' AS active_sparcs, 'CTR' AS eq_class,
                  CASE WHEN lcs.ContainerStatusCd = 'S' THEN 'T' WHEN lcs.ContainerStatusCd = 'U' THEN 'D' WHEN lcs.ContainerStatusCd = 'I' THEN 'I' WHEN lcs.ContainerStatusCd = 'M' THEN 'M' WHEN lcs.ContainerStatusCd = 'X' THEN 'E' ELSE NULL
                  END AS category, lcv.ContainerPrefixCd + lcv.ContainerNum + lcv.ContainerChkDigit AS equipment_id, lcs.FullEmptyCd AS status, gcc.ISOCd AS iso_code, gsl.ShippingLineCd AS owner_id,
                  CASE WHEN lcp.ContainerPosTypeCd = 'Y' THEN 'Y' WHEN lcp.ContainerPosTypeCd = 'A' AND lcp.CarrierTypeCd = 'V' THEN 'V' WHEN lcp.ContainerPosTypeCd = 'A' AND lcp.CarrierTypeCd = 'R' THEN 'R' ELSE '' END AS loc_type,
                  CASE WHEN lcs.ContainerStatusCd = 'I' THEN
                      (SELECT LTVS.VslCd + LTVS.VoyNum
                       FROM      LT_VslSchedule LTVS
                       WHERE   LTVS.VslScheduleId = lcp.VslScheduleId) ELSE NULL END AS vessel_visit_id, NULL AS arr_loc_type, NULL AS arr_loc_id, NULL AS arr_pos_id, NULL AS arr_visit_id,
                      (SELECT CarrierTypeCd
                       FROM      dbo.LT_ContainerPos AS lcp
                       WHERE   (ContainerVisitId = lcv.ContainerVisitId) AND (ContainerPosTypeCd = 'D')) AS dep_loc_type,
                      (SELECT CASE WHEN lcp.CarrierTypeCd = 'V' THEN
                                             (SELECT LTVS.VslCd + LTVS.VoyNum
                                              FROM      LT_VslSchedule LTVS
                                              WHERE   LTVS.VslScheduleId = lcp.VslScheduleId) WHEN lcp.CarrierTypeCd = 'R' THEN
                                             (SELECT LTVS.TRAIN_ID
                                              FROM      LT_TrainVisits_VW LTVS
                                              WHERE   LTVS.TrainScheduleId = lcp.TrainScheduleId) ELSE NULL END AS Expr1
                       FROM      dbo.LT_ContainerPos AS lcp
                       WHERE   (ContainerVisitId = lcv.ContainerVisitId) AND (ContainerPosTypeCd = 'D')) AS dep_loc_id,
                      (SELECT CASE WHEN lcp.CarrierTypeCd = 'V' THEN
                                             (SELECT LTVS.VslCd + LTVS.VoyNum
                                              FROM      LT_VslSchedule LTVS
                                              WHERE   LTVS.VslScheduleId = lcp.VslScheduleId) ELSE NULL END AS Expr1
                       FROM      dbo.LT_ContainerPos AS lcp
                       WHERE   (ContainerVisitId = lcv.ContainerVisitId) AND (ContainerPosTypeCd = 'D')) AS dep_visit_id, NULL AS dep_pos_id, CASE WHEN lcod.ODUOM = 'A' THEN 2.54 * lcod.ODOnBack ELSE lcod.ODOnBack END AS oog_back_cm,
                  CASE WHEN lcod.ODUOM = 'A' THEN 2.54 * lcod.ODOnFront ELSE lcod.ODOnFront END AS oog_front_cm, CASE WHEN lcod.ODUOM = 'A' THEN 2.54 * lcod.ODOnLeft ELSE lcod.ODOnLeft END AS oog_left_cm,
                  CASE WHEN lcod.ODUOM = 'A' THEN 2.54 * lcod.ODOnRight ELSE lcod.ODOnRight END AS oog_right_cm, CASE WHEN lcod.ODUOM = 'A' THEN 2.54 * lcod.ODOnTop ELSE lcod.ODOnTop END AS oog_top_cm,
                  CASE WHEN lcs.ContainerStatusCd = 'X' THEN
                      (SELECT CASE WHEN ContainerVgmwgtUOM = 1 THEN ContainerVgmwgt WHEN ContainerVgmwgtUOM = 2 THEN ContainerVgmwgt / 2.2046 ELSE NULL END AS weight
                       FROM      LT_ContainerVGM lcvg
                       WHERE   lcvg.ContainerVisitId = lcv.ContainerVisitId) ELSE
                      (SELECT CASE WHEN GrossWgtUOM = 1 THEN GrossWgt WHEN GrossWgtUOM = 2 THEN GrossWgt / 2.2046 ELSE NULL END AS weight
                       FROM      LT_ContainerCondition lcc
                       WHERE   lcc.ContainerVisitId = lcv.ContainerVisitId) END AS gross_weight, CASE WHEN lcs.ContainerStatusCd = 'X' THEN
                      (SELECT CASE WHEN GrossWgtUOM = 1 THEN GrossWgt WHEN GrossWgtUOM = 2 THEN GrossWgt / 2.2046 ELSE NULL END AS weight
                       FROM      LT_ContainerCondition lcc
                       WHERE   lcc.ContainerVisitId = lcv.ContainerVisitId) ELSE NULL END AS cg_weight, 'KG' AS gross_units, lcad1.EquipPrefixCd + lcad1.EquipNum + CAST(ISNULL(lcad1.EquipChkDigit, '') AS varchar) AS chassis_number,
                  lcad2.EquipPrefixCd + lcad2.EquipNum + CAST(ISNULL(lcad2.EquipChkDigit, '') AS varchar) AS accessory_number, lcs.CmdtyCd AS commodity_description,
                  CASE WHEN ltcr.ReeferTemperatureUOM = 6 THEN ltcr.ReeferTemperature WHEN ltcr.ReeferTemperatureUOM = 5 THEN (ltcr.ReeferTemperature - 32) * 5 / 9 ELSE NULL END AS temp_required_c, ltcr.VentSetting AS VENT_REQUIRED,
                  CASE WHEN ltcr.VentSettingUOM = 7 THEN '%' WHEN ltcr.VentSettingUOM = 8 THEN 'CMH' ELSE NULL END AS VENT_UNITS, lcr.POLCd AS origin, lcr.PODCd AS destination, lcr.POLCd AS pol, lcr.PODCd AS pod1, NULL AS pod2,
                  lcr.RailDestHubCd AS rail_destination, lcs.SvcOrderStatusCd AS service_status_code, lcr.ContainerGrpCd AS group_code_id, NULL AS note, NULL AS dray_status,
                      (SELECT MIN(lcseal.SealNum) AS Expr1
                       FROM      dbo.LT_ContainerSeal AS lcseal INNER JOIN
                                         dbo.LT_ContainerSealInfo AS lcsi ON lcseal.ContainerSealInfoId = lcsi.ContainerSealInfoId
                       WHERE   (lcsi.ContainerVisitId = lcv.ContainerVisitId)) AS seal_nbr1, NULL AS seal_nbr2, NULL AS seal_nbr3, NULL AS seal_nbr4, NULL AS dray_trkc_id,
                      (SELECT CarrierTypeCd
                       FROM      dbo.LT_ContainerPos AS lcp
                       WHERE   (ContainerVisitId = lcv.ContainerVisitId) AND (ContainerPosTypeCd = 'A')) AS in_loc_type,
                      (SELECT CASE WHEN lcp.CarrierTypeCd = 'V' THEN
                                             (SELECT LTVS.VslCd + LTVS.VoyNum
                                              FROM      LT_VslSchedule LTVS
                                              WHERE   LTVS.VslScheduleId = lcp.VslScheduleId) WHEN lcp.CarrierTypeCd = 'T' THEN
                                             (SELECT LRTT.TruckerCd
                                              FROM      LRef_TerminalTrucker LRTT
                                              WHERE   LRTT.TerminalTruckerId = lcp.TerminalTruckerId) WHEN lcp.CarrierTypeCd = 'R' THEN
                                             (SELECT LTVS.TRAIN_ID
                                              FROM      LT_TrainVisits_VW LTVS
                                              WHERE   LTVS.TrainScheduleId = lcp.TrainScheduleId) ELSE NULL END AS Expr1
                       FROM      dbo.LT_ContainerPos AS lcp
                       WHERE   (ContainerVisitId = lcv.ContainerVisitId) AND (ContainerPosTypeCd = 'A')) AS in_loc_id, CASE WHEN lcp.CarrierTypeCd = 'R' THEN
                      (SELECT LTRC.RailcarNum + '-' + lcp.SlotNum
                       FROM      LT_Railcar LTRC
                       WHERE   LTRC.RailcarId = lcp.RailcarId AND lcp.ContainerPosTypeCd = 'A') WHEN lcp.ContainerPosTypeCd = 'Y' THEN
                      (SELECT SlotNum
                       FROM      dbo.LT_ContainerPos AS lcp
                       WHERE   (lcp.ContainerVisitId = lcv.ContainerVisitId) AND (ContainerPosTypeCd = 'A')) ELSE lcp.SlotNum END AS in_pos_id,
                      (SELECT CASE WHEN lcp.CarrierTypeCd = 'V' THEN
                                             (SELECT LTVS.VslCd + LTVS.VoyNum
                                              FROM      LT_VslSchedule LTVS
                                              WHERE   LTVS.VslScheduleId = lcp.VslScheduleId) WHEN lcp.CarrierTypeCd = 'T' THEN
                                             (SELECT LRTT.TruckerCd
                                              FROM      LRef_TerminalTrucker LRTT
                                              WHERE   LRTT.TerminalTruckerId = lcp.TerminalTruckerId) WHEN lcp.CarrierTypeCd = 'R' THEN
                                             (SELECT LTVS.TRAIN_ID
                                              FROM      LT_TrainVisits_VW LTVS
                                              WHERE   LTVS.TrainScheduleId = lcp.TrainScheduleId) ELSE NULL END AS Expr1
                       FROM      dbo.LT_ContainerPos AS lcp
                       WHERE   (ContainerVisitId = lcv.ContainerVisitId) AND (ContainerPosTypeCd = 'A')) AS in_visit_id, CASE WHEN lcp.ContainerPosTypeCd = 'A' THEN NULL ELSE ISNULL(UCH.ContainerArrdtTm, lcp.ContainerPosStatusDtTm)
                  END AS in_time, NULL AS out_time, NULL AS out_loc_type, NULL AS out_loc_id, NULL AS out_pos_id, NULL AS out_visit_id, NULL AS created, 'MIGRATION' AS creator, NULL AS changed, NULL AS remarks, 'ITS' AS loc_id,
                  lcp.SlotNum AS pos_id,
                      (SELECT BookingNum
                       FROM      dbo.LT_Booking AS LTB
                       WHERE   (BookingId IN
                                             (SELECT BookingId
                                              FROM      dbo.LT_BookingContainer AS LTBC
                                              WHERE   (ContainerVisitId = lcv.ContainerVisitId))) AND (BookingStatusCd = 'A' OR
                                         BookingStatusCd = 'S')) AS eqo_nbr, NULL AS eqo_line_id, NULL AS eqo_sub_type, NULL AS customer, NULL AS port_gtd, NULL AS port_lfd, NULL AS port_ptd,
                  CASE WHEN lcm.TareWgtUOM = 1 THEN lcm.TareWgt WHEN lcm.TareWgtUOM = 2 THEN lcm.TareWgt / 2.2046 ELSE NULL END AS tare_weight, 'KG' AS tare_units,
                  CASE WHEN lcm.MaxGrossWgtUOM = 1 THEN lcm.MaxGrossWgt WHEN lcm.MaxGrossWgtUOM = 2 THEN lcm.MaxGrossWgt / 2.2046 ELSE NULL END AS safe_weight, 'KG' AS safe_units,
                      (SELECT DamageFlg
                       FROM      dbo.LT_ContainerCondition AS lcc
                       WHERE   (ContainerVisitId = lcv.ContainerVisitId)) AS damaged, lcr.SpecialStowCd AS shand_id, NULL AS bl_no, NULL AS service_code, UCH.FirstDeliverableDtTm AS DeliverableDate,
                  UCH.CustomsHoldRelDtTm AS CustomsReleaseDate
FROM     dbo.LT_ContainerVisit AS lcv LEFT OUTER JOIN
                  dbo.LT_ContainerReeferInfo AS ltcr ON ltcr.ContainerVisitId = lcv.ContainerVisitId AND ltcr.ReeferTemperature IS NOT NULL LEFT OUTER JOIN
                  dbo.LT_ContainerODInfo AS lcod ON lcv.ContainerVisitId = lcod.ContainerVisitId LEFT OUTER JOIN
                  dbo.LT_ContainerAssocInfo AS lcai WITH (NOLOCK) ON lcai.ContainerVisitId = lcv.ContainerVisitId LEFT OUTER JOIN
                  dbo.LT_ContainerAssocDetail AS lcad1 WITH (NOLOCK) ON lcai.ContainerAssocInfoId = lcad1.ContainerAssocInfoId AND lcad1.EquipCatCd = 'CH' LEFT OUTER JOIN
                  dbo.LT_ContainerAssocDetail AS lcad2 WITH (NOLOCK) ON lcai.ContainerAssocInfoId = lcad2.ContainerAssocInfoId AND lcad2.EquipCatCd = 'MG' INNER JOIN
                  dbo.LT_ContainerStatus AS lcs ON lcv.ContainerVisitId = lcs.ContainerVisitId INNER JOIN
                  centraldb.dbo.GRef_ContainerCode AS gcc ON lcv.ContainerHgtCd = gcc.ContainerHgtCd AND lcv.ContainerSzCd = gcc.ContainerSzCd AND lcv.ContainerTypeCd = gcc.ContainerTypeCd INNER JOIN
                  dbo.LRef_ContainerMaster AS lcm ON lcv.ContainerNum = lcm.ContainerNum AND ISNULL(lcv.ContainerPrefixCd, 'X') = ISNULL(lcm.ContainerPrefixCd, 'X') INNER JOIN
                  centraldb.dbo.GRef_ShippingLine AS gsl ON lcv.OwnerLineId = gsl.ShippingLineId INNER JOIN
                  dbo.LT_ContainerPos AS lcp ON lcv.CurrPosId = lcp.ContainerPosId INNER JOIN
                  dbo.LT_ContainerRoute AS lcr ON lcv.ContainerVisitId = lcr.ContainerVisitId LEFT OUTER JOIN
                  dbo.DM_UnitChargeHeader AS UCH ON UCH.ContainerVisitId = lcv.ContainerVisitId
WHERE  (lcv.VisitStatusCd IN ('O', 'P')) AND (lcp.ContainerPosTypeCd IN ('Y', 'A'))


CREATE OR ALTER   VIEW [dbo].[LT_EquipmentOrderHaz_VW]
AS
SELECT LTB.BookingNum AS NBR, NULL AS SEQ, LTBH.EmergencyContactNum AS CONTACT_PHONE, LTBH.IMOClassCd AS IMDG_ID, NULL AS DESCRIPTION, LTBH.EmergencyContactNm AS EMERGENCY_CONTACT, LTBH.EMSNum AS EMS_NBR,
                  LTBH.FlashPoint AS FLASH_POINT, CASE WHEN FlashPointUOM = 6 THEN 'C' WHEN FlashPointUOM = 5 THEN 'F' ELSE NULL END AS FLASH_POINT_UNITS, LTBH.IMDGCdPageNum AS IMDG_PAGE, NULL AS INHALATION_ZONE,
                  LTBH.LimitedQtyFlg AS LIMITED_QTY_FLAG, LTBH.MarinePollutantFlg AS MARINE_POLLUTANT, LTBH.MFAGNum AS MFAG_NBR, NULL AS PACKAGE_TYPE, LTBH.PackingGrpCd AS PACKING_GROUP,
                  LTBH.ProperShippingNm AS PROPER_NAME, 1 AS QTY, NULL AS TECHNICAL_NAME, LTBH.UNNum AS UNDG_NBR, NULL AS WEIGHT, NULL AS CREATED, 'MIGRATION' AS CREATOR, NULL AS CHANGED, 'MIGRATION' AS CHANGER,
                  lvs.VESSEL_VISIT_ID
FROM     dbo.LT_BookingCmdtyHazmat AS LTBH INNER JOIN
                  dbo.LT_BookingCmdty AS LTBC ON LTBC.BookingCmdtyId = LTBH.BookingCmdtyId INNER JOIN
                  dbo.LT_Booking AS LTB ON LTB.BookingId = LTBC.BookingId INNER JOIN
                  dbo.LT_ShipVisit_VW AS lvs ON lvs.VslScheduleId = LTB.VslScheduleId
WHERE  (ltb.BookingStatusCd = 'A' OR
                  ltb.BookingStatusCd = 'S') AND (ltb.DeletedFlg = 'N') and (lvs.eta > getdate()-30)



CREATE OR ALTER VIEW [dbo].[LT_EquipmentOrderPortFeeFlag_VW]
AS
SELECT LTP.BookingId AS BookingID, LTP.PortFeeRelStatusCd as ReleaseCode, LTP.BookingNum AS BkgNum,LTP.PortFeeActionDtTm AS performed, LTP.PortFeeCd as port_fee_cd, LTP.PortFeeUpdateDtTm as updated_on
FROM LT_BookingPortFee AS LTP INNER JOIN
dbo.LT_Booking AS LTB ON LTB.BookingId = LTP.BookingId INNER JOIN
                  dbo.LT_ShipVisit_VW AS lvs ON lvs.VslScheduleId = LTB.VslScheduleId
WHERE  (ltb.BookingStatusCd = 'A' OR
                  ltb.BookingStatusCd = 'S') AND (ltb.DeletedFlg = 'N') and (lvs.eta > getdate()-30) and LTP.BookingId is not null


CREATE OR ALTER VIEW [dbo].[LT_EquipmentOrderTmfFlag_VW]
AS
SELECT LTH.BookingId, LTH.TMFRelStatusCd as ReleaseCode, LTH.BookingNum AS BkgNum,LTH.TMFActionDtTm AS performed, LTH.TMFUpdateDtTm as updated_on
FROM LT_BookingTMFHold AS LTH INNER JOIN
dbo.LT_Booking AS LTB ON LTB.BookingId = LTH.BookingId INNER JOIN
                  dbo.LT_ShipVisit_VW AS lvs ON lvs.VslScheduleId = LTB.VslScheduleId
WHERE  (ltb.BookingStatusCd = 'A' OR
                  ltb.BookingStatusCd = 'S') AND (ltb.DeletedFlg = 'N') and (lvs.eta > getdate()-30) and LTH.BookingId is not null



CREATE  or alter view LT_EquipmentOrder_VW
 AS
SELECT lvs.VESSEL_VISIT_ID, NULL AS CHANGED, NULL AS CHANGEID, 'MIGRATION' AS CHANGER, NULL AS CREATED, 'MIGRATION' AS CREATOR, 0 AS CUTOFF_OVERRIDE, lb.FDSTCd AS DESTINATION,
                  lb.PODCd AS DISCHARGE_POINT_ID1, NULL AS DISCHARGE_POINT_ID2, NULL AS END_DATE, NULL AS FLEX1, lb.BookingId AS GKEY, gsl.ShippingLineCd AS LINE_ID, lb.POLCd AS LOAD_POINT_ID, lb.BookingNum AS NBR,
                  CAST(lb.RemarksTxt AS varchar(180)) AS NOTES, lb.PORCd AS ORIGIN, lb.ShipperNm AS SHIPPER, lb.VslCd AS SHIP_ID, lb.SpecialStowCd AS SPECIAL_STOW, NULL AS START_DATE, 'F' AS STATUS, 'BOOK' AS SUB_TYPE, NULL
                  AS TRUCKER_ID, lvs.OutboundVoyNum AS VOY_NBR
FROM     dbo.LT_Booking AS lb INNER JOIN
                  dbo.LT_ShipVisit_VW AS lvs ON lb.VslScheduleId = lvs.VslScheduleId INNER JOIN
                  centraldb.dbo.GRef_ShippingLine AS gsl ON lb.ShippingLineId = gsl.ShippingLineId
WHERE  (lb.BookingStatusCd = 'A' OR
                  lb.BookingStatusCd = 'S') AND (lb.DeletedFlg = 'N')
and (lvs.eta > getdate()-30 or lvs.VESSEL_VISIT_ID in ('GOVT22','HOLD22'))
union
SELECT lvs.VESSEL_VISIT_ID, NULL AS CHANGED, NULL AS CHANGEID, 'MIGRATION' AS CHANGER, NULL AS CREATED, 'MIGRATION' AS CREATOR, 0 AS CUTOFF_OVERRIDE, lb.FDSTCd AS DESTINATION,
                  lb.PODCd AS DISCHARGE_POINT_ID1, NULL AS DISCHARGE_POINT_ID2, NULL AS END_DATE, NULL AS FLEX1, lb.BookingId AS GKEY, gsl.ShippingLineCd AS LINE_ID, lb.POLCd AS LOAD_POINT_ID, lb.BookingNum AS NBR,
                  CAST(lb.RemarksTxt AS varchar(180)) AS NOTES, lb.PORCd AS ORIGIN, lb.ShipperNm AS SHIPPER, lb.VslCd AS SHIP_ID, lb.SpecialStowCd AS SPECIAL_STOW, NULL AS START_DATE, 'F' AS STATUS, 'BOOK' AS SUB_TYPE, NULL
                  AS TRUCKER_ID, lvs.OutboundVoyNum AS VOY_NBR
FROM     dbo.LT_Booking AS lb INNER JOIN
                  dbo.LT_ShipVisit_VW AS lvs ON lb.VslScheduleId = lvs.VslScheduleId INNER JOIN
                  centraldb.dbo.GRef_ShippingLine AS gsl ON lb.ShippingLineId = gsl.ShippingLineId
WHERE  (lb.BookingStatusCd = 'A' OR
                  lb.BookingStatusCd = 'S') AND (lb.DeletedFlg = 'N')
and lb.BookingNum in (select distinct eqo_nbr from LT_ContainerUses_VW where eqo_nbr is not null);


CREATE OR ALTER  VIEW [dbo].[LT_EquipmentOrderItem_VW]
AS
SELECT lft.ContainerSzCd AS EQSZ_ID, lft.ContainerTypeCd AS EQTP_ID, lft.ContainerHgtCd AS EQHT_ID,
                      (SELECT ISOCd
                       FROM      centraldb.dbo.GRef_ContainerCode AS gcc
                       WHERE   (lft.ContainerHgtCd = ContainerHgtCd) AND (lft.ContainerSzCd = ContainerSzCd) AND (lft.ContainerTypeCd = ContainerTypeCd)) AS ISO_CODE, lft.NbrOfContainersBooked AS QTY, NULL AS TALLY,
                  Lbc.CmdtyCd AS COMMODITY_CODE, CAST(Lbc.CmdtyDscTxt AS VARCHAR(45)) AS COMMODITY, ltrf.ReeferTemperature AS TEMP_REQUIRED,
                  CASE WHEN ltrf.ReeferTemperatureUOM = 6 THEN 'C' WHEN ltrf.ReeferTemperatureUOM = 5 THEN 'F' ELSE NULL END AS TEMP_UNITS, ltrf.VentSetting AS VENT_REQUIRED,
                  CASE WHEN ltrf.VentSettingUOM = 7 THEN '%' WHEN ltrf.VentSettingUOM = 8 THEN 'CMH' ELSE NULL END AS VENT_UNITS, NULL AS CREATED, 'MIGRATION' AS CREATOR, NULL AS CHANGED, 'MIGRATION' AS CHANGER, NULL
                  AS GROSS_WEIGHT, NULL AS GROSS_UNITS, NULL AS CLIENT_SZTP, NULL AS TALLY_LIMIT, ltrf.HumiditySetting AS HUMIDITY, NULL AS ACC_TYPE_ID, ltrf.CO2MaxSetting AS CO2_REQUIRED,
                  ltrf.O2MinSetting AS O2_REQUIRED, NULL AS CHANGEID, lvs.VslCd + lvs.VoyNum  AS VESSEL_VISIT_ID, lb.BookingNum AS NBR
FROM     dbo.LT_FCLTally AS lft INNER JOIN
                  dbo.LT_Booking AS lb LEFT OUTER JOIN
                  dbo.LT_ReeferSettings AS ltrf ON ltrf.BookingId = lb.BookingId AND ltrf.ReeferTemperature IS NOT NULL LEFT OUTER JOIN
                  dbo.LT_BookingCmdty AS Lbc ON Lbc.BookingId = lb.BookingId AND Lbc.CmdtyItemNbr = 1 ON lft.BookingId = lb.BookingId INNER JOIN
                  dbo.LT_ShipVisit_VW AS lvs ON lb.VslScheduleId = lvs.VslScheduleId
WHERE  (lb.BookingStatusCd = 'A' OR
                  lb.BookingStatusCd = 'S') AND (lb.DeletedFlg = 'N')



CREATE OR ALTER VIEW LT_EquipmentDeliveryOrder_VW
 AS
SELECT svo.SvcOrderId AS GKEY,svo.SvcOrderId AS SERVICE_ORDER_ID, gsl.ShippingLineCd AS LINE_ID, svo.SvcOrderNum as NBR,
'EDO' as SUB_TYPE,
 NULL AS CHANGED, NULL AS CHANGEID, 'MIGRATION' AS CHANGER, NULL AS CREATED, 'MIGRATION' AS CREATOR
from LT_SvcOrder svo
INNER JOIN centraldb.dbo.GRef_ShippingLine AS gsl ON svo.ShippingLineId = gsl.ShippingLineId
where svo.SvcOrderCatCd = 'MTYREPO'

CREATE OR ALTER VIEW LT_EquipmentDeliveryOrderItems_VW
 AS
SELECT let.EmptyTallyId as GKEY, let.SvcOrderId AS SERVICE_ORDER_ID,let.EquipSzCd AS EQSZ_ID, let.EquipTypeCd AS EQTP_ID, let.EquipHgtCd AS EQHT_ID,
(SELECT ISOCd FROM centraldb.dbo.GRef_ContainerCode AS gcc
WHERE   (let.EquipHgtCd = ContainerHgtCd) AND (let.EquipSzCd = ContainerSzCd) AND (let.EquipTypeCd = ContainerTypeCd)) AS ISO_CODE,
let.BookedQty as QTY, lvo.SvcOrderNum as NBR,
 NULL AS CHANGED, NULL AS CHANGEID, 'MIGRATION' AS CHANGER, NULL AS CREATED, 'MIGRATION' AS CREATOR
FROM LT_EmptyTally let INNER JOIN LT_SvcOrder lvo
ON let.SvcOrderId = lvo.SvcOrderId
WHERE lvo.SvcOrderCatCd = 'MTYREPO'

USE [itsdb]
GO
CREATE NONCLUSTERED INDEX WS_LT_BookingNum_IDX
ON [dbo].[LT_Booking] ([BookingNum])

GO



USE [sparcsn4]
GO
CREATE NONCLUSTERED INDEX [WS_CRG_BL_line-nbr_idx]
ON [dbo].[crg_bills_of_lading] ([nbr],[complex_gkey],[line_gkey])

GO

USE [sparcsn4]
GO
CREATE NONCLUSTERED INDEX WS_eqb_order_line_nbr_idx
ON [dbo].[inv_eq_base_order] ([sub_type],[complex_gkey],[line_gkey])
INCLUDE ([nbr])
GO


SELECT
    'ALTER INDEX ' + i.name + ' ON ' + o.name + ' REBUILD with(online=on, maxdop=1);',
    p.avg_fragmentation_in_percent AS frag
FROM sys.dm_db_index_physical_stats (DB_ID(), NULL, NULL , NULL, 'LIMITED') p
      JOIN sys.indexes AS i ON p.object_id = i.object_id AND p.index_id = i.index_id
      JOIN sys.objects AS o ON p.object_id = o.object_id
      AND p.index_type_desc != 'EXTENDED INDEX'
WHERE  p.index_id > 0 AND p.avg_fragmentation_in_percent > 30.0 and p.page_count >10
ORDER By frag desc

