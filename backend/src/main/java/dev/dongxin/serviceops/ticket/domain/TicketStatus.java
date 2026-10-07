package dev.dongxin.serviceops.ticket.domain;

/**
 * Ticket lifecycle states defined by the frozen Ticket State Machine.
 *
 * <p>Do not add, rename or remove a state without an approved Domain Design change.
 * Transition rules are intentionally not part of this slice (Create/Get only).
 */
public enum TicketStatus {
    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    REOPENED
}
