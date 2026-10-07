package dev.dongxin.serviceops.ticket.domain;

import java.time.OffsetDateTime;

/**
 * Ticket aggregate root (framework-free).
 *
 * <p>This slice covers creation and retrieval only. Fields that future
 * state-transition / assignment behavior will change are non-final but have NO
 * public setters: they may only evolve through upcoming domain operations
 * (assign, startProgress, resolve, close, reopen) validated against the frozen
 * state machine. Identity and creation-time facts stay immutable.
 */
public class Ticket {

    public static final int TITLE_MAX_LENGTH = 200;
    public static final int CATEGORY_MAX_LENGTH = 64;

    // immutable identity / creation-time facts
    private final Long id;
    private final String ticketNo;
    private final TicketPriority priority;
    private final Long requesterId;
    private final Long slaPolicyId;
    private final OffsetDateTime responseDueAt;
    private final OffsetDateTime resolutionDueAt;
    private final OffsetDateTime createdAt;

    // mutable only through future domain business methods - never via setters
    private String title;
    private String description;
    private String category;
    private TicketStatus status;
    private Long currentAssigneeId;
    private OffsetDateTime firstRespondedAt;
    private OffsetDateTime resolvedAt;
    private OffsetDateTime closedAt;
    private int escalationLevel;
    private Integer version;
    private OffsetDateTime updatedAt;

    private Ticket(Long id, String ticketNo, String title, String description, String category,
                   TicketPriority priority, TicketStatus status, Long requesterId,
                   Long currentAssigneeId, Long slaPolicyId, OffsetDateTime responseDueAt,
                   OffsetDateTime resolutionDueAt, OffsetDateTime firstRespondedAt,
                   OffsetDateTime resolvedAt, OffsetDateTime closedAt, int escalationLevel,
                   Integer version, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
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
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Creates a brand-new ticket in status OPEN.
     *
     * <p>SLA deadlines are snapshots calculated from {@code createdAt} (ADR-003/ADR-005);
     * the caller resolves the policy, this factory only enforces their presence and order.
     */
    public static Ticket open(String ticketNo, String title, String description, String category,
                              TicketPriority priority, Long requesterId, Long slaPolicyId,
                              OffsetDateTime createdAt, OffsetDateTime responseDueAt,
                              OffsetDateTime resolutionDueAt) {
        requireText(ticketNo, "ticketNo");
        requireMaxLength(title, TITLE_MAX_LENGTH, "title");
        requireText(description, "description");
        requireMaxLength(category, CATEGORY_MAX_LENGTH, "category");
        if (priority == null) {
            throw new IllegalArgumentException("priority must not be null");
        }
        if (requesterId == null || requesterId <= 0) {
            throw new IllegalArgumentException("requesterId must be a positive id");
        }
        if (slaPolicyId == null || slaPolicyId <= 0) {
            throw new IllegalArgumentException("slaPolicyId must be a positive id");
        }
        if (createdAt == null || responseDueAt == null || resolutionDueAt == null) {
            throw new IllegalArgumentException("SLA snapshot fields must not be null");
        }
        if (responseDueAt.isBefore(createdAt) || resolutionDueAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("SLA deadlines must not be earlier than creation time");
        }
        return new Ticket(null, ticketNo, title, description, category, priority,
                TicketStatus.OPEN, requesterId, null, slaPolicyId, responseDueAt, resolutionDueAt,
                null, null, null, 0, null, createdAt, createdAt);
    }

    /** Reconstitutes a ticket loaded from persistence; no invariant re-derivation on load. */
    public static Ticket restore(Long id, String ticketNo, String title, String description,
                                 String category, TicketPriority priority, TicketStatus status,
                                 Long requesterId, Long currentAssigneeId, Long slaPolicyId,
                                 OffsetDateTime responseDueAt, OffsetDateTime resolutionDueAt,
                                 OffsetDateTime firstRespondedAt, OffsetDateTime resolvedAt,
                                 OffsetDateTime closedAt, int escalationLevel, Integer version,
                                 OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new Ticket(id, ticketNo, title, description, category, priority, status,
                requesterId, currentAssigneeId, slaPolicyId, responseDueAt, resolutionDueAt,
                firstRespondedAt, resolvedAt, closedAt, escalationLevel, version, createdAt, updatedAt);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireMaxLength(String value, int max, String field) {
        requireText(value, field);
        if (value.length() > max) {
            throw new IllegalArgumentException(field + " must not exceed " + max + " characters");
        }
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
