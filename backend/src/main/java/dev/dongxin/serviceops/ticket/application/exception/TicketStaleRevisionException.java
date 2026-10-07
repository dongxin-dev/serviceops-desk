package dev.dongxin.serviceops.ticket.application.exception;

/** The client submitted a stale version token for this ticket. */
public class TicketStaleRevisionException extends TicketApplicationException {

    public TicketStaleRevisionException(Long id, Integer currentVersion, Integer expectedVersion) {
        super("Ticket " + id + " was modified concurrently: current version=" + currentVersion
                + ", submitted version=" + expectedVersion);
    }
}
