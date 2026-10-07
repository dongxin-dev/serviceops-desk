package dev.dongxin.serviceops.ticket.application.exception;

/** No ticket row exists for the requested id. */
public class TicketNotFoundException extends TicketApplicationException {

    public TicketNotFoundException(Long id) {
        super("Ticket not found: id=" + id);
    }
}
