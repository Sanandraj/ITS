/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/


import com.navis.external.framework.ui.AbstractFormSubmissionCommand
import com.navis.external.framework.util.EFieldChanges
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.orders.business.eqorders.Booking
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @Author: mailto:skishore@weservetech.com, Kishore S; Date: 11/10/2022
 *
 *  Requirements: 5-14-Mass-Booking-Roll -- This groovy is used to roll multiple booking selected from booking entity.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSMassBookingRollSubmitFormCommand
 *     Code Extension Type: FORM_SUBMISSION_INTERCEPTOR
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup override configuration in variformId - REV_ORD001
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */

class ITSMassBookingRollSubmitFormCommand extends AbstractFormSubmissionCommand {
    private static Logger LOGGER = Logger.getLogger(ITSMassBookingRollSubmitFormCommand.class)

    @Override
    void doBeforeSubmit(String inVariformId, EntityId inEntityId, List<Serializable> inGkeys, EFieldChanges inOutFieldChanges, EFieldChanges inNonDbFieldChanges, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.INFO)
        LOGGER.info("ITSMassBookingRollSubmitFormCommand Starts ::")
        if (inGkeys == null && inGkeys.isEmpty()) {
            return;
        }
        PersistenceTemplate persistenceTemplate = new PersistenceTemplate(FrameworkPresentationUtils.getUserContext())
        persistenceTemplate.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                String constantLineOpr = null
                String constantVvd = null
                int count = 0
                for (Serializable gKey : inGkeys as List<Serializable>) {
                    count = count + 1
                    Booking booking = Booking.hydrate(gKey)
                    if (booking == null) {
                        return;
                    }
                    if (count == 1) {

                        constantLineOpr = booking.getEqoLine() ? booking.getEqoLine().getBzuId() : null;
                        constantVvd = booking.getEqoVesselVisit() != null ? booking.getEqoVesselVisit().getCvId() : null;

                    }
                    if (count > 1) {
                        String bkgCurrentLineOpr = booking.getEqoLine() != null ? ooking.getEqoLine().getBzuId() : null;
                        if (bkgCurrentLineOpr != null && !bkgCurrentLineOpr.equals(constantLineOpr)) {
                            registerError("Error : Cannot perform Booking roll for selected bookings")
                            return
                        }

                        if (booking.getEqoVesselVisit()) {
                            String bkgVvd = booking.getEqoVesselVisit().getCvId()
                            if (!bkgVvd.equals(constantVvd)) {
                                registerError("Error : Cannot perform Booking roll for selected bookings")
                                return
                            }
                        }
                    }


                    Long bkgTallyReceive = booking?.getEqoTallyReceive()
                    if (bkgTallyReceive > 0) {
                        registerError("Error : Cannot perform Booking roll for selected bookings")
                        return
                    }
                }
            }
        })
    }
}
