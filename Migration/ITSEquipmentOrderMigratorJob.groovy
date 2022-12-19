import com.navis.argo.ArgoIntegrationEntity
import com.navis.argo.ArgoIntegrationField
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.EquipmentOrderSubTypeEnum
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.LogicalEntityEnum
import com.navis.argo.business.atoms.VentUnitEnum
import com.navis.argo.business.integration.IntegrationServiceMessage
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.*
import com.navis.external.argo.AbstractGroovyJobCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.DateUtil
import com.navis.framework.util.TransactionParms
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.message.MessageCollectorFactory
import com.navis.inventory.business.api.UnitManager
import com.navis.inventory.business.atoms.HazardPackingGroupEnum
import com.navis.inventory.business.atoms.HazardsNumberTypeEnum
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.imdg.HazardousGoods
import com.navis.inventory.business.imdg.Hazards
import com.navis.inventory.business.imdg.ImdgClass
import com.navis.orders.business.api.OrdersFinder
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.road.business.model.TruckingCompany
import com.navis.road.business.util.RoadBizUtil
import com.navis.services.business.api.EventManager
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger
import wslite.json.JSONArray
import wslite.json.JSONObject

class ITSEquipmentOrderMigratorJob extends AbstractGroovyJobCodeExtension {
    StringBuilder errorMessage = new StringBuilder()
    UnitManager manager = Roastery.getBean(UnitManager.BEAN_ID);
    def em = Roastery.getBean(EventManager.BEAN_ID);
    private static final Logger LOGGER = Logger.getLogger(this.class)


