package dev.dongxin.serviceops.ticket.web.dto;

import dev.dongxin.serviceops.ticket.domain.Ticket;

import java.time.OffsetDateTime;

/**
 * Compact list element for the ticket list endpoint. Deliberately NOT the full
 * TicketResponse: the list use case only exposes summary columns.
 */
public record TicketSummaryResponse(
        Long id,
        String ticketNo,
        String title,
        String status,
        String priority,
        Long currentAssigneeId,
        OffsetDateTime createdAt) {

    public static TicketSummaryResponse from(Ticket ticket) {
        return new TicketSummaryResponse(
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getTitle(),
                ticket.getStatus().name(),
                ticket.getPriority().name(),
                ticket.getCurrentAssigneeId(),
                ticket.getCreatedAt());
    }
}
