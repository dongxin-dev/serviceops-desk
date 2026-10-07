package dev.dongxin.serviceops.ticket.web.dto;

import dev.dongxin.serviceops.ticket.domain.Ticket;

import java.time.OffsetDateTime;

/** Full read model of a ticket. Mapped explicitly from the domain object. */
public record TicketResponse(
        Long id,
        String ticketNo,
        String title,
        String description,
        String category,
        String priority,
        String status,
        Long requesterId,
        Long currentAssigneeId,
        Long slaPolicyId,
        OffsetDateTime responseDueAt,
        OffsetDateTime resolutionDueAt,
        OffsetDateTime firstRespondedAt,
        OffsetDateTime resolvedAt,
        OffsetDateTime closedAt,
        int escalationLevel,
        Integer version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCategory(),
                ticket.getPriority().name(),
                ticket.getStatus().name(),
                ticket.getRequesterId(),
                ticket.getCurrentAssigneeId(),
                ticket.getSlaPolicyId(),
                ticket.getResponseDueAt(),
                ticket.getResolutionDueAt(),
                ticket.getFirstRespondedAt(),
                ticket.getResolvedAt(),
                ticket.getClosedAt(),
                ticket.getEscalationLevel(),
                ticket.getVersion(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }
}
