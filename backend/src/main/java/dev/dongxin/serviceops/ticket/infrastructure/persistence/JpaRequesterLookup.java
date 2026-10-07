package dev.dongxin.serviceops.ticket.infrastructure.persistence;

import dev.dongxin.serviceops.ticket.application.port.RequesterLookup;
import org.springframework.stereotype.Component;

/**
 * Answers the application's "does this requester exist?" question from app_user.
 */
@Component
public class JpaRequesterLookup implements RequesterLookup {

    private final SpringDataAppUserRepository appUserTable;

    public JpaRequesterLookup(SpringDataAppUserRepository appUserTable) {
        this.appUserTable = appUserTable;
    }

    @Override
    public boolean existsById(Long requesterId) {
        return appUserTable.existsById(requesterId);
    }
}
