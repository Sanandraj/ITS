/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */


import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.external.framework.ui.AbstractTableViewCommand
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.presentation.ui.message.ButtonTypes
import com.navis.framework.presentation.ui.message.MessageType
import com.navis.framework.presentation.ui.message.OptionDialog
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.inventory.business.units.EqBaseOrderItem
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.services.business.rules.EventType
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Level
import org.apache.log4j.Logger

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
 *     Code Extension Name: ITSBkgMassReduceTableViewCommand
 *     Code Extension Type: TABLE_VIEW_COMMAND
 *     Groovy Code: Copy and paste the contents of groovy code.
 *  4. Click Save button
 *
 * @Setup override configuration in variformId - REV_ORD001.
 *
 *
 *  S.No    Modified Date   Modified By     Jira      Description
 *
 */

class ITSBkgMassReduceTableViewCommand extends AbstractTableViewCommand {
    @Override
    void execute(EntityId inEntityId, List<Serializable> inGkeys, Map<String, Object> inParams) {
        LOGGER.setLevel(Level.INFO)
        LOGGER.info("ITSBkgMassReduceTableViewCommand Starts")
        if (inGkeys == null && inGkeys.isEmpty()) {
            return;
        }
        PersistenceTemplate pt = new PersistenceTemplate(getUserContext())
        pt.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                if (inGkeys != null && !inGkeys.isEmpty() && inGkeys.size() > 0) {
                    Iterator it = inGkeys.iterator()
                    long count = 0
                    long errorCount = 0
                    boolean bkgReduce = false
                    while (it.hasNext()) {
                        Booking booking = Booking.hydrate(it.next())
                        if (booking == null) {
                            return;
                        }
                        VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(booking.getEqoVesselVisit())
                        TimeZone timeZone = ContextHelper.getThreadUserTimezone()
                        EventType event = EventType.findEventTypeProxy("BOOKING_REDUCED")
                        if (vvd != null &&
                                (booking.getEqoQuantity()>booking.getEqoTallyReceive())) {
                            Long totalItemQuantity = 0
                            boolean bkgQtyUpdate = false
                            if (booking.getEqboNbr() != null) {
                                if (booking.eqoTallyReceive >= 0) {
                                    Set bkgItems = booking.getEqboOrderItems()
                                    if (bkgItems != null && !bkgItems.isEmpty() && bkgItems.size() >= 1) {
                                        Iterator iterator = bkgItems.iterator()
                                        while (iterator.hasNext()) {
                                            EquipmentOrderItem eqoItem = EquipmentOrderItem.resolveEqoiFromEqboi((EqBaseOrderItem) iterator.next())
                                            Long eqoiQty = eqoItem.getEqoiQty()
                                            Long eqoiTallyOut = eqoItem.getEqoiTally()
                                            Long eqoiTallyIn = eqoItem.getEqoiTallyReceive()
                                            if (eqoiTallyIn >= 0 || eqoiTallyOut >= 0) {
                                                if (eqoiTallyIn >= eqoiTallyOut && eqoiTallyIn < eqoiQty) {
                                                    if(eqoItem.getEqoiTallyLimit() != null && eqoItem.getEqoiTallyLimit() > eqoiTallyIn){
                                                        eqoItem.setEqoiTallyLimit(eqoiTallyIn)
                                                    } else if (eqoItem.getEqoiReceiveLimit() != null && eqoItem.getEqoiReceiveLimit() > eqoiTallyIn){
                                                        eqoItem.setEqoiReceiveLimit(eqoiTallyIn)

                                                    }
                                                    eqoItem.setEqoiQty(eqoiTallyIn)
                                                    bkgReduce = true
                                                    bkgQtyUpdate = true
                                                }
                                                else if (eqoiTallyOut >= eqoiTallyIn && eqoiTallyOut < eqoiQty) {
                                                    if(eqoItem.getEqoiTallyLimit() != null && eqoItem.getEqoiTallyLimit() > eqoiTallyOut){
                                                        eqoItem.setEqoiTallyLimit(eqoiTallyOut)
                                                    } else if (eqoItem.getEqoiReceiveLimit() != null && eqoItem.getEqoiReceiveLimit() > eqoiTallyOut){
                                                        eqoItem.setEqoiReceiveLimit(eqoiTallyOut)

                                                    }
                                                    eqoItem.setEqoiQty(eqoiTallyOut)
                                                    bkgReduce = true
                                                    bkgQtyUpdate = true
                                                }
                                            }
                                            totalItemQuantity += eqoItem.getEqoiQty()
                                        }
                                        if(bkgQtyUpdate){
                                            count = count + 1
                                            booking.recordEvent(event, null, ContextHelper.getThreadUserId(), ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), timeZone))

                                        }
                                    }
                                }
                            }
                            if (bkgQtyUpdate){
                                booking.setEqoQuantity(totalItemQuantity)
                                HibernateApi.getInstance().save(booking)
                            }

                        } else if (booking.getEqoQuantity() == booking.getEqoTallyReceive()){
                            errorCount = errorCount + 1
                        }
                    }
                    if (!bkgReduce) {
                        informationBox(count, Long.valueOf(inGkeys.size()))
                    }
                    if (bkgReduce) {
                        informationBox(count, errorCount)
                    }

                } else {
                    OptionDialog.showInformation(PropertyKeyFactory.valueOf("Please select one or more than one booking"), PropertyKeyFactory.valueOf("Booking Reduction"))
                }
            }
        })
    }

    private static final informationBox(long count, long errorCount) {
        OptionDialog.showMessage(PropertyKeyFactory.valueOf("Vessel Cut-offs (Performed count):      ${count} \nVessel Cut-offs (Not performed count):  ${errorCount}"), PropertyKeyFactory.valueOf("Information"), MessageType.INFORMATION_MESSAGE, ButtonTypes.OK, null)
    }
    private final static Logger LOGGER = Logger.getLogger(ITSBkgMassReduceTableViewCommand.class)
}
