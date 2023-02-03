/*
* Copyright (c) 2022 WeServe LLC. All Rights Reserved.
*
*/

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.IArgoYardUtils
import com.navis.argo.business.model.Yard
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.ObsoletableFilterFactory
import com.navis.framework.portal.query.PredicateFactory
import com.navis.spatial.BinField
import com.navis.spatial.BlockField
import com.navis.spatial.business.model.block.AbstractSection
import com.navis.spatial.business.model.block.AbstractStack
import com.navis.yard.business.model.AbstractYardBlock
import org.apache.log4j.Logger
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class ITSYardBlockMasterCallback  extends AbstractExtensionPersistenceCallback {

    @Override
    void execute(@Nullable Map inMap, @Nullable Map outMap) {

        long start = System.currentTimeMillis();
        LOG.info(" start.............................  " + start);

        String  json = "  {\n" +
                "  \"Table\": \n"

        final Yard yard = ContextHelper.getThreadYard();
        long yardAbnGkey = yard.yrdBinModel.getAbnGkey()
        List<LinkedHashMap<String, Object>> mapList = getQueryResultMap(yardAbnGkey)

        ObjectMapper objectMapper = new ObjectMapper()
        try {
            json = json +  objectMapper.writeValueAsString(mapList);
            LOG.info( " json............................. " + json );
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        json = json + "\n } \n"
        outMap.put("responseMessage", json)

        long elapsedTime = System.currentTimeMillis() - start;
        LOG.info(" - Time taken in milli Seconds to process the request: " + elapsedTime);
    }

    @Nullable
    static List<Map<String, Object>>   getQueryResultMap(@NotNull long yardAbnGkey) {
        LOG.info("  yardAbnGkey: " + yardAbnGkey);
        List<LinkedHashMap<String, Object>> resultListMap  = new ArrayList<LinkedHashMap<String, Object>>()

        DomainQuery dq = QueryUtils.createDomainQuery("AbstractYardBlock")
        dq.addDqPredicate(PredicateFactory.eq(BinField.ABN_PARENT_BIN, yardAbnGkey))
        dq.setFilter(ObsoletableFilterFactory.createShowActiveFilter());
        dq.addDqOrdering(Ordering.asc(BinField.ABN_NAME))

        IArgoYardUtils argoYardUtils = (IArgoYardUtils) Roastery.getBean("argoYardUtils");
        List<AbstractYardBlock> yardBlockList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)

        for (AbstractYardBlock abstractYardBlock : (yardBlockList as List<AbstractYardBlock>)) {
            LinkedHashMap<String, Object> resultMap  = new LinkedHashMap<String, Object>()
            String block = "L";
            if("SBL".equals(abstractYardBlock.getAbnSubType())) {
                if (argoYardUtils.isBlockWheeledBlock(abstractYardBlock)) {
                    block = "W"
                } else if (argoYardUtils.isBlockGroundedBlock(abstractYardBlock)) {
                    block = "S"
                }
            }
            String masterAbnName = abstractYardBlock.getAbnName()
            resultMap.put("YardBlockId", abstractYardBlock.getAbnGkey())
            resultMap.put("YardMasterId", 1)
            resultMap.put("YardBlockNum", masterAbnName)
            resultMap.put("YardBlockTypeCd", block)
            resultMap.put("YardZoneCd", null)

            if("S".equals(block) ) {
                List<AbstractSection> ysnSection = getYSN(abstractYardBlock.getPrimaryKey())
                if (!ysnSection.isEmpty() &&   ysnSection.size() > 0) {
                    Serializable ystGkey = ysnSection.get(0).getPrimaryKey()
                    String sectionNameFirst = ysnSection.get(0).getAbnName()
                    String sectionNameLast = ysnSection.get(ysnSection.size() - 1).getAbnName()
                    resultMap.put("BayFromNum", Long.valueOf(sectionNameFirst.replace(masterAbnName, "")))
                    resultMap.put("BayToNum", Long.valueOf(sectionNameLast.replace(masterAbnName, "")))
                    List<AbstractStack> yst = getYstIndex(ystGkey)
                    resultMap.put("MaxRowNum", yst.size())
                    resultMap.put("MaxTierNum", abstractYardBlock.getMaxTier())
                }
            } else if ("W".equals(block)) {
                List<AbstractSection> ysnSection = getYSN(abstractYardBlock.getPrimaryKey())
                if (!ysnSection.isEmpty() &&   ysnSection.size() > 0) {
                    Serializable ystGkey = ysnSection.get(0).getPrimaryKey()
                    List<AbstractStack> ystSection = getYstAbnName(ystGkey)
                    if (!ystSection.isEmpty() && ystSection.size() > 0) {
                        String stackNameFirst = ystSection.get(0).getAbnName()
                        String stackNameLast = ystSection.get(ystSection.size() - 1).getAbnName()
                        resultMap.put("BayFromNum", Long.valueOf(stackNameFirst.replace(masterAbnName, "")))
                        resultMap.put("BayToNum", Long.valueOf(stackNameLast.replace(masterAbnName, "")))
                        resultMap.put("MaxRowNum", 0)
                        resultMap.put("MaxTierNum", 0)
                    }
                }
            } else if("L".equals(block) ) {
                resultMap.put("BayFromNum", 0)
                resultMap.put("BayToNum", 0)
                resultMap.put("MaxTierNum", 0)
            }
            resultMap.put("BayDirectionCd", "A")
            resultMap.put("RowDirectionCd", null)
            resultMap.put("StraddleFlg", "N")
            resultMap.put("WhereNetTrackingFlg", "N")

            resultListMap.add(resultMap)
        }
        return resultListMap
    }

    @NotNull
    private static List<AbstractSection> getYSN(Serializable gkey) {
        DomainQuery dq = QueryUtils.createDomainQuery("AbstractSection")
        dq.addDqField(BinField.ABN_NAME)
        dq.addDqPredicate(PredicateFactory.eq(BinField.ABN_PARENT_BIN, gkey))
        dq.setFilter(ObsoletableFilterFactory.createShowActiveFilter());
        dq.addDqOrdering(Ordering.asc(BinField.ABN_NAME))

        List<AbstractSection> ysnList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        return ysnList
    }

    @NotNull
    private static List<AbstractStack> getYstIndex(Serializable gkey) {
        DomainQuery dq = QueryUtils.createDomainQuery("AbstractStack")
        dq.addDqField(BlockField.AST_COL_INDEX)
        dq.addDqPredicate(PredicateFactory.eq(BinField.ABN_PARENT_BIN, gkey))
        dq.addDqOrdering(Ordering.asc(BinField.ABN_Z_INDEX_MAX))
        dq.setFilter(ObsoletableFilterFactory.createShowActiveFilter());

        List<AbstractStack> ystIndexList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        return ystIndexList
    }


    @NotNull
    private static List<AbstractStack> getYstAbnName(Serializable gkey) {
        DomainQuery dq = QueryUtils.createDomainQuery("AbstractStack")
        dq.addDqField(BinField.ABN_NAME)
        dq.addDqPredicate(PredicateFactory.eq(BinField.ABN_PARENT_BIN, gkey))
        dq.setFilter(ObsoletableFilterFactory.createShowActiveFilter());
        dq.addDqOrdering(Ordering.asc(BinField.ABN_NAME))

        List<AbstractStack> ystNameList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)
        return ystNameList
    }


    private static final Logger LOG = Logger.getLogger(ITSYardBlockMasterCallback.class)
}
