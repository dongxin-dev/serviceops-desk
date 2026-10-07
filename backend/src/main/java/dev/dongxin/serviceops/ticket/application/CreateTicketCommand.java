package dev.dongxin.serviceops.ticket.application;

import dev.dongxin.serviceops.ticket.domain.TicketPriority;

/**
 * Command for the create-ticket use case. Kept separate from the web DTO.
 * Creation time and SLA snapshots are derived inside the service (ADR-003 / ADR-005).
 */
public record CreateTicketCommand(
        String title,
        String description,
        String category,
        TicketPriority priority,
        Long requesterId) {
}
