import com.navis.argo.ArgoIntegrationEntity
import com.navis.argo.ArgoIntegrationField
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.*
import com.navis.argo.business.integration.IntegrationServiceMessage
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.Commodity
import com.navis.argo.business.reference.LineOperator
import com.navis.argo.business.reference.RoutingPoint
import com.navis.cargo.business.model.BillOfLading
import com.navis.cargo.business.model.BlItem
import com.navis.cargo.business.model.BlRelease
import com.navis.cargo.business.model.PackageType
import com.navis.edi.business.entity.EdiReleaseMap
import com.navis.external.argo.AbstractGroovyJobCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.DateUtil
import com.navis.framework.util.TransactionParms
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageCollectorFactory
import com.navis.road.business.util.RoadBizUtil
import com.navis.services.business.rules.Flag
import com.navis.services.business.rules.FlagType
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.vessel.business.schedule.VesselVisitLine
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger
import wslite.json.JSONArray
import wslite.json.JSONObject

class ITSBillOfLadingMigratorJob_2 extends AbstractGroovyJobCodeExtension {
    StringBuilder errorMessage = new StringBuilder()
    private static final Logger LOGGER = Logger.getLogger(this.class)
    final String BL_LINE_HOLD = 'FREIGHT_HOLD'
    final String CUSTOMS_DEFAULT_HOLD = 'CUSTOMS_HOLD'

    @Override
    void execute(Map<String, Object> inParams) {
        List<Serializable> ismList = getMessagesToProcess()
        LOGGER.warn("Got Messages "+ismList.size());
        try {
            for (Serializable ismGkey : ismList) {
                IntegrationServiceMessage ism = HibernateApi.getInstance().load(IntegrationServiceMessage.class,ismGkey);
                errorMessage = new StringBuilder();
                String blNbr = ism.getIsmUserString1()
                MessageCollector mc = MessageCollectorFactory.createMessageCollector()
                TransactionParms parms = TransactionParms.getBoundParms();
                parms.setMessageCollector(mc)

                String payload = null
                if (StringUtils.isNotEmpty(ism.getIsmMessagePayload())) {
                    payload = ism.getIsmMessagePayload()
                } else if (StringUtils.isNotEmpty(ism.getIsmMessagePayloadBig())) {
                    payload = ism.getIsmMessagePayloadBig()
                }
                if (StringUtils.isNotEmpty(payload)) {
                    JSONObject jsonObj = new JSONObject(payload);

                    try {
                        BillOfLading bol = processBl(blNbr, jsonObj)
                        LOGGER.warn("errorMessage " + errorMessage.toString())
                        LOGGER.warn(" blNbr" + blNbr + "-- bol " + bol)

                        if (getMessageCollector().hasError() && getMessageCollector().getMessageCount() > 0) {
                            errorMessage.append(getMessageCollector().toCompactString()).append("::")
                        }
                        if (errorMessage.toString().length() > 0) {
                            ism.setIsmUserString4(errorMessage.toString())
                        } else if (bol != null) {
                            ism.setIsmUserString3("true")
                        }
                        HibernateApi.getInstance().save(ism)
                    } catch (Exception e) {
                        LOGGER.warn("e " + e)
                        errorMessage.append("" + e.getMessage().take(100)).append("::")
                        ism.setIsmUserString4(errorMessage.toString())

                    }

                } else {
                    LOGGER.warn("No JSON Payload for " + ism.getIsmUserString1())
                }

            }
        } catch (Exception e) {
            LOGGER.warn("Exception occurred while executing ITSEquipmentOrderMigratorJob" + e.toString())
        }
    }

