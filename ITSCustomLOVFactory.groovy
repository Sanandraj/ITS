/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.ArgoField
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.CarrierVisitReadyToBillEnum
import com.navis.argo.business.atoms.DrayStatusEnum
import com.navis.argo.business.security.ArgoUser
import com.navis.argo.business.util.QueryUtil
import com.navis.external.framework.ui.lov.AbstractExtensionLovFactory
import com.navis.external.framework.ui.lov.ELovKey
import com.navis.framework.business.atoms.LifeCycleStateEnum
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.framework.presentation.lovs.Lov
import com.navis.framework.presentation.lovs.Style
import com.navis.framework.presentation.lovs.list.AtomizedEnumLov
import com.navis.framework.presentation.lovs.list.AtomizedEnumSubsetLov
import com.navis.framework.presentation.lovs.list.DomainQueryLov
import com.navis.framework.presentation.lovs.value.AtomLovValue
import com.navis.framework.util.AtomizedEnum
import com.navis.inventory.business.atoms.CraneOperationModeEnum
import com.navis.vessel.api.VesselVisitField

/*
 *
 * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 21/Dec/2022
 *
 * Requirements : This groovy is used to render the LOV based on Custom logic.
 *
 * @Inclusion Location	: Incorporated as a code extension of the type LOV_FACTORY as mention below.
 *
 * Deployment Steps:
 *	a) Administration -> System -> Code Extension
 *	b) Click on + (Add) Button
 *	c) Add as LOV_FACTORY and code extension name as ITSCustomLOVFactory
 *	d) Paste the groovy code and click on save
 *
 *
 */

class ITSCustomLOVFactory extends AbstractExtensionLovFactory {


    @Override
    Lov getLov(ELovKey eLovKey) {
        if (eLovKey.represents("customCvReadyToInvoice")) {
            AtomizedEnumLov lov = new AtomizedEnumLov(CarrierVisitReadyToBillEnum.class);
            lov.setCollection(new ArrayList());
            PersistenceTemplate persistenceTemplate = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext());
            persistenceTemplate.invoke(new CarinaPersistenceCallback() {
                @Override
                protected void doInTransaction() {
                    UserContext context = FrameworkPresentationUtils.getUserContext();
                    if (context == null) {
                        context = ContextHelper.getThreadUserContext();
                    }
                    Serializable userGkey = context.getUserGkey();
                    ArgoUser user = (ArgoUser) HibernateApi.getInstance().load(ArgoUser.class, userGkey)

                    if (user != null) {
                        String[] userRoles = user.getUserRoleNames();
                        if (userRoles.contains("Super User")) {
                            lov.addLovEntry(new AtomLovValue(CarrierVisitReadyToBillEnum.READY, Style.LABEL_ONLY));
                            lov.addLovEntry(new AtomLovValue(CarrierVisitReadyToBillEnum.NOT_READY, Style.LABEL_ONLY));
                        } else if (userRoles.contains("Vessel Planner")) {
                            lov.addLovEntry(new AtomLovValue(CarrierVisitReadyToBillEnum.READY, Style.LABEL_ONLY));
                        }
                    }
                }
            });

            return lov;

        }
        return null
    }
}
