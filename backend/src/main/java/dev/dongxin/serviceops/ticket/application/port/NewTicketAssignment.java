package dev.dongxin.serviceops.ticket.application.port;

import dev.dongxin.serviceops.ticket.domain.AssignmentType;

import java.time.OffsetDateTime;

/**
 * Immutable description of one new assignment history row, supplied by the
 * application use case and written verbatim by the infrastructure adapter.
 * A pure write model - no JPA type is exposed through this port.
 */
public record NewTicketAssignment(
        Long ticketId,
        Long assigneeId,
        Long assignedById,
        AssignmentType assignmentType,
        OffsetDateTime assignedAt,
        OffsetDateTime createdAt) {

    public NewTicketAssignment {
        if (ticketId == null || assigneeId == null || assignedById == null
                || assignmentType == null || assignedAt == null || createdAt == null) {
            throw new IllegalArgumentException(
                    "NewTicketAssignment requires all fields to be present (programming error)");
        }
    }
}
