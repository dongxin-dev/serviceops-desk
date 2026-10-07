package dev.dongxin.serviceops.ticket.infrastructure.persistence;

import dev.dongxin.serviceops.ticket.application.ListTicketsQuery;
import dev.dongxin.serviceops.ticket.application.exception.TicketStaleRevisionException;
import dev.dongxin.serviceops.ticket.application.port.PageResult;
import dev.dongxin.serviceops.ticket.application.port.TicketRepository;
import dev.dongxin.serviceops.ticket.domain.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Implements the application TicketRepository port with Spring Data JPA.
 * Owns all domain &lt;-&gt; entity conversion; the application layer never sees
 * a JPA type, and the domain never learns about the insert mechanics.
 */
@Component
public class JpaTicketRepositoryAdapter implements TicketRepository {

    private final SpringDataTicketRepository ticketTable;

    public JpaTicketRepositoryAdapter(SpringDataTicketRepository ticketTable) {
        this.ticketTable = ticketTable;
    }

    @Override
    public Ticket saveNew(Ticket ticket) {
        TicketJpaEntity saved = ticketTable.save(TicketJpaMapper.toNewEntity(ticket));
        return TicketJpaMapper.toDomain(saved);
    }

    @Override
    public Optional<Ticket> findById(Long id) {
        return ticketTable.findById(id).map(TicketJpaMapper::toDomain);
    }

    @Override
    public PageResult<Ticket> search(ListTicketsQuery query) {
        // Fixed deterministic order; clients cannot control sorting in V1.
        Pageable pageable = PageRequest.of(query.page(), query.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<TicketJpaEntity> result;
        if (query.status() != null && query.priority() != null) {
            result = ticketTable.findByStatusAndPriority(query.status(), query.priority(), pageable);
        } else if (query.status() != null) {
            result = ticketTable.findByStatus(query.status(), pageable);
        } else if (query.priority() != null) {
            result = ticketTable.findByPriority(query.priority(), pageable);
        } else {
            result = ticketTable.findAll(pageable);
        }

        return new PageResult<>(
                result.getContent().stream().map(TicketJpaMapper::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    /**
     * Update strategy: load the MANAGED entity, verify the aggregate snapshot
     * still carries the current database version (the port rejects a stale
     * snapshot on its own instead of relying on a shared persistence context),
     * copy the mutable state onto the managed entity, flush, and map back.
     * Never re-create an entity via toNewEntity, never merge a detached copy,
     * never touch version - JPA @Version + saveAndFlush stay the final
     * concurrency guard for real race windows.
     */
    @Override
    public Ticket update(Ticket ticket) {
        TicketJpaEntity managed = ticketTable.findById(ticket.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "update() called for a non-existent ticket: " + ticket.getId()));
        if (!Objects.equals(managed.getVersion(), ticket.getVersion())) {
            throw new TicketStaleRevisionException(ticket.getId(), managed.getVersion(),
                    ticket.getVersion());
        }
        managed.applyMutableState(ticket.getTitle(), ticket.getDescription(),
                ticket.getCategory(), ticket.getUpdatedAt());
        TicketJpaEntity flushed = ticketTable.saveAndFlush(managed);
        return TicketJpaMapper.toDomain(flushed);
    }
}
