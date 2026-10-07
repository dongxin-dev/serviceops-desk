package dev.dongxin.serviceops.ticket.domain;

/**
 * Signals a violated Ticket aggregate input / invariant rule.
 * Deliberately distinct from programming-error IllegalArgumentException, so the
 * web layer can map ONLY genuine business violations to HTTP 400.
 */
public class TicketValidationException extends RuntimeException {

    public TicketValidationException(String message) {
        super(message);
    }
}
