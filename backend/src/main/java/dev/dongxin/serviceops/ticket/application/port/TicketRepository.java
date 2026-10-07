package dev.dongxin.serviceops.ticket.application.port;

import dev.dongxin.serviceops.ticket.domain.Ticket;

import java.util.Optional;

/**
 * Persistence port for the Ticket aggregate, limited to what the current
 * slice (create + get) actually needs. No update/list methods until a use
 * case requires them.
 */
public interface TicketRepository {

    /**
     * Persists a brand-new ticket and returns the fully populated domain object,
     * including the identifiers assigned by the database (id, version).
     */
    Ticket saveNew(Ticket ticket);

    Optional<Ticket> findById(Long id);
}
