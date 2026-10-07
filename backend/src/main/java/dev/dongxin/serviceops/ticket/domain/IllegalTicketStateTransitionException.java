package dev.dongxin.serviceops.ticket.domain;

/**
 * The requested business action is not allowed from the ticket's current
 * lifecycle state, per the frozen Ticket State Machine. Distinct from
 * TicketValidationException (bad field values): the request itself is
 * well-formed, the resource state simply does not permit the action.
 */
public class IllegalTicketStateTransitionException extends RuntimeException {

    public IllegalTicketStateTransitionException(String action, TicketStatus currentStatus) {
        super("Action '" + action + "' is not allowed from current status " + currentStatus);
    }
}
