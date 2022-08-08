/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.*
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.reference.Container
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.HibernatingEntity
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizFailure
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat

/*
 *
 * @Author <a href="mailto:sanandaraj@weservetech.com">Anandaraj S</a>, 02/AUG/2022
 *
 * Requirements : This groovy is used to update the LastFreeday override via Release EMODAL 315 EDI.
 *
 * @Inclusion Location	: Incorporated as a code extension of the type EDI_POST_INTERCEPTOR.
 *
 *  Load Code Extension to N4:
        1. Go to Administration --> System --> Code Extensions
        2. Click Add (+)
        3. Enter the values as below:
            Code Extension Name:  ITSUpdateLastFreedayReleaseEdiPostInterceptor
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


class ITSUpdateLastFreedayReleaseEdiPostInterceptor extends AbstractEdiPostInterceptor {

    private static final Logger LOGGER = Logger.getLogger(this.class)

    @Override
    void beforeEdiPost(XmlObject inXmlTransactionDocument, Map inParams) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("ITSUpdateLastFreedayReleaseEdiPostInterceptor Started")
        ReleaseTransactionsDocument doc = (ReleaseTransactionsDocument) inXmlTransactionDocument;
        ReleaseTransactionsDocument.ReleaseTransactions transactions = doc.getReleaseTransactions();
        List<ReleaseTransactionDocument.ReleaseTransaction> releases = transactions.getReleaseTransactionList();

        if (releases.size() != 1) {
            throw BizFailure.create("expected exactly one ReleaseTransactionDocument, but inXmlObject contained " + releases.size());
        }

        ReleaseTransactionDocument.ReleaseTransaction inRelease = releases.get(0)
        EdiFlexFields inEdiFlexFields = inRelease!=null ? inRelease.getEdiFlexFields(): null
        String ufvLFDDate = null;
        if (inEdiFlexFields != null) {
            ufvLFDDate = inEdiFlexFields.getUfvFlexString01();
            inEdiFlexFields.unsetUfvFlexString01();
        }
        inParams.put("LFDOVERRIDE", ufvLFDDate);
    }

    @Override
    void afterEdiPost(XmlObject inXmlTransactionDocument, HibernatingEntity inHibernatingEntity, Map inParams) {

        ReleaseTransactionsDocument doc = (ReleaseTransactionsDocument) inXmlTransactionDocument;
        ReleaseTransactionsDocument.ReleaseTransactions transactions = doc.getReleaseTransactions();
        List<ReleaseTransactionDocument.ReleaseTransaction> releases = transactions.getReleaseTransactionList();

        if (releases.size() != 1) {
            throw BizFailure.create("expected exactly one ReleaseTransactionDocument, but inXmlObject contained " + releases.size());
        }
        ReleaseTransactionDocument.ReleaseTransaction inRelease = releases.get(0)
        UnitCategoryEnum ediCategory = UnitCategoryEnum.getEnum(inRelease.getReleaseIdentifierCategory())

        List<EdiReleaseIdentifier> releaseIdentifiers = inRelease.getEdiReleaseIdentifierList();
        String lfdOverride = inParams.get("LFDOVERRIDE")
        for (int j = 0; j < releaseIdentifiers.size(); j++) {
            EdiReleaseIdentifier releaseIdentifier = releaseIdentifiers.get(j);
            String cntNbr = releaseIdentifier.getReleaseIdentifierNbr();
            Container ctr = Container.findContainer(cntNbr);
            // Find or create the container
            Unit inUnit = ctr != null ? findTargetUnit(ctr.getEqIdFull(), ediCategory) : null;
            UnitFacilityVisit inUfv = inUnit != null ? inUnit.getUnitActiveUfvNowActive() : null;
            if (lfdOverride != null) {
                Date date_new = getDate(lfdOverride)
                if (inUfv != null) {
                    inUfv.setUfvLineLastFreeDay(date_new)
                    HibernateApi.getInstance().save(inUfv)
                }
            }


        }
        HibernateApi.getInstance().flush();

    }


    private Unit findTargetUnit(String inUnitId, UnitCategoryEnum inCategory) {
        String[] visitState = new String[2];
        visitState[0] = "2ADVISED";
        visitState[1] = "1ACTIVE";
        if (inUnitId != null) {
            DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                    .addDqPredicate(PredicateFactory.eq(InventoryField.UNIT_ID, inUnitId))
                    .addDqPredicate(PredicateFactory.eq(InventoryField.UNIT_COMPLEX, ContextHelper.getThreadComplex().getPrimaryKey()))
                    .addDqPredicate(PredicateFactory.in(UnitField.UNIT_VISIT_STATE, visitState));
            List<Unit> unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            if (!unitList.isEmpty()) {
                for (int i = 0; i < unitList.size(); i++) {
                    Unit unit = unitList.get(i);
                    return unit;

                }
            }
        }
        return null;
    }

    private Date getDate(String dt) throws ParseException {
        Calendar cal = Calendar.getInstance();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        cal.setTime(dateFormat.parse(dt));
        return cal.getTime();
    }

}
