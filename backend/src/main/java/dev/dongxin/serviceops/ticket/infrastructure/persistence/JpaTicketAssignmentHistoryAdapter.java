package dev.dongxin.serviceops.ticket.infrastructure.persistence;

import dev.dongxin.serviceops.ticket.application.port.NewTicketAssignment;
import dev.dongxin.serviceops.ticket.application.port.TicketAssignmentHistory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Implements the TicketAssignmentHistory port against the ticket_assignment
 * table. closeActive flushes its UPDATE immediately: the partial unique
 * index allows only one active row per ticket, so the old row must be closed
 * in the database BEFORE append's INSERT becomes visible - deferring that
 * ordering to Hibernate's flush queue would be a correctness gamble.
 */
@Component
public class JpaTicketAssignmentHistoryAdapter implements TicketAssignmentHistory {

    private final SpringDataTicketAssignmentRepository assignmentTable;

    public JpaTicketAssignmentHistoryAdapter(SpringDataTicketAssignmentRepository assignmentTable) {
        this.assignmentTable = assignmentTable;
    }

    @Override
    public void closeActive(Long ticketId, OffsetDateTime endedAt) {
        assignmentTable.findByTicketIdAndEndedAtIsNull(ticketId)
                .ifPresent(active -> {
                    active.markEndedAt(endedAt);
                    assignmentTable.saveAndFlush(active);
                });
    }

    @Override
    public void append(NewTicketAssignment assignment) {
        assignmentTable.save(TicketAssignmentJpaEntity.newActive(
                assignment.ticketId(),
                assignment.assigneeId(),
                assignment.assignedById(),
                assignment.assignmentType(),
                assignment.assignedAt(),
                assignment.createdAt()));
    }
}
