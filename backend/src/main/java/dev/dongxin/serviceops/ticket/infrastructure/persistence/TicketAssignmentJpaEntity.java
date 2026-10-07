package dev.dongxin.serviceops.ticket.infrastructure.persistence;

import dev.dongxin.serviceops.ticket.domain.AssignmentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * JPA mapping of the existing {@code ticket_assignment} table from the V1
 * migration - assignment history. Columns mirror the database exactly
 * (schema ownership stays with Flyway); {@code ended_at IS NULL} marks the
 * single active row per ticket, enforced by a partial unique index in DB.
 */
@Entity
@Table(name = "ticket_assignment")
public class TicketAssignmentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "assignee_id", nullable = false)
    private Long assigneeId;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false, length = 32)
    private AssignmentType assignmentType;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "ended_at", nullable = true)
    private OffsetDateTime endedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TicketAssignmentJpaEntity() {
        // required by JPA
    }

    private TicketAssignmentJpaEntity(Long ticketId, Long assigneeId, Long assignedBy,
                                      AssignmentType assignmentType, OffsetDateTime assignedAt,
                                      OffsetDateTime createdAt) {
        this.ticketId = ticketId;
        this.assigneeId = assigneeId;
        this.assignedBy = assignedBy;
        this.assignmentType = assignmentType;
        this.assignedAt = assignedAt;
        this.createdAt = createdAt;
    }

    /** A fresh history row: ended_at stays null until the next (re)assignment. */
    static TicketAssignmentJpaEntity newActive(Long ticketId, Long assigneeId, Long assignedBy,
                                               AssignmentType assignmentType, OffsetDateTime assignedAt,
                                               OffsetDateTime createdAt) {
        return new TicketAssignmentJpaEntity(ticketId, assigneeId, assignedBy, assignmentType,
                assignedAt, createdAt);
    }

    /**
     * Closes this active assignment. The only permitted mutation of a history
     * row - everything else (who/whom/when/type) is immutable once written.
     */
    void markEndedAt(OffsetDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public Long getAssignedBy() {
        return assignedBy;
    }

    public AssignmentType getAssignmentType() {
        return assignmentType;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
