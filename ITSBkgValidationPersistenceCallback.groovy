/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
*/


import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback
import com.navis.inventory.business.units.EqBaseOrderItem
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.services.business.rules.EventType
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable

/**
 * @Author: mailto:skishore@weservetech.com, Kishore Kumar S; Date: 28/10/2022
 *
 *  Requirements: 5-2-Button to Cancel unused bookings after vessel cut-offs -- This groovy is used to reduce multiple booking selected from booking entity.
 *
 * @Inclusion Location: Incorporated as a code extension of the type
 *
 *  Load Code Extension to N4:
 *  1. Go to Administration --> System --> Code Extensions
 *  2. Click Add (+)
 *  3. Enter the values as below:
 *     Code Extension Name: ITSBkgValidationPersistenceCallback
 *     Code Extension Type: TRANSACTED_BUSINESS_FUNCTION
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */
class ITSBkgValidationPersistenceCallback extends AbstractExtensionPersistenceCallback {
    @Override
    void execute(@Nullable Map input, @Nullable Map inOutResults) {
        if (input.get("recordEvent") != null && "YES".equalsIgnoreCase((String) input.get("recordEvent"))) {
            Serializable vesselVisitDetails = (Serializable) input?.get("vesselVisit")
            if (vesselVisitDetails) {
                VesselVisitDetails vvd = VesselVisitDetails.hydrate(vesselVisitDetails)
                EventType event = EventType.findEventType("VESSEL_VISIT_REDUCED")
                if (vvd != null && event != null) {
                    TimeZone timeZone = ContextHelper.getThreadUserTimezone()
                    vvd.recordEvent(event, null, ContextHelper.getThreadUserId(), ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))
                    return
                }
            }

        }
        Serializable bookingOrder = (Serializable) input?.get("entityGkey")
        Booking booking = Booking.hydrate(bookingOrder)
        if (booking == null) {
            return;
        }
        boolean reduced = false

        Long totalItemQuantity = 0;
        if (booking.eqoTallyReceive > 0) {
            Set bkgItems = booking != null ? booking.getEqboOrderItems() : null;
            if (bkgItems != null && !bkgItems.isEmpty() && bkgItems.size() >= 1) {
                Iterator iterator = bkgItems.iterator()
                while (iterator.hasNext()) {
                    EquipmentOrderItem eqoItem = EquipmentOrderItem.resolveEqoiFromEqboi((EqBaseOrderItem) iterator.next())
                    Long eqoiQty = eqoItem?.getEqoiQty()
                    Long eqoiTallyOut = eqoItem?.getEqoiTally()
                    Long eqoiTallyIn = eqoItem?.getEqoiTallyReceive()

                    if (eqoiTallyIn >= 0 || eqoiTallyOut >= 0) {
                        if (eqoiTallyIn >= eqoiTallyOut && eqoiTallyIn < eqoiQty) {
                            if (eqoItem.getEqoiTallyLimit() != null && eqoItem.getEqoiTallyLimit() > eqoiTallyIn) {
                                eqoItem.setEqoiTallyLimit(eqoiTallyIn)
                            } else if (eqoItem.getEqoiReceiveLimit() != null && eqoItem.getEqoiReceiveLimit() > eqoiTallyIn) {
                                eqoItem.setEqoiReceiveLimit(eqoiTallyIn)
                            }
                            eqoItem.setEqoiQty(eqoiTallyIn)
                            reduced = true

                        } else if (eqoiTallyOut >= eqoiTallyIn && eqoiTallyOut < eqoiQty) {
                            if (eqoItem.getEqoiTallyLimit() != null && eqoItem.getEqoiTallyLimit() > eqoiTallyOut) {
                                eqoItem.setEqoiTallyLimit(eqoiTallyOut)
                            } else if (eqoItem.getEqoiReceiveLimit() != null && eqoItem.getEqoiReceiveLimit() > eqoiTallyOut) {
                                eqoItem.setEqoiReceiveLimit(eqoiTallyOut)

                            }
                            eqoItem.setEqoiQty(eqoiTallyOut)
                            reduced = true
                        }
                    }

                    totalItemQuantity += eqoItem.getEqoiQty()
                }
            }
            booking.setEqoQuantity(totalItemQuantity)

        } else if (booking.eqoTallyReceive == 0) {
            booking.purge()
            reduced = true
        }
        if (reduced) {
            inOutResults.put("reduced", "YES")
        }

    }
    private static Logger LOGGER = Logger.getLogger(ITSBkgValidationPersistenceCallback.class)
}
