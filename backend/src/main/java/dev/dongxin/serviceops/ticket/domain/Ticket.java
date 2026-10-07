package dev.dongxin.serviceops.ticket.domain;

import java.time.OffsetDateTime;

/**
 * Ticket aggregate root (framework-free).
 *
 * <p>Fields that state-transition / assignment behavior will change are
 * non-final but have NO public setters: they may only evolve through domain
 * operations validated against the frozen state machine. Identity and
 * creation-time facts stay immutable.
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
            throw new TicketValidationException("priority must not be null");
        }
        if (requesterId == null || requesterId <= 0) {
            throw new TicketValidationException("requesterId must be a positive id");
        }
        if (slaPolicyId == null || slaPolicyId <= 0) {
            throw new TicketValidationException("slaPolicyId must be a positive id");
        }
        if (createdAt == null || responseDueAt == null || resolutionDueAt == null) {
            throw new TicketValidationException("SLA snapshot fields must not be null");
        }
        if (responseDueAt.isBefore(createdAt) || resolutionDueAt.isBefore(createdAt)) {
            throw new TicketValidationException("SLA deadlines must not be earlier than creation time");
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
            throw new TicketValidationException(field + " must not be blank");
        }
    }

    private static void requireMaxLength(String value, int max, String field) {
        requireText(value, field);
        if (value.length() > max) {
            throw new TicketValidationException(field + " must not exceed " + max + " characters");
        }
    }

    /**
     * Business update of the basic information fields (PATCH semantics).
     *
     * <p>A {@code null} parameter means "not provided, keep current value" - the
     * presence / explicit-null discrimination happens at the web boundary.
     * Any provided value is re-validated against aggregate invariants. An
     * update that provides no field at all is a no-op the aggregate rejects -
     * this rule lives here, not only in outer layers. Version is never touched
     * here; concurrency control is delegated to JPA {@code @Version}.
     */
    public void updateBasicInfo(String title, String description, String category,
                                OffsetDateTime updatedAt) {
        if (title == null && description == null && category == null) {
            throw new TicketValidationException(
                    "at least one basic information field is required for an update");
        }
        if (updatedAt == null) {
            throw new TicketValidationException("updatedAt is required for a business update");
        }
        if (title != null) {
            requireMaxLength(title, TITLE_MAX_LENGTH, "title");
            this.title = title;
        }
        if (description != null) {
            requireText(description, "description");
            this.description = description;
        }
        if (category != null) {
            requireMaxLength(category, CATEGORY_MAX_LENGTH, "category");
            this.category = category;
        }
        this.updatedAt = updatedAt;
    }

    // ------------------------------------------------------------------
    // Lifecycle behaviours - the ONLY place where frozen state-machine
    // transition rules live. Each method expresses a business action,
    // not a target status.
    // ------------------------------------------------------------------

    /**
     * Assigns this ticket to a user and moves it to ASSIGNED.
     *
     * <p>Valid from OPEN (fresh assignment) and from REOPENED (a new handling
     * cycle). The returned {@link AssignmentType} is the business meaning of
     * this act for the assignment history - callers must never derive it
     * themselves. Any other current status is an illegal transition.
     */
    public AssignmentType assign(Long assigneeId, OffsetDateTime now) {
        requireNow(now);
        if (assigneeId == null || assigneeId <= 0) {
            throw new TicketValidationException("assigneeId must be a positive id");
        }
        AssignmentType type;
        if (status == TicketStatus.OPEN) {
            type = AssignmentType.ASSIGN;
        } else if (status == TicketStatus.REOPENED) {
            type = AssignmentType.REASSIGN;
        } else {
            throw new IllegalTicketStateTransitionException("assign", status);
        }
        status = TicketStatus.ASSIGNED;
        currentAssigneeId = assigneeId;
        updatedAt = now;
        return type;
    }

    /**
     * ASSIGNED -> IN_PROGRESS. firstRespondedAt is stamped exactly once ever -
     * a later reopen / reassign / start cycle must never overwrite it.
     */
    public void startProgress(OffsetDateTime now) {
        requireNow(now);
        requireStatus(TicketStatus.ASSIGNED, "startProgress");
        status = TicketStatus.IN_PROGRESS;
        if (firstRespondedAt == null) {
            firstRespondedAt = now;
        }
        updatedAt = now;
    }

    /** IN_PROGRESS -> RESOLVED; resolvedAt is (re-)stamped on every resolve. */
    public void resolve(OffsetDateTime now) {
        requireNow(now);
        requireStatus(TicketStatus.IN_PROGRESS, "resolve");
        status = TicketStatus.RESOLVED;
        resolvedAt = now;
        updatedAt = now;
    }

    /** RESOLVED -> CLOSED, the terminal state. resolvedAt stays untouched. */
    public void close(OffsetDateTime now) {
        requireNow(now);
        requireStatus(TicketStatus.RESOLVED, "close");
        status = TicketStatus.CLOSED;
        closedAt = now;
        updatedAt = now;
    }

    /**
     * RESOLVED -> REOPENED: the resolution did not hold. resolvedAt is cleared;
     * currentAssigneeId is deliberately kept until a (re)assignment replaces
     * it; closedAt is unreachable from here (CLOSED has no exit transitions).
     */
    public void reopen(OffsetDateTime now) {
        requireNow(now);
        requireStatus(TicketStatus.RESOLVED, "reopen");
        status = TicketStatus.REOPENED;
        resolvedAt = null;
        updatedAt = now;
    }

    private void requireStatus(TicketStatus expected, String action) {
        if (status != expected) {
            throw new IllegalTicketStateTransitionException(action, status);
        }
    }

    private static void requireNow(OffsetDateTime now) {
        if (now == null) {
            throw new TicketValidationException("operation time must not be null");
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
