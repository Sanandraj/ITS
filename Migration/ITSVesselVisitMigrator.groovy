import com.navis.argo.ArgoIntegrationEntity
import com.navis.argo.ArgoIntegrationField
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.atoms.LogicalEntityEnum
import com.navis.argo.business.integration.IntegrationServiceMessage
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Quay
import com.navis.argo.business.reference.CarrierService
import com.navis.argo.business.reference.LineOperator
import com.navis.external.framework.AbstractExtensionCallback
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.road.business.util.RoadBizUtil
import com.navis.vessel.VesselEntity
import com.navis.vessel.VesselField
import com.navis.vessel.business.atoms.BerthSideTypeEnum
import com.navis.vessel.business.atoms.VesselClassificationEnum
import com.navis.vessel.business.atoms.VesselTypeEnum
import com.navis.vessel.business.operation.Vessel
import com.navis.vessel.business.schedule.VesselVisitBerthing
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.vessel.business.schedule.VesselVisitLine
import com.navis.vessel.business.util.DateUtil
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger
import org.mule.util.DateUtils
import wslite.json.JSONArray
import wslite.json.JSONObject

/**
 * Migrator Library to import Vessel Visit from External system to N4
 */

class ITSVesselVisitMigrator extends AbstractExtensionCallback {
    StringBuilder errorMessage = new StringBuilder()

