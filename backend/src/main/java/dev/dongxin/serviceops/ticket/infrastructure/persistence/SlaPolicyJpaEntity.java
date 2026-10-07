package dev.dongxin.serviceops.ticket.infrastructure.persistence;

import dev.dongxin.serviceops.ticket.domain.TicketPriority;
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
 * Minimal read-only mapping of {@code sla_policy}, used by the Ticket create
 * use case to resolve deadlines from the ticket priority (ADR-003 snapshot source).
 */
@Entity
@Table(name = "sla_policy")
public class SlaPolicyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private TicketPriority priority;

    @Column(name = "response_minutes", nullable = false)
    private int responseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SlaPolicyJpaEntity() {
        // required by JPA
    }

    public SlaPolicyJpaEntity(String name, TicketPriority priority, int responseMinutes,
                              int resolutionMinutes, boolean enabled,
                              OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.name = name;
        this.priority = priority;
        this.responseMinutes = responseMinutes;
        this.resolutionMinutes = resolutionMinutes;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public int getResponseMinutes() {
        return responseMinutes;
    }

    public int getResolutionMinutes() {
        return resolutionMinutes;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
