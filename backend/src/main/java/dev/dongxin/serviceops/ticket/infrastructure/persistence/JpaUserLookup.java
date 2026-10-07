package dev.dongxin.serviceops.ticket.infrastructure.persistence;

import dev.dongxin.serviceops.ticket.application.port.UserLookup;
import org.springframework.stereotype.Component;

/**
 * Answers the application's "does this user exist?" question from app_user.
 */
@Component
public class JpaUserLookup implements UserLookup {

    private final SpringDataAppUserRepository appUserTable;

    public JpaUserLookup(SpringDataAppUserRepository appUserTable) {
        this.appUserTable = appUserTable;
    }

    @Override
    public boolean existsById(Long userId) {
        return appUserTable.existsById(userId);
    }
}
