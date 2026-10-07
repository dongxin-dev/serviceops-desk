package dev.dongxin.serviceops.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for the ticket_assignment table. Internal to the
 * persistence layer; the application talks to the TicketAssignmentHistory
 * port instead. Only the lookup the closeActive use case truly needs.
 */
public interface SpringDataTicketAssignmentRepository
        extends JpaRepository<TicketAssignmentJpaEntity, Long> {

    Optional<TicketAssignmentJpaEntity> findByTicketIdAndEndedAtIsNull(Long ticketId);
}
