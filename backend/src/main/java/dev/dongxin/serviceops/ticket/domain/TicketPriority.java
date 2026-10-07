package dev.dongxin.serviceops.ticket.domain;

/**
 * Ticket priority levels. Values are frozen by the Domain Model and the
 * {@code ck_ticket_priority} / {@code ck_sla_policy_priority} CHECK constraints.
 */
public enum TicketPriority {
    P1_CRITICAL,
    P2_HIGH,
    P3_MEDIUM,
    P4_LOW
}