    def processBl(blNbr, jsonObj) {
        String vessel_visit_id = jsonObj.getOrDefault("vessel_visit_id", null)

        if (StringUtils.isEmpty(vessel_visit_id)) {
            errorMessage.append("vessel_visit_id is required for equipment order " + blNbr).append("::")
            return null
        }

        CarrierVisit cv;
        cv = CarrierVisit.findVesselVisit(ContextHelper.getThreadFacility(), vessel_visit_id);
        if (cv == null) {
            errorMessage.append("Cannot find vessel visit " + vessel_visit_id).append("::")
            return null
        }


        String category = jsonObj.getOrDefault("category", null)
        String line_id = jsonObj.getOrDefault("line_id", null)
        if (StringUtils.isEmpty(line_id)) {
            errorMessage.append("LINE_ID is required for BL ").append("::")
            return null
        }
        LineOperator line = LineOperator.findLineOperatorById(line_id);

        if (line == null) {
            errorMessage.append("Unknown Operator " + line_id + " for BL").append("::")
            return null
        }

        BillOfLading bl = BillOfLading.findBillOfLading(blNbr, line, cv);
        if (bl == null)
            bl = BillOfLading.findAllBillsOfLading(blNbr)[0]
        if (bl == null) {
            bl = BillOfLading.createBillOfLading(blNbr, line, UnitCategoryEnum.IMPORT, cv, ContextHelper.getThreadDataSource());
        } else {
            errorMessage.append("BL exist.").append("::")
            return bl
        }

        if (line_id != cv.cvOperator.bzuId) {
            VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(cv)
            if (vvd == null) {
                errorMessage.append("Failed to resolve vessel visit details from carrier visit.").append("::")
                return null
            }

            VesselVisitLine.findOrCreate(vvd, line)
        }


        bl.setBlCategory(UnitCategoryEnum.IMPORT)

        String origin = jsonObj.getOrDefault("origin", null)
        String destination = jsonObj.getOrDefault("destination", null)

        String discharge_point_id = jsonObj.getOrDefault("discharge_point_id", null)
        String load_point_id = jsonObj.getOrDefault("load_point_id", null)
        String release_nbr = jsonObj.getOrDefault("release_nbr", null)

        Double manifest_qty = (jsonObj.getOrDefault("manifest_qty", null) != null) ? jsonObj.getDouble("manifest_qty") : null


        String created = jsonObj.getOrDefault("created", null)
        Date ct = DateUtil.getTodaysDate(ContextHelper.getThreadUserContext().getTimeZone())
        if (created) {
            ct = DateUtil.dateStringToDate(DateUtil.parseStringToDate(created, ContextHelper.getThreadUserContext()).format('yyyy-MM-dd HH:mm:ss'))
        }
        bl.setBlCreated(ct)
        String changed = jsonObj.getOrDefault("changed", null)
        Date changedDate = DateUtil.getTodaysDate(ContextHelper.getThreadUserContext().getTimeZone())
        if (changed) {
            changedDate = DateUtil.dateStringToDate(DateUtil.parseStringToDate(changed, ContextHelper.getThreadUserContext()).format('yyyy-MM-dd HH:mm:ss'))
        }
        bl.setBlChanged(changedDate)
        String creator = jsonObj.getOrDefault("creator", null)
        bl.setBlCreator(creator)

        String changer = jsonObj.getOrDefault("changer", null)
        bl.setBlChanger(changer)
        bl.setBlCarrierVisit(cv)
        bl.setBlLineOperator(line)
        bl.setBlOrigin(origin)
        bl.setBlDestination(destination)

        RoutingPoint pod1 = null;
        if (discharge_point_id) {
            pod1 = RoutingPoint.resolveRoutingPointFromEncoding("UNLOCCODE", discharge_point_id);
            if (pod1 == null) {

                pod1 = RoutingPoint.findRoutingPoint(discharge_point_id.substring(2, discharge_point_id.length()));
                if (pod1 == null) {
                    errorMessage.append(discharge_point_id + " is not a valid POD 1").append("::")
                    return null
                }
            }
        }
        bl.blPod1 = pod1;

        RoutingPoint pol = null;
        if (load_point_id) {
            pol = RoutingPoint.resolveRoutingPointFromEncoding("UNLOCCODE", load_point_id);
            if (pol == null) {
                pol = RoutingPoint.findRoutingPoint(load_point_id.substring(2, load_point_id.length()));
                if (pol == null) {
                    //  errorMessage.append(load_point_id + " is not a valid POL").append("::")
                    //return null
                }
            }
        }
        bl.blPol = pol;
        bl.blManifestedQty = manifest_qty;

        //TODO Holds?

        updateBlItems(bl, jsonObj)
        String line_status = jsonObj.getOrDefault("line_status", null)
        Boolean blLineReleaseStatus = Boolean.FALSE;
        if(line_status && 'R'.equalsIgnoreCase(line_status)){
            applyHold(BL_LINE_HOLD, bl, null, null, null, 'Apply hold during migration');
            releaseHold(BL_LINE_HOLD, bl, null, null, null, 'Released during migration');
        }

        String releaseStatus = jsonObj.getOrDefault("release_status", null)
        releaseStatus = (releaseStatus != null && !releaseStatus.isEmpty()) ? releaseStatus : null;
        Boolean blReleaseStatus = (releaseStatus == "R");
        Boolean inBond = releaseStatus == "I"

        if (inBond) {
            bl.updateInbond(InbondEnum.INBOND)
        } else if (releaseStatus == "P" || releaseStatus == "S") { //// or sample exam
            // if release status - PTT  - set Exam status to Offsite
            bl.updateExam(ExamEnum.OFFSITE);
        }
        if (StringUtils.isNotEmpty(releaseStatus)) {
            bl.blFlexString02 = releaseStatus
        }

        String evnt_count = jsonObj.getOrDefault("evnt_count", null)

        if (StringUtils.isNotEmpty(evnt_count)) {
            int count = Integer.valueOf(evnt_count)

            if (count > 0) {
                List<JSONArray> evntList = (List<JSONArray>) jsonObj.getOrDefault("event-list", null)
                JSONArray array = evntList.get(0)
                for (int i = 0; i < array.size(); i++) {
                    JSONObject blItemObj = array.getJSONObject(i)
                    String event_id = blItemObj.getOrDefault("event_id", null)

                    String notes = blItemObj.getOrDefault("notes", null)

                    String performed = blItemObj.getOrDefault("performed", null)
                    Date pfDate = DateUtil.getTodaysDate(ContextHelper.getThreadUserContext().getTimeZone())
                    if (performed) {
                        pfDate = DateUtil.dateStringToDate(DateUtil.parseStringToDate(performed, ContextHelper.getThreadUserContext()).format('yyyy-MM-dd HH:mm:ss'))
                    }

                    String qty = blItemObj.getOrDefault("quantity_provided", 0)
                    String referenceNbr = pfDate;

                    BlRelease blRelease;
                    HashSet releaseSet = new HashSet();
                    releaseSet.add(event_id)
                    List<EdiReleaseMap> ediReleaseMapList = EdiReleaseMap.findEdiReleaseMapByEdiCode("350",
                            releaseSet, null, null, LogicalEntityEnum.BL);
                    EdiReleaseMap ediReleaseMap = null;
                    if (ediReleaseMapList != null && ediReleaseMapList.size() > 0) {
                        ediReleaseMap = ediReleaseMapList.get(0);
                    }


                    if (ediReleaseMap != null) { //if release map not found then do not create the BL Releases.
                        if (event_id.equals('CR'))
                            blRelease = BlRelease.create(bl, manifest_qty, blLineReleaseStatus, referenceNbr);
                        else
                            blRelease = BlRelease.create(bl, manifest_qty, blReleaseStatus, referenceNbr);
                    }

                    Double dispQty = Double.valueOf(qty);
                    dispQty = getDispositionQty(event_id, dispQty, jsonObj);
                    if (blRelease != null) {
                        blRelease.blrelQuantity = dispQty;

                        if (pfDate) {
                            blRelease.setBlrelPostDate(pfDate)
                        }

                        if (notes) {
                            blRelease.setBlrelNotes(notes)
                        }
                        FlagType flagType = FlagType.findFlagType(ediReleaseMap.getEdirelmapFlagTypeId());
                        blRelease.setBlrelDispositionCode(event_id);

                        blRelease.setBlrelQuantityType(ediReleaseMap.getEdirelmapModifyQty());
                        blRelease.setBlrelFlagType(flagType);


                        HibernateApi.getInstance().save(blRelease);
                    }
                }

            }
        }

        if (blReleaseStatus) {
            applyHold(CUSTOMS_DEFAULT_HOLD, bl, null, null, created, 'Applied during migration');
            releaseHold(CUSTOMS_DEFAULT_HOLD, bl, null, null, created, 'Released during migration');
        } else if(releaseStatus.isEmpty()) {
            applyHold(CUSTOMS_DEFAULT_HOLD, bl, null, null, created, 'Applied during migration');
        }

        HibernateApi.getInstance().save(bl)
        RoadBizUtil.commit()
        return bl
    }

