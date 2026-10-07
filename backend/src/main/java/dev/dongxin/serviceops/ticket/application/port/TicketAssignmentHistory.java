package dev.dongxin.serviceops.ticket.application.port;

import java.time.OffsetDateTime;

/**
 * Write-side port for the ticket_assignment history table, limited to what
 * the assign use case needs: close the current active assignment (if any)
 * and append a new one. The database partial unique index enforces at most
 * one active (ended_at IS NULL) row per ticket, so closeActive MUST be
 * flushed before append inserts - same transaction, in that order.
 * No find / list / delete: no current use case consumes them.
 */
public interface TicketAssignmentHistory {

    /**
     * Sets ended_at on the ticket's active assignment row, if one exists.
     * A no-op for a first-time assignment (0 rows affected is valid).
     */
    void closeActive(Long ticketId, OffsetDateTime endedAt);

    void append(NewTicketAssignment assignment);
}
