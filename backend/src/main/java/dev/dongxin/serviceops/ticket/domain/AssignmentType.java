package dev.dongxin.serviceops.ticket.domain;

/**
 * Business meaning of one assignment record, matching the frozen
 * ck_ticket_assignment_type values in the V1 migration.
 * CLAIM is part of the frozen vocabulary but produces no V1 API yet
 * (no claim endpoint exists in this slice).
 */
public enum AssignmentType {
    ASSIGN,
    CLAIM,
    REASSIGN
}
