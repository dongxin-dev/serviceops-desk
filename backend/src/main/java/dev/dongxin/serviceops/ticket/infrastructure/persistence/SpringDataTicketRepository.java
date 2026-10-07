package dev.dongxin.serviceops.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for the ticket table. Internal to the persistence
 * layer; the application talks to {@link JpaTicketRepositoryAdapter} via the
 * application port instead.
 */
public interface SpringDataTicketRepository extends JpaRepository<TicketJpaEntity, Long> {
}
