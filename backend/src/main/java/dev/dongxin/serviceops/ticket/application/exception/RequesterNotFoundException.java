package dev.dongxin.serviceops.ticket.application.exception;

/** The referenced requester does not exist in app_user. */
public class RequesterNotFoundException extends TicketApplicationException {

    public RequesterNotFoundException(Long requesterId) {
        super("Requester not found: id=" + requesterId);
    }
}
