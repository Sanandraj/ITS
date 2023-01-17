package ITS

import com.navis.argo.ContextHelper

/*
 * Copyright (c) 2022 WeServe LLC. All Rights Reserved.
 *
 */

import com.navis.external.framework.util.ExtensionUtils
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.orders.business.eqorders.Booking
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger

/*
 * @Author <a href="mailto:annalakshmig@weservetech.com">ANNALAKSHMI G</a>
 * Requirements:-
 */

class BookingUpdateGenNotice extends AbstractGeneralNoticeCodeExtension {

    @Override
    void execute(GroovyEvent inEvent) {
        LOGGER.setLevel(Level.DEBUG)
        LOGGER.debug("Inside the BookingUpdateGenNotice :: Start")
        def library = ExtensionUtils.getLibrary(ContextHelper.getThreadUserContext(), "ITSEmodalLibrary");
        final Booking booking = (Booking) inEvent.getEntity()

        if (booking == null) {
            return
        }

        final Event event = inEvent.getEvent()
        if (event != null) {
            library.execute((Booking) inEvent.getEntity(), inEvent.getEvent())
        }
    }
    private static final Logger LOGGER = Logger.getLogger(this.class)
}
