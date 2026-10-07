package dev.dongxin.serviceops.ticket.application.exception;

import dev.dongxin.serviceops.ticket.domain.TicketPriority;

/** No enabled SLA policy is configured for the requested priority. */
public class SlaPolicyNotConfiguredException extends TicketApplicationException {

    public SlaPolicyNotConfiguredException(TicketPriority priority) {
        super("No enabled SLA policy configured for priority: " + priority);
    }
}
