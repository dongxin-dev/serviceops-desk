package dev.dongxin.serviceops.ticket.application.exception;

/** Base for ticket use-case failures mapped to HTTP responses by the web layer. */
public abstract class TicketApplicationException extends RuntimeException {

    protected TicketApplicationException(String message) {
        super(message);
    }
}
