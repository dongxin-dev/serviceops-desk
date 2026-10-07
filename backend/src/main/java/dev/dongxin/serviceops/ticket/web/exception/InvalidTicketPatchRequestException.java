package dev.dongxin.serviceops.ticket.web.exception;

/**
 * Web-boundary contract violation of the PATCH basic-info request body
 * (unknown or forbidden JSON field). Deliberately a WEB-layer exception:
 * a JSON shape problem is not a Ticket aggregate invariant violation, so it
 * must not be expressed through the domain TicketValidationException.
 * Never referenced by domain / application / infrastructure.
 */
public class InvalidTicketPatchRequestException extends RuntimeException {

    public InvalidTicketPatchRequestException(String message) {
        super(message);
    }
}