    def updateBlItems(bl, jsonObj) {
        String item_count = jsonObj.getOrDefault("item_count", null)

        if (StringUtils.isNotEmpty(item_count)) {
            int itemCount = Integer.valueOf(item_count)

            if (itemCount > 0) {

                List<JSONArray> eqoiList = (List<JSONArray>) jsonObj.getOrDefault("item-list", null)
                JSONArray array = eqoiList.get(0)
                for (int i = 0; i < array.size(); i++) {
                    JSONObject blItemObj = array.getJSONObject(i)
                    String cmdtycd = blItemObj.getOrDefault("cmdtycd", null)
                    String itemnbr = blItemObj.getOrDefault("itemnbr", null)
                    String pkgtype = blItemObj.getOrDefault("pkgtype", null)
                    Double qty = (blItemObj.getOrDefault("qty", null) != null) ? blItemObj.getDouble("qty") : null
                    Double cmdt_wt = (blItemObj.getOrDefault("cmdt_wt", null) != null) ? blItemObj.getDouble("cmdt_wt") : null
                    if (StringUtils.isNotEmpty(cmdtycd)) {
                        Commodity cmdy = Commodity.findOrCreateCommodity(cmdtycd)
                        if (StringUtils.isNotEmpty(pkgtype)) {
                            PackageType pkgType = PackageType.findOrCreatePackageType(pkgtype, pkgtype)
                            BlItem blItem = BlItem.createBlItem(bl, cmdy, pkgType, false) // TODO check Break BULK
                            LOGGER.warn("itemnbr " + itemnbr)

                            if (StringUtils.isNotEmpty(itemnbr)) {
                                blItem.setBlitemNbr(itemnbr)
                            }
                            blItem.setBlitemQuantity(qty)
                            LOGGER.warn("blItem qty " + blItem.getBlitemQuantity())
                            if (cmdt_wt != null) {
                                blItem.setBlitemPackageWeightKg(cmdt_wt)
                            }

                        }
                    }

                }
            }
        }
    }

