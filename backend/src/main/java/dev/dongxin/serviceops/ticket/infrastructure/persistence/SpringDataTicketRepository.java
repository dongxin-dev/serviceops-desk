package dev.dongxin.serviceops.ticket.infrastructure.persistence;

import dev.dongxin.serviceops.ticket.domain.TicketPriority;
import dev.dongxin.serviceops.ticket.domain.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for the ticket table. Internal to the persistence
 * layer; the application talks to {@link JpaTicketRepositoryAdapter} via the
 * application port instead.
 */
public interface SpringDataTicketRepository extends JpaRepository<TicketJpaEntity, Long> {

    Page<TicketJpaEntity> findByStatus(TicketStatus status, Pageable pageable);

    Page<TicketJpaEntity> findByPriority(TicketPriority priority, Pageable pageable);

    Page<TicketJpaEntity> findByStatusAndPriority(TicketStatus status, TicketPriority priority,
                                                  Pageable pageable);
}