    @Override
    void execute(Map<String, Object> inParams) {
        List<IntegrationServiceMessage> ismList = getMessagesToProcess()

        try{
            for (IntegrationServiceMessage ism : ismList) {
                /*PersistenceTemplate pt = new PersistenceTemplate(getUserContext())
                pt.invoke(new CarinaPersistenceCallback() {
                    protected void doInTransaction() {*/
                errorMessage = new StringBuilder()
                String bookNbr = ism.getIsmUserString1()
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

                    try{
                        Booking booking = processBook(bookNbr, jsonObj)
                        LOGGER.warn("errorMessage "+errorMessage.toString())
                        LOGGER.warn(" bookNbr"+bookNbr +"-- booking "+booking)
                        LOGGER.warn(" MC -- "+getMessageCollector())

                        if(getMessageCollector().hasError() && getMessageCollector().getMessageCount() > 0 ){
                            errorMessage.append(getMessageCollector().toCompactString()).append("::")
                        }
                        if (errorMessage.toString().length() > 0) {
                            ism.setIsmUserString4(errorMessage.toString())
                        } else if (booking != null) {
                            ism.setIsmUserString3("true")
                        }
                        HibernateApi.getInstance().save(ism)
                    } catch (Exception e) {
                        LOGGER.warn("e "+e)
                        errorMessage.append("" + e.getMessage().take(100)).append("::")
                        ism.setIsmUserString4(errorMessage.toString())

                    }

                } else {
                    LOGGER.warn("No JSON Payload for " + ism.getIsmUserString1())
                }

            }
            /*})
        }*/
        } catch (Exception e) {
            LOGGER.warn("Exception occurred while executing ITSEquipmentOrderMigratorJob")
        }
    }

    Booking processBook(String bookNbr, JSONObject jsonObj) {
        Booking booking = null
        String line_id = jsonObj.getOrDefault("line_id", null)

        if (StringUtils.isEmpty(line_id)) {
            errorMessage.append("LINE_ID is required for equipment order " + bookNbr).append("::")
            return null
        }
        LineOperator line = LineOperator.findLineOperatorById(line_id);

        if (line == null) {
            errorMessage.append("Unknown Operator " + line_id + " for equipment order " + bookNbr).append("::")
            return null
        }

        String sub_type = jsonObj.getOrDefault("sub_type", null)
        if (StringUtils.isEmpty(sub_type)) {
            errorMessage.append("subType is required for equipment order " + bookNbr).append("::")
            return null
        }
        EquipmentOrderSubTypeEnum subType = EquipmentOrderSubTypeEnum.getEnum(sub_type);
        if (subType == null) {
            errorMessage.append("EquipmentOrderSubTypeEnum cannot be null for equipment order " + bookNbr).append("::")
            return null
        }

        String vessel_visit_id = jsonObj.getOrDefault("vessel_visit_id", null)
        CarrierVisit cv = null;
        VesselVisitDetails vvd = null;

        if (vessel_visit_id && subType != EquipmentOrderSubTypeEnum.EDO) {
            cv = CarrierVisit.findVesselVisit(ContextHelper.threadFacility, vessel_visit_id);
            if (cv == null) {
                errorMessage.append("Cannot find vessel visit \" + vessel_visit_id + \" for equipment order " + bookNbr).append("::")
                return null
            }

            vvd = VesselVisitDetails.resolveVvdFromCv(cv);
            if (vvd) {
                CarrierItinerary itinerary = vvd.cvdItinerary;
                if (itinerary == null) {
                    errorMessage.append("Vessel's itinerary cannot be null").append("::")
                    return null
                }
            }
        }

        RoutingPoint pol = null;
        ScopedBizUnit shipper = null;
        RoutingPoint pod1 = null;
        RoutingPoint pod2 = null;

        // POL
        String polId = jsonObj.getOrDefault("load_point_id", null)
        if (polId) {
            pol = RoutingPoint.resolveRoutingPointFromEncoding("UNLOCCODE", polId);
            if (pol == null) {
                pol = RoutingPoint.findRoutingPoint(polId.substring(2, polId.length()));
                if (pol == null) {
                    errorMessage.append(polId + " is not a valid POL").append("::")
                    return null
                }
            }
        }

        String podId1 = jsonObj.getOrDefault("discharge_point_id1", null)
        if (podId1) {
            pod1 = RoutingPoint.resolveRoutingPointFromEncoding("UNLOCCODE", podId1);
            if (pod1 == null) {

                pod1 = RoutingPoint.findRoutingPoint(podId1.substring(2, podId1.length()));
                if (pod1 == null) {
                    errorMessage.append(podId1 + " is not a valid POD 1").append("::")
                    return null
                }
            }
        }

        // POD2
        String podId2 = jsonObj.getOrDefault("discharge_point_id2", null)

        if (StringUtils.isNotEmpty(podId2)) {
            pod2 = RoutingPoint.resolveRoutingPointFromEncoding("UNLOCCODE", podId2);
            if (pod2 == null) {
                pod2 = RoutingPoint.findRoutingPoint(podId2.substring(2, podId2.length()));
                if (pod2 == null) {
                    errorMessage.append(podId2 + " is not a valid POD 2").append("::")
                    return null
                }
            }
        }

        String shipperId = jsonObj.getOrDefault("shipper", null)

        if (StringUtils.isNotEmpty(shipperId)) {
            Shipper newShipper = Shipper.findOrCreateShipper(shipperId, shipperId);
            if (newShipper != null) {
                shipper = ScopedBizUnit.findScopedBizUnit(shipperId, BizRoleEnum.SHIPPER);
            }
        }

        String status = jsonObj.getOrDefault("status", null)
        if (status == null) {
            errorMessage.append("Freight Kind mandatory for Order ").append("::")
            return null
        }
        FreightKindEnum freightKind = getFreightKind(status);


        Booking eqo = Booking.findBookingByUniquenessCriteria(bookNbr, line, cv);
        LOGGER.warn("eqo "+eqo)

        if (eqo == null){
            eqo = Booking.create(bookNbr, line, cv, freightKind, pol, pod1, pod2, shipper, null);
        } else {
            return
        }


        if (eqo == null) {
            errorMessage.append("Booking creation failed. ").append("::")
            return null
        }

        String created = jsonObj.getOrDefault("created", null)
        Date ct = DateUtil.getTodaysDate(ContextHelper.getThreadUserContext().getTimeZone())
        if (created) {
            ct = DateUtil.dateStringToDate(DateUtil.parseStringToDate(created, ContextHelper.getThreadUserContext()).format('yyyy-MM-dd HH:mm:ss'))
        }
        eqo.setEqboCreated(ct)

        String changed = jsonObj.getOrDefault("changed", null)
        Date changedDate = DateUtil.getTodaysDate(ContextHelper.getThreadUserContext().getTimeZone())
        if (changed) {
            changedDate = DateUtil.dateStringToDate(DateUtil.parseStringToDate(changed, ContextHelper.getThreadUserContext()).format('yyyy-MM-dd HH:mm:ss'))
        }
        eqo.setEqboChanged(changedDate)

        String creator = jsonObj.getOrDefault("creator", null)
        eqo.setEqboCreator(creator)

        String changer = jsonObj.getOrDefault("changer", null)
        eqo.setEqboChanger(changer)
        eqo.eqoPol = pol;
        eqo.eqoLine = line;
        eqo.eqoPod1 = pod1;
        eqo.eqoPod2 = pod2;
        String destination = jsonObj.getOrDefault("destination", null)
        String origin = jsonObj.getOrDefault("origin", null)
        eqo.eqoOrigin = origin;
        eqo.eqoDestination = destination;
        eqo.eqoVesselVisit = cv;
        eqo.setEqoEqStatus(freightKind);
        HibernateApi.instance.save(eqo);

        TruckingCompany tc = null
        String truckerId = jsonObj.getOrDefault("trucker_id", null)
        if (truckerId) {
            tc = TruckingCompany.findOrCreateTruckingCompany(truckerId);
            if (tc == null) {
                errorMessage.append("Unknown Trucking Company " + truckerId + " for EQO " + bookNbr).append("::")
                return null
            }
            if (eqo.eqoTruckCo != tc) {
                eqo.setEqoTruckCo(tc)
            }
        }

        String stow = jsonObj.getOrDefault("special_stow", null)
        if (stow) {
            SpecialStow specialStow = SpecialStow.findOrCreateSpecialStow(stow);
            if (specialStow == null) {
                errorMessage.append("Special Stow " + stow + " does not exist for EQO " + bookNbr).append("::")
                return null
            }
            eqo.setEqoSpecialStow(specialStow)
        }
        LOGGER.warn("Trying to process haz for "+eqo.getEqboNbr());
        updateHazards(eqo, jsonObj)

        updateOrderItems(eqo, jsonObj)
        HibernateApi.getInstance().save(eqo)
        RoadBizUtil.commit()
        return eqo
    }

    private FreightKindEnum getFreightKind(fk) {
        switch (fk) {
            case 'E':
            case 'M':
                return FreightKindEnum.MTY;
            case 'F':
                return FreightKindEnum.FCL;
        }

        errorMessage.append("Invalid Freight kind Status ").append("::")
        return null
    }

    private void updateOrderItems(Booking eqo, JSONObject jsonObj) {
        eqo.eqoTallyReceive = 0;
        eqo.eqoTally = 0;
        eqo.eqoQuantity = 0;
        String eqoi_count = jsonObj.getOrDefault("eqoi_count", null)

        if (StringUtils.isNotEmpty(eqoi_count)) {
            int eqoiCount = Integer.valueOf(eqoi_count)

            if (eqoiCount > 0) {
                OrdersFinder ordersFinder = Roastery.getBean(OrdersFinder.BEAN_ID);

                List<JSONArray> eqoiList = (List<JSONArray>) jsonObj.getOrDefault("eqoi-list", null)
                JSONArray array = eqoiList.get(0)
                for (int i = 0; i < array.size(); i++) {
                    JSONObject eqoiObj = array.getJSONObject(i)
                    String iso_code = eqoiObj.getOrDefault("iso_code", null)
                    if(StringUtils.isEmpty(iso_code)){
                        errorMessage.append("Not a valid Order Item ISO: "+iso_code).append("::")
                        return
                    }

                    EquipType equipmentType = EquipType.findEquipType(iso_code);
                    if (equipmentType == null){
                        errorMessage.append("Unknown Equipment Type for ISO Code " + iso_code+" for booking ").append("::")
                        return
                    }

                    Long qty = (eqoiObj.getOrDefault("qty", null) != null) ? eqoiObj.getLong("qty") : null
                    EquipmentOrderItem eqoi = ordersFinder.findEqoItemByEqType(eqo, equipmentType)
                    if (eqoi == null){
                        eqoi = EquipmentOrderItem.createOrderItem(eqo, qty, equipmentType)
                    }

                    String commodityDesc = eqoiObj.getOrDefault("commodity", null)
                    String commodity_code = eqoiObj.getOrDefault("commodity_code", null)

                    if (commodity_code) {
                        Commodity commodity = Commodity.findOrCreateCommodity(commodity_code.trim());

                        if (commodity == null){
                            errorMessage.append("Cannot find Commodity with code " + commodity_code + " for line item ").append("::")
                            return
                        }

                        if(commodity.cmdyDescription == null) {
                            if(commodityDesc != null){
                                commodity.setCmdyDescription(commodityDesc)
                            } else {
                                commodity.cmdyDescription = commodity_code.trim();
                            }
                        }
                        if (eqoi.eqoiCommodity != commodity)
                            eqoi.setEqoiCommodity(commodity)

                        if (eqoi.eqoiCommodityDesc != commodityDesc)
                            eqoi.setEqoiCommodityDesc(commodityDesc)

                    }

                    if (eqoi.eqoiTally == null) {
                        eqoi.eqoiTally = 0;
                    }
                    eqo.eqoTally += eqoi.eqoiTally;

                    //TODO tally limit set to qty?

                    Double o2required = (eqoiObj.getOrDefault("o2_required", null) != null) ? eqoiObj.getDouble("o2_required") : null
                    eqoi.eqoiO2Required = o2required;
                    Double co2_required = (eqoiObj.getOrDefault("co2_required", null) != null) ? eqoiObj.getDouble("co2_required") : null
                    eqoi.setEqoiCo2Required(co2_required)
                    if (equipmentType.isTemperatureControlled()) {
                        Double humidity = (eqoiObj.getOrDefault("humidity", null) != null) ? eqoiObj.getDouble("humidity") : null
                        eqoi.setEqoiHumidityRequired(humidity)

                        String vent_Unit = eqoiObj.getOrDefault("vent_units", null)
                        VentUnitEnum ventUnit = null;

                        if(StringUtils.isNotEmpty(vent_Unit)){
                            ventUnit = getVentilationUnit(vent_Unit)
                            eqoi.setEqoiVentUnit(ventUnit)
                        }

                        Double vent_required = eqoiObj.getOrDefault("vent_required", null) != null ? eqoiObj.getDouble("vent_required") : null
                        eqoi.eqoiVentRequired = vent_required

                        Double temperature = eqoiObj.getOrDefault("temp_required", null) != null ? eqoiObj.getDouble("temp_required") : null
                        eqoi.eqoiTempRequired = temperature;

                        if (temperature)
                            eqo.eqoHasReefers = true;

                        String acc_type_id = eqoiObj.getOrDefault("acc_type_id", null)
                        eqoi.eqoiAccType = acc_type_id;

                    }
                }
            }
        }
    }

    private void updateHazards(Booking booking, JSONObject jsonObj) {

        String haz_count = jsonObj.getOrDefault("haz_count", null)

        if (StringUtils.isNotEmpty(haz_count)) {
            int hazCount = Integer.valueOf(haz_count)

            if (hazCount > 0) {
                List<JSONArray> hazList = (List<JSONArray>) jsonObj.getOrDefault("hazard-list", null)
                if (hazList != null && hazList.size() > 0) {
                    JSONArray jarray = hazList.get(0)
                    Hazards hazards = Hazards.createHazardsEntity()
                    booking.attachHazards(hazards)
                    for (int i = 0; i < jarray.size(); i++) {
                        JSONObject hazObj = jarray.getJSONObject(i)
                        if (hazObj != null) {
                            String imdg_id = hazObj.getOrDefault("imdg_id", null)
                            String unNbr = hazObj.getOrDefault("undg_nbr", null)
                            try {
                                ImdgClass imdg_ = null;
                                if (imdg_id != null) {
                                    imdg_ = ImdgClass.getEnum(imdg_id);
                                } else {
                                    continue
                                }

                                HazardsNumberTypeEnum hazEnum = HazardsNumberTypeEnum.NA;

                                if (unNbr != null) {
                                    HazardousGoods hazGoods = HazardousGoods.findHazardousGoods(unNbr);
                                    if (hazGoods != null && hazGoods.getHzgoodsNbrType() != null) {
                                        hazEnum = hazGoods.getHzgoodsNbrType();
                                    }
                                }

                                HazardItem hi = hazards.addHazardItem(imdg_, unNbr, hazEnum);
                                Long qty = (hazObj.getOrDefault("qty", null) != null) ? hazObj.getLong("qty") : null
                                hi.setHzrdiQuantity(qty)

                                String proper_name = hazObj.getOrDefault("proper_name", null)
                                hi.setHzrdiProperName(proper_name)
                                String imdg_page = hazObj.getOrDefault("imdg_page", null)
                                hi.setHzrdiPageNumber(imdg_page)

                                String ems_nbr = hazObj.getOrDefault("ems_nbr", null)
                                hi.setHzrdiEMSNumber(ems_nbr)

                                String package_type = hazObj.getOrDefault("package_type", '')
                                hi.setHzrdiPackageType(package_type);
                                String emergency_contact = hazObj.getOrDefault("emergency_contact", '')
                                String contact_phone = hazObj.getOrDefault("contact_phone", '')
                                hi.hzrdiEmergencyTelephone = emergency_contact.trim().take(20);
                                hi.hzrdiPlannerRef = contact_phone.trim()

                                String mfag_nbr = hazObj.getOrDefault("mfag_nbr", null)
                                String fp = hazObj.getOrDefault("flash_point", null)
                                Double flash_point = null;
                                if (fp != null && fp.length() > 0) {
                                    flash_point = Double.parseDouble(fp)
                                }
                                String limited_qty_flag = hazObj.getOrDefault("limited_qty_flag", null)
                                String marine_pollutant = hazObj.getOrDefault("marine_pollutant", null)
                                String packing_group = hazObj.getOrDefault("packing_group", null)
                                hi.setHzrdiMFAG(mfag_nbr)
                                hi.setHzrdiFlashPoint(flash_point)
                                Boolean limitedQuantity_ = (limited_qty_flag && limited_qty_flag == 'X');
                                if (hi.hzrdiLtdQty != limitedQuantity_) {
                                    hi.hzrdiLtdQty = limitedQuantity_;
                                }
                                Boolean mpFlag = (marine_pollutant && marine_pollutant == 'Y');
                                if (hi.hzrdiMarinePollutants != mpFlag)
                                    hi.hzrdiMarinePollutants = mpFlag;

                                HazardPackingGroupEnum pGrp = null;
                                if (packing_group) {
                                    pGrp = HazardPackingGroupEnum.getEnum(packing_group);

                                    if (hi.hzrdiPackingGroup != pGrp)
                                        hi.hzrdiPackingGroup = pGrp;
                                }
                                HibernateApi.getInstance().save(hi)
                            } catch (Exception e) {
                                errorMessage.append("Exception while adding Haz Item " + e).append("::")
                                LOGGER.error("Exception occurred while adding Haz " + e)
                            }
                        }
                    }
                }
            }
        }
    }

    private VentUnitEnum getVentilationUnit(ventilationUnit) {
        if (ventilationUnit == '' || ventilationUnit == null || ventilationUnit == ' ' || ventilationUnit == 'null')
            return null;

        switch (ventilationUnit) {
            case '%': return VentUnitEnum.PERCENTAGE;
            case 'CMH': return VentUnitEnum.CUBIC_M_HOUR;
            case 'CFM': return VentUnitEnum.CUBIC_FT_MIN;
        }
        errorMessage.append("Dont know how to map ventilation unit " + ventilationUnit).append("::")
        return null
    }

    List<IntegrationServiceMessage> getMessagesToProcess() {
        DomainQuery domainQuery = QueryUtils.createDomainQuery(ArgoIntegrationEntity.INTEGRATION_SERVICE_MESSAGE)
        domainQuery.addDqPredicate(PredicateFactory.eq(ArgoIntegrationField.ISM_ENTITY_CLASS, LogicalEntityEnum.BKG));
        domainQuery.addDqPredicate(PredicateFactory.eq(ArgoIntegrationField.ISM_USER_STRING3, "false"))
        //        .addDqPredicate(PredicateFactory.isNotNull(ArgoIntegrationField.ISM_USER_STRING4))
        domainQuery.addDqPredicate(PredicateFactory.isNull(ArgoIntegrationField.ISM_FIRST_SEND_TIME))
        domainQuery.addDqOrdering(Ordering.asc(ArgoIntegrationField.ISM_CREATED))
                .setDqMaxResults(5000)

        return HibernateApi.getInstance().findEntitiesByDomainQuery(domainQuery)
    }
}
