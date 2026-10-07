package dev.dongxin.serviceops.ticket.infrastructure.persistence;

import dev.dongxin.serviceops.ticket.domain.Ticket;

/**
 * Explicit mapping between the Ticket domain object and its JPA entity.
 * Package-internal to infrastructure: only the persistence adapters use it.
 */
final class TicketJpaMapper {

    private TicketJpaMapper() {
    }

    static TicketJpaEntity toNewEntity(Ticket ticket) {
        return TicketJpaEntity.newInsert(
                ticket.getTicketNo(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCategory(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getRequesterId(),
                ticket.getCurrentAssigneeId(),
                ticket.getSlaPolicyId(),
                ticket.getResponseDueAt(),
                ticket.getResolutionDueAt(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }

    static Ticket toDomain(TicketJpaEntity entity) {
        return Ticket.restore(
                entity.getId(),
                entity.getTicketNo(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getPriority(),
                entity.getStatus(),
                entity.getRequesterId(),
                entity.getCurrentAssigneeId(),
                entity.getSlaPolicyId(),
                entity.getResponseDueAt(),
                entity.getResolutionDueAt(),
                entity.getFirstRespondedAt(),
                entity.getResolvedAt(),
                entity.getClosedAt(),
                entity.getEscalationLevel(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
