package dev.dongxin.serviceops.ticket.infrastructure.persistence;

import dev.dongxin.serviceops.ticket.application.port.TicketRepository;
import dev.dongxin.serviceops.ticket.domain.Ticket;
import org.springframework.stereotype.Component;

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
}
