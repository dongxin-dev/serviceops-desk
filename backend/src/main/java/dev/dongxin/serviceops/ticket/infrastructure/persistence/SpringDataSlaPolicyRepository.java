package dev.dongxin.serviceops.ticket.infrastructure.persistence;

import dev.dongxin.serviceops.ticket.domain.TicketPriority;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for sla_policy. Internal to the persistence layer;
 * the application uses the adapter implementing the SlaPolicyLookup port.
 */
public interface SpringDataSlaPolicyRepository extends JpaRepository<SlaPolicyJpaEntity, Long> {

    Optional<SlaPolicyJpaEntity> findByPriorityAndEnabled(TicketPriority priority, boolean enabled);
}
