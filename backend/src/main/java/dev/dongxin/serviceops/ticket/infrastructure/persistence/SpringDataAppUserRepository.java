package dev.dongxin.serviceops.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for app_user. Internal to the persistence layer;
 * the application uses the adapter implementing the UserLookup port.
 */
public interface SpringDataAppUserRepository extends JpaRepository<AppUserJpaEntity, Long> {
}