    public boolean applyHold(inFlagTypeId, serviceable, note, referenceId, applyDate, appliedBy) {
        String flagTypeId = inFlagTypeId;
        if (serviceable == null)
            return false;

        LogicalEntityEnum entityType = serviceable.logicalEntityType;
        FlagType flagTypeObj = FlagType.findFlagType(flagTypeId);

        if (flagTypeObj == null) {
            errorMessage.append("Cannot find hold type " + flagTypeId).append("::")
            return null
        }

        if (flagTypeObj.flgtypPurpose != FlagPurposeEnum.HOLD) {
            errorMessage.append(flagTypeId + " exists but is not a hold").append("::")
            return null
        }

        if (flagTypeObj.flgtypAppliesTo != entityType) {
            flagTypeId = flagTypeId + "_";
            flagTypeObj = FlagType.updateOrCreateFlagType(flagTypeId, "Created by DataConv", FlagPurposeEnum.HOLD, Boolean.FALSE, entityType);
        }

        if (hasActiveFlag(serviceable, flagTypeObj)) {
            return false;
        }

        if (flagTypeObj.flagReferenceIdRequired && !referenceId)
            throw new Exception(flagTypeId + " hold type requires referenceId");
        ServicesManager sm = Roastery.getBean(ServicesManager.BEAN_ID)
        sm.applyHold(flagTypeId, serviceable, referenceId, note, true)

        List<Flag> flags = (List<Flag>) flagTypeObj.findMatchingActiveFlags(serviceable, null, null, null, false);
        if (!flags.isEmpty()) {
            Flag flag = flags.get(0)
            flag.flagAppliedDate = applyDate;
            flag.flagAppliedBy = appliedBy;
        }

        return true
    }

