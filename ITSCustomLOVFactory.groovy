/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.CarrierVisitReadyToBillEnum
import com.navis.argo.business.security.ArgoUser
import com.navis.external.framework.ui.lov.AbstractExtensionLovFactory
import com.navis.external.framework.ui.lov.ELovKey
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.UserContext
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.framework.presentation.lovs.Lov
import com.navis.framework.presentation.lovs.LovValue
import com.navis.framework.presentation.lovs.Style
import com.navis.framework.presentation.lovs.list.AtomizedEnumLov
import com.navis.framework.presentation.lovs.list.BasicLov
import com.navis.framework.presentation.lovs.value.AtomLovValue
import com.navis.framework.presentation.lovs.value.LocalizedLovValue

/**
 * @Author <a href="mailto:kgopinath@weservetech.com">Gopinath K</a>, 21/Dec/2022
 *
 * Requirements : This groovy is used to render the LOV based on Custom logic.
 *
 * @Inclusion Location	: Incorporated as a code extension of the type LOV_FACTORY.
 *
 *  Load Code Extension to N4:
 *   1. Go to Administration --> System -->  Code Extension
 *   2. Click Add (+)
 *   3. Enter the values as below:
 Code Extension Name:  ITSCustomLOVFactory
 Code Extension Type:  LOV_FACTORY
 Groovy Code: Copy and paste the contents of groovy code.
 *   4. Click Save button
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *  1        23-Jan-2023    Gopinath K      IP-320    To make the grpFlexString01 as dropdown - Yes or No.
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
                        if (userRoles.contains("VesselInvoice_READY_AND_NOT_READY")) {
                            lov.addLovEntry(new AtomLovValue(CarrierVisitReadyToBillEnum.READY, Style.LABEL_ONLY));
                            lov.addLovEntry(new AtomLovValue(CarrierVisitReadyToBillEnum.NOT_READY, Style.LABEL_ONLY));
                        } else if (userRoles.contains("VesselInvoice_READY_ONLY")) {
                            lov.addLovEntry(new AtomLovValue(CarrierVisitReadyToBillEnum.READY, Style.LABEL_ONLY));
                        }
                    }
                }
            });

            return lov;

        } else if(eLovKey.represents("customgrpFlexString01")){
            BasicLov lov = new BasicLov();
            LovValue lovYesValue = new LocalizedLovValue("Yes", "Yes");
            LovValue lovNoValue = new LocalizedLovValue("No", "No");
            lov.addLovEntry(lovYesValue);
            lov.addLovEntry(lovNoValue);
            return lov;
        }
        return null
    }
}
