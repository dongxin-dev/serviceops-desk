package dev.dongxin.serviceops.ticket.infrastructure.persistence;

import dev.dongxin.serviceops.ticket.domain.TicketPriority;
import dev.dongxin.serviceops.ticket.domain.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;

/**
 * JPA mapping of the existing {@code ticket} table created by V1 migration.
 *
 * <p>Columns mirror the database exactly; schema ownership stays with Flyway
 * (Hibernate never generates or validates DDL in this project).
 */
@Entity
@Table(name = "ticket")
public class TicketJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_no", nullable = false, length = 32)
    private String ticketNo;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TicketStatus status;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "current_assignee_id", nullable = true)
    private Long currentAssigneeId;

    @Column(name = "sla_policy_id", nullable = false)
    private Long slaPolicyId;

    @Column(name = "response_due_at", nullable = false)
    private OffsetDateTime responseDueAt;

    @Column(name = "resolution_due_at", nullable = false)
    private OffsetDateTime resolutionDueAt;

    @Column(name = "first_responded_at", nullable = true)
    private OffsetDateTime firstRespondedAt;

    @Column(name = "resolved_at", nullable = true)
    private OffsetDateTime resolvedAt;

    @Column(name = "closed_at", nullable = true)
    private OffsetDateTime closedAt;

    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TicketJpaEntity() {
        // required by JPA
    }

    private TicketJpaEntity(String ticketNo, String title, String description, String category,
                            TicketPriority priority, TicketStatus status, Long requesterId,
                            Long currentAssigneeId, Long slaPolicyId, OffsetDateTime responseDueAt,
                            OffsetDateTime resolutionDueAt, OffsetDateTime firstRespondedAt,
                            OffsetDateTime resolvedAt, OffsetDateTime closedAt, int escalationLevel,
                            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.ticketNo = ticketNo;
        this.title = title;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.status = status;
        this.requesterId = requesterId;
        this.currentAssigneeId = currentAssigneeId;
        this.slaPolicyId = slaPolicyId;
        this.responseDueAt = responseDueAt;
        this.resolutionDueAt = resolutionDueAt;
        this.firstRespondedAt = firstRespondedAt;
        this.resolvedAt = resolvedAt;
        this.closedAt = closedAt;
        this.escalationLevel = escalationLevel;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static TicketJpaEntity newInsert(String ticketNo, String title, String description,
                                     String category, TicketPriority priority, TicketStatus status,
                                     Long requesterId, Long currentAssigneeId, Long slaPolicyId,
                                     OffsetDateTime responseDueAt, OffsetDateTime resolutionDueAt,
                                     OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new TicketJpaEntity(ticketNo, title, description, category, priority, status,
                requesterId, currentAssigneeId, slaPolicyId, responseDueAt, resolutionDueAt,
                null, null, null, 0, createdAt, updatedAt);
    }

    /**
     * Copies the mutable basic-info state of an existing aggregate onto this
     * MANAGED entity for the patch use case. Only title / description /
     * category / updatedAt may be written; id, ticketNo, priority, status,
     * requester, assignee, SLA fields, escalationLevel, version and createdAt
     * are intentionally untouched (Hibernate owns version via @Version).
     */
    void applyMutableState(String title, String description, String category,
                           OffsetDateTime updatedAt) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public Long getCurrentAssigneeId() {
        return currentAssigneeId;
    }

    public Long getSlaPolicyId() {
        return slaPolicyId;
    }

    public OffsetDateTime getResponseDueAt() {
        return responseDueAt;
    }

    public OffsetDateTime getResolutionDueAt() {
        return resolutionDueAt;
    }

    public OffsetDateTime getFirstRespondedAt() {
        return firstRespondedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public int getEscalationLevel() {
        return escalationLevel;
    }

    public Integer getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
