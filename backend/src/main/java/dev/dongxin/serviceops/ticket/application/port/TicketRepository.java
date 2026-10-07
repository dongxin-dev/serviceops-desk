package dev.dongxin.serviceops.ticket.application.port;

import dev.dongxin.serviceops.ticket.application.ListTicketsQuery;
import dev.dongxin.serviceops.ticket.domain.Ticket;

import java.util.Optional;

/**
 * Persistence port for the Ticket aggregate, limited to what the current
 * slices (create + get + list + basic-info patch) actually need.
 */
public interface TicketRepository {

    /**
     * Persists a brand-new ticket and returns the fully populated domain object,
     * including the identifiers assigned by the database (id, version).
     */
    Ticket saveNew(Ticket ticket);

    Optional<Ticket> findById(Long id);

    /** Pageable ticket list with optional status / priority filters. */
    PageResult<Ticket> search(ListTicketsQuery query);

    /**
     * Persists changes made to an existing aggregate and returns the updated
     * domain object carrying the new database-managed version.
     */
    Ticket update(Ticket ticket);
}