    void execute() {
        List<Serializable> ismList = getMessagesToProcess()

        for (Serializable ismGkey : ismList) {
            PersistenceTemplate pt = new PersistenceTemplate(getUserContext())
            pt.invoke(new CarinaPersistenceCallback() {
                protected void doInTransaction() {
                    IntegrationServiceMessage ism = HibernateApi.getInstance().load(IntegrationServiceMessage.class, ismGkey);
                    errorMessage = new StringBuilder()
                    String jsonPayload = null
                    if (StringUtils.isNotEmpty(ism.getIsmMessagePayload())) {
                        jsonPayload = ism.getIsmMessagePayload()
                    } else if (StringUtils.isNotEmpty(ism.getIsmMessagePayloadBig())) {
                        jsonPayload = ism.getIsmMessagePayloadBig()
                    }

                    if (StringUtils.isNotEmpty(jsonPayload)) {
                        JSONObject jsonObj = new JSONObject(jsonPayload);
                        try {
                            VesselVisitDetails vvd = processVisit(jsonObj)

                            String berth = jsonObj.getOrDefault("berth", null)
                            if (vvd != null && StringUtils.isNotEmpty(berth)) {
                                String shipSide = jsonObj.getOrDefault("ship_side_to", null)
                                updateBerthing(berth, shipSide, vvd)
                            }
                            String line_count = jsonObj.getOrDefault("line_count", null)

                            if (vvd != null && StringUtils.isNotEmpty(line_count)) {
                                int line = Integer.valueOf(line_count)

                                if (line > 0) {
                                    List<JSONArray> visit_lines = (List<JSONArray>) jsonObj.getOrDefault("visit_lines", null)
                                    JSONArray array = visit_lines.get(0)
                                    for (int i = 0; i < array.size(); i++) {
                                        JSONObject lineObj = array.getJSONObject(i)
                                        processVisitLines(vvd, lineObj)
                                    }

                                }
                            }
                            if (getMessageCollector().hasError() && getMessageCollector().getMessageCount() > 0) {
                                errorMessage.append(getMessageCollector().toCompactString()).append("::")
                            }
                            if (errorMessage.toString().length() > 0) {
                                ism.setIsmUserString4(errorMessage.toString())
                            } else if (vvd != null) {
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
            })


        }
    }

    VesselVisitDetails processVisit(JSONObject object) {
        VesselVisitDetails vvd = null
        String visitId = object.getOrDefault("vessel_visit_id", null)
        // String gkey = object.get("gkey")
        String ibVoy = object.getOrDefault("in_voy_nbr", null)


        if (StringUtils.isEmpty(visitId)) {
            errorMessage.append("vessel_visit_id is required for vessel visit.").append("::")
            return null
        }

        String shipId = object.get('ship_id')
        if (StringUtils.isEmpty(shipId)) {
            errorMessage.append("ship_id is required for vessel visit.").append("::")
            return null
        }

        Vessel vessel = Vessel.findVesselById(shipId);
        if (vessel == null) {
            errorMessage.append("Unknown vessel " + shipId + " for vessel visit " + visitId).append("::")
            return null
        }
        LOGGER.warn("visitId " + visitId)

        CarrierVisit cv = findCarrierVisitByInboundVoyage(vessel, ibVoy)
        if (!cv) {
            cv = CarrierVisit.findOrCreateVesselVisit(ContextHelper.threadFacility, visitId);
        }

        if (cv) {
            cv.setCvId(visitId)
        } else {
            errorMessage.append('Not able to create vessel visit: could be blank space in vessel id-not allowed in N4 ' + visitId).append("::")
            return null
        }

        vvd = VesselVisitDetails.resolveVvdFromCv(cv);

        String ata = object.getOrDefault("ata", null)
        String atd = object.getOrDefault("atd", null)
        String line_id = object.getOrDefault("line_id", null)
        String service_id = object.getOrDefault("service_id", null)
        String out_voy_nbr = object.getOrDefault("out_voy_nbr", null)
        String work_start = object.getOrDefault("work_start", null)
        String work_complete = object.getOrDefault("work_complete", null)
        String notes = object.getOrDefault("notes", null)
        String published_eta = object.getOrDefault("published_eta", null)
        String published_etd = object.getOrDefault("published_etd", null)
        String eta = object.getOrDefault("eta", null)
        String etd = object.getOrDefault("etd", null)
        String begin_receive = object.getOrDefault("begin_receive", null)
        String hazardous_cutoff = object.getOrDefault("hazardous_cutoff", null)
        String reefer_cutoff = object.getOrDefault("reefer_cutoff", null)
        String cargo_cutoff = object.getOrDefault("cargo_cutoff", null)
        String discharged = object.getOrDefault("discharged", null)

        Date date = null
        if (StringUtils.isNotEmpty(ata)) {
            date = getFormattedDate(ata)
            if (cv.cvATA != date) {
                cv.cvATA = date;
            }
        }

        if (StringUtils.isNotEmpty(atd)) {
            date = getFormattedDate(atd)
            if (cv.cvATD != date) {
                cv.cvATA = date;
            }
        }

        if (StringUtils.isEmpty(line_id)) {
            line_id = 'ITS'
        }

        LineOperator operator = LineOperator.findLineOperatorById(line_id);
        if (operator == null) {
            LineOperator.createLineOperator(line_id);
        }

        operator = LineOperator.findLineOperatorById(line_id);
        if (cv.cvOperator != operator)
            cv.cvOperator = operator;

        HibernateApi.getInstance().save(cv);

        if (StringUtils.isEmpty(service_id)) {
            errorMessage.append("service_id is required for vessel visit " + visitId).append("::")
            return null
        }

        CarrierService service = CarrierService.findCarrierService(service_id);
        if (service == null) {
            errorMessage.append("Unknown Carrier Service " + service_id + " for vessel visit " + visitId).append("::")
            return null
        }

        if (!service.srvcItinerary) {
            errorMessage.append("Null Itinerary for Service " + service_id + " for vessel visit " + visitId).append("::")
            return null
        }

        if (StringUtils.isEmpty(out_voy_nbr)) {
            errorMessage.append("obVoyage is required for vessel visit " + visitId).append("::")
            return null
        }
        if (vvd == null) {
            vvd = VesselVisitDetails.createVesselVisitDetails(cv, service, service.getSrvcItinerary(), vessel, ibVoy, out_voy_nbr, null, ContextHelper.getThreadDataSource())
        }

        if (StringUtils.isNotEmpty(work_start)) {
            date = getFormattedDate(work_start)
            Date ataDate = getFormattedDate(ata)
            if (ata != null && date != null && ataDate.after(DateUtils.addSeconds(date, 1))) {
                date = DateUtil.incrementByMinute(ataDate, 1);
            }
            if (vvd.vvdTimeStartWork != date)
                vvd.vvdTimeStartWork = date;
        }


        if (StringUtils.isNotEmpty(work_complete)) {
            date = getFormattedDate(work_complete)
            if (cv != null && cv.getCvATD() != null && date != null && cv.getCvATD().before(date)) {
                date = cv.getCvATD();
            }
            if (vvd.vvdTimeEndWork != date)
                vvd.vvdTimeEndWork = date;
        }

        if (StringUtils.isNotEmpty(notes)) {
            if (notes.length() > 50)
                notes = notes.substring(0, 50)

            if (vvd.vvdNotes != notes)
                vvd.vvdNotes = notes;
        }

        // Vessel Classification
        if (vessel.getVesVesselClass()?.getVesclassVesselType() == VesselTypeEnum.BARGE) {
            if (vvd.vvdClassification != VesselClassificationEnum.BARGE)
                vvd.vvdClassification = VesselClassificationEnum.BARGE;
            else if (vvd.vvdClassification != VesselClassificationEnum.DEEPSEA)
                vvd.vvdClassification = VesselClassificationEnum.DEEPSEA;
        }


        if (StringUtils.isNotEmpty(published_eta)) {
            date = getFormattedDate(published_eta)
            if (vvd == null || vvd.vvdPublishedEta != date) {
                vvd.setVvdPublishedEta(date);
            }
        }


        // Operator
        if ((vvd && vvd.getVvdBizu() != operator) || vvd == null) {
            vvd.setVvdBizu(operator);
        }
        if (StringUtils.isNotEmpty(published_etd)) {
            date = getFormattedDate(published_etd)
            if (vvd == null || vvd.vvdPublishedEtd != date) {
                vvd.setVvdPublishedEtd(date);
            }
        }

        if (StringUtils.isNotEmpty(eta)) {
            date = getFormattedDate(eta)
            if (vvd == null || vvd.cvdETA != date) {
                vvd.cvdETA = date;
            }
        }

        if (StringUtils.isNotEmpty(etd)) {
            date = getFormattedDate(etd)
            if (vvd == null || vvd.cvdETD != date) {
                vvd.cvdETD = date;
            }
            //Apply ETD to ATD - IP 441
            if(cv.cvATD == null && vvd.cvdETD != null){
                cv.cvATD = vvd.cvdETD
            }
        }

        if (StringUtils.isNotEmpty(begin_receive)) {
            date = getFormattedDate(begin_receive)
            if (vvd == null || vvd.vvdTimeBeginReceive != date) {
                vvd.vvdTimeBeginReceive = date;
            }
        }

        if (StringUtils.isNotEmpty(hazardous_cutoff)) {
            date = getFormattedDate(hazardous_cutoff)
            if (date > vvd.vvdTimeBeginReceive) {
                if (vvd == null || vvd.vvdTimeHazCutoff != date) {
                    vvd.vvdTimeHazCutoff = date;
                }
            }
        }

        if (StringUtils.isNotEmpty(reefer_cutoff)) {
            date = getFormattedDate(reefer_cutoff)
            if (date > vvd.vvdTimeBeginReceive) {
                if (vvd == null || vvd.vvdTimeReeferCutoff != date) {
                    vvd.vvdTimeReeferCutoff = date;
                }
            }

        }

        if (StringUtils.isNotEmpty(cargo_cutoff)) {
            date = getFormattedDate(cargo_cutoff)
            if (date > vvd.vvdTimeBeginReceive) {
                if (vvd == null || vvd.vvdTimeCargoCutoff != date) {
                    vvd.vvdTimeCargoCutoff = date;
                }
            }

        }

        if (StringUtils.isNotEmpty(discharged)) {
            date = getFormattedDate(discharged)
            if (date)
                use(groovy.time.TimeCategory) {
                    date = date + 6.hour
                }
            if (vvd == null || vvd.getCvdTimeDischargeComplete() != date)
                vvd.setCvdTimeDischargeComplete(date)
        }

        // VV Phase
        if (cv.cvATD == null || (cv.cvATD && cv.cvATD.after(new Date()))) {
            if (vvd.cvdETA == null && cv.cvATA == null) {
                cv.cvVisitPhase = CarrierVisitPhaseEnum.CREATED;
                cv.cvATD = null
            }
            else if ((vvd.cvdETA && cv.cvATA == null) || (vvd.cvdETA && cv.cvATA && cv.cvATA.after(new Date()))) {
                cv.cvVisitPhase = CarrierVisitPhaseEnum.INBOUND;
                cv.cvATD = null
            } else if (vvd.cvdETA && cv.cvATA && cv.cvATA.before(new Date()))
                cv.cvVisitPhase = CarrierVisitPhaseEnum.ARRIVED;
        } else {
            cv.cvVisitPhase = CarrierVisitPhaseEnum.CLOSED;
        }

        if (vvd) {
            HibernateApi.getInstance().save(vvd)
            RoadBizUtil.commit()
        }

        return vvd
    }

    void updateBerthing(String quayId, String shipSide, vvd) {
        if (StringUtils.isNotEmpty(quayId)) {
            quayId = quayId.replaceAll("[^0-9]", "")
            Quay quay = Quay.findQuay(ContextHelper.getThreadFacility(), quayId)
            if (quay != null) {
                BerthSideTypeEnum sideTo = BerthSideTypeEnum.UNKNOWN
                switch (shipSide) {
                    case 'P':
                        sideTo = BerthSideTypeEnum.PORTSIDE
                        break;
                    case 'S':
                        sideTo = BerthSideTypeEnum.PORTSIDE
                        break;
                    default:
                        sideTo = BerthSideTypeEnum.UNKNOWN
                        break
                }
                if (sideTo) {
                    VesselVisitBerthing.createBerthing(quay, vvd, sideTo, DataSourceEnum.DATA_IMPORT)
                }
            } else {
                LOGGER.error("ERROR: Invalid quay " + quayId)
            }
        }
    }

    void processVisitLines(VesselVisitDetails vvd, JSONObject lineObj) {
        String line_id = lineObj.getOrDefault("line_id", null)
        String cargo_cutoff = lineObj.getOrDefault("cargo_cutoff", null)
        String hazardous_cutoff = lineObj.getOrDefault("hazardous_cutoff", null)
        String empty_pickup = lineObj.getOrDefault("empty_pickup", null)
        String reefer_cutoff = lineObj.getOrDefault("reefer_cutoff", null)
        String line_in_voy_nbr = lineObj.getOrDefault("line_in_voy_nbr", vvd.getVvdIbVygNbr())
        String line_out_voy_nbr = lineObj.getOrDefault("line_out_voy_nbr", null)
        String created = lineObj.getOrDefault("created", null)
        String changed = lineObj.getOrDefault("changed", null)
        String creator = lineObj.getOrDefault("creator", null)
        String changer = lineObj.getOrDefault("changer", null)

        if (StringUtils.isEmpty(line_id)) {
            errorMessage.append("STATUS is required for vessel visit " + vvd.getCvdCv().getCvId() + " vv line").append("::")
            return
        }
        LineOperator operator = LineOperator.findLineOperatorById(line_id);
        if (operator == null) {
            return
        }

        VesselVisitLine vvLine = vvd.getVvlineForBizu(operator);
        if (vvLine == null) {
            vvLine = VesselVisitLine.createVesselVisitLine(vvd, operator);
        }
        Date date = null
        if (StringUtils.isNotEmpty(cargo_cutoff)) {
            date = getFormattedDate(cargo_cutoff)
            if (vvLine.vvlineTimeCargoCutoff != date) {
                vvLine.vvlineTimeCargoCutoff = date;
            }
        }

        if (StringUtils.isNotEmpty(hazardous_cutoff)) {
            date = getFormattedDate(hazardous_cutoff)
            if (vvLine.vvlineTimeHazCutoff != date) {
                vvLine.vvlineTimeHazCutoff = date;
            }
        }

        // Empty Pickup holds Begin Receive value
        if (StringUtils.isNotEmpty(empty_pickup)) {
            date = getFormattedDate(empty_pickup)
            if (vvLine.vvlineTimeBeginReceive != date) {
                vvLine.vvlineTimeBeginReceive = date;
            }
        }

        if (StringUtils.isNotEmpty(reefer_cutoff)) {
            date = getFormattedDate(reefer_cutoff)
            if (vvLine.vvlineTimeReeferCutoff != date) {
                vvLine.vvlineTimeReeferCutoff = date;
            }
        }

        if (StringUtils.isNotEmpty(line_in_voy_nbr)) {
            if (vvLine.vvlineInVoyNbr != line_in_voy_nbr) {
                vvLine.vvlineInVoyNbr = line_in_voy_nbr;
            }
        }

        if (StringUtils.isNotEmpty(line_out_voy_nbr)) {
            if (vvLine.vvlineOutVoyNbr != line_out_voy_nbr) {
                vvLine.vvlineOutVoyNbr = line_out_voy_nbr;
            }
        }

        if (StringUtils.isNotEmpty(created)) {
            date = getFormattedDate(created)
            if (vvLine.vvlineCreated != date) {
                vvLine.vvlineCreated = date;
            }
        }

        if (StringUtils.isNotEmpty(changed)) {
            date = getFormattedDate(created)
            if (vvLine.vvlineChanged != date) {
                vvLine.vvlineChanged = date;
            }
        }

        if (StringUtils.isNotEmpty(creator)) {
            if (vvLine.vvlineCreator != creator) {
                vvLine.vvlineCreator = creator;
            }
        }

        if (StringUtils.isNotEmpty(changer)) {
            if (vvLine.vvlineChanger != changer) {
                vvLine.vvlineChanger = changer;
            }
        }

        HibernateApi.getInstance().save(vvLine);

    }


    private CarrierVisit findCarrierVisitByInboundVoyage(Vessel vessel, String inboundVoyageID) {
        CarrierVisit carrierVisit = null
        DomainQuery dq = QueryUtils.createDomainQuery(VesselEntity.VESSEL_VISIT_DETAILS)
        dq.addDqPredicate(PredicateFactory.eq(MetafieldIdFactory.valueOf("vvdVessel.vesGkey"), vessel.vesGkey))
        dq.addDqPredicate(PredicateFactory.eq(VesselField.VVD_IB_VYG_NBR, inboundVoyageID))
        dq.setScopingEnabled(false)
        VesselVisitDetails vvd = (VesselVisitDetails) HibernateApi.instance.getUniqueEntityByDomainQuery(dq)
        if (vvd)
            carrierVisit = CarrierVisit.findVesselVisit(ContextHelper.threadFacility, vvd.getVesselId() + vvd.getVvdObVygNbr())
        return carrierVisit
    }

    List<Serializable> getMessagesToProcess() {
        DomainQuery domainQuery = QueryUtils.createDomainQuery(ArgoIntegrationEntity.INTEGRATION_SERVICE_MESSAGE)
        domainQuery.addDqPredicate(PredicateFactory.eq(ArgoIntegrationField.ISM_ENTITY_CLASS, LogicalEntityEnum.VV));
        domainQuery.addDqPredicate(PredicateFactory.eq(ArgoIntegrationField.ISM_USER_STRING3, "false"))
        domainQuery.addDqOrdering(Ordering.asc(ArgoIntegrationField.ISM_CREATED));

        return HibernateApi.getInstance().findPrimaryKeysByDomainQuery(domainQuery)
    }

    Date getFormattedDate(String date) {
        if (StringUtils.isNotEmpty(date) && date != "null") {
            return DateUtils.getDateFromString(date, "yyyy-MM-dd HH:mm:ss")
        }
        return null
    }

    private static final String ENTITY = 'VesselVisit'
    private static final Logger LOGGER = Logger.getLogger(this.class)
}