    def releaseHold(inFlagTypeId, serviceable, note, referenceId, applyDate, appliedBy) {
        String flagTypeId = inFlagTypeId;
        if (serviceable == null)
            return false

        def entityType = serviceable.getLogicalEntityType()
        def flagTypeObj = FlagType.findFlagType(flagTypeId)

        if (flagTypeObj == null) {
            errorMessage.append("Cannot find hold type " + flagTypeId).append("::")
            return null
        }


        if (flagTypeObj.flgtypPurpose != FlagPurposeEnum.HOLD) {
            errorMessage.append(flagTypeId + " exists but is not a hold").append("::")
            return null
        }

        if (flagTypeObj.flgtypAppliesTo != entityType) {
            flagTypeId = flagTypeId + "_";
            flagTypeObj = FlagType.findFlagType(flagTypeId)
        }

        List<Flag> flags = (List<Flag>) flagTypeObj.findMatchingActiveFlags(serviceable, null, null, null, false)
        def note2 = 'Released by DM.'

        if (!flags.isEmpty()) {
            note2 = note2 + "Applied date:" + flags.get(0).flagAppliedDate
            flags.get(0).flagAppliedDate = applyDate
            flags.get(0).flagAppliedBy = appliedBy
        }

        ServicesManager sm = Roastery.getBean(ServicesManager.BEAN_ID)

        sm.applyPermission(flagTypeId, serviceable, referenceId, note ? "DM: $note . $note2" : "DM: $note2", true)

        return true
    }

    def hasActiveFlag(flagable, flagType) {
        if (flagType == null || flagType == '')
            return false

        def flags = flagType.findMatchingActiveFlags(flagable, null, null, null, true)

        return flags != []
    }

    private Double getDispositionQty(String inDispCode, Double inQty, jsonObj) {
        Boolean has1A = Boolean.FALSE;
        Boolean has1C = Boolean.FALSE;

        if (inDispCode == null) {
            return null;
        }

        String evnt_count = jsonObj.getOrDefault("evnt_count", null)

        if (StringUtils.isNotEmpty(evnt_count)) {
            int eventCount = Integer.valueOf(evnt_count)
            if (eventCount && "4E".equals(inDispCode)) {
                List<JSONArray> evntList = (List<JSONArray>) jsonObj.getOrDefault("event-list", null)
                JSONArray array = evntList.get(0)
                for (int i = 0; i < array.size(); i++) {
                    JSONObject blItemObj = array.getJSONObject(i)
                    String eventId = blItemObj.getOrDefault("event_id", null)
                    eventId = (eventId && eventId != 'null') ? eventId : null;

                    if (eventId != null && "1A".equals(eventId))
                        has1A = Boolean.TRUE;
                    if (eventId != null && "1C".equals(eventId))
                        has1C = Boolean.TRUE;

                    if (has1C && !has1A)
                        return inQty - (inQty * 2);
                    else if (!has1C && has1A) {
                        return inQty;
                    }
                }
            }
        }

        def returnQty = ['1B','1C','1J','4C']
        def mapQty = ['14','15','16','4A','4E','66','67','68','83','95']
        if (inQty <= 0 || returnQty.contains(inDispCode)){
            return inQty;
        } else if (mapQty.contains(inDispCode)){
            return inQty - (inQty*2);
        } else {
            return inQty;
        }


    }

    List<Serializable> getMessagesToProcess() {
        DomainQuery domainQuery = QueryUtils.createDomainQuery(ArgoIntegrationEntity.INTEGRATION_SERVICE_MESSAGE)
        domainQuery.addDqPredicate(PredicateFactory.eq(ArgoIntegrationField.ISM_ENTITY_CLASS, LogicalEntityEnum.BL));
        domainQuery.addDqPredicate(PredicateFactory.eq(ArgoIntegrationField.ISM_USER_STRING3, "false"))
        domainQuery.addDqPredicate(PredicateFactory.gt(ArgoIntegrationField.ISM_SEQ_NBR, 40000))
      //  domainQuery.addDqPredicate(PredicateFactory.le(ArgoIntegrationField.ISM_SEQ_NBR, 74500))
        //    domainQuery.addDqOrdering(Ordering.asc(ArgoIntegrationField.ISM_CREATED))
            //    .addDqOrdering(Ordering.desc(ArgoIntegrationField.ISM_SEQ_NBR))
       // .setDqMaxResults(5000)
        return HibernateApi.getInstance().findPrimaryKeysByDomainQuery(domainQuery)
    }
}
