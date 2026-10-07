package dev.dongxin.serviceops.ticket.application.port;

/**
 * Business capability needed by create: "does this requester exist?".
 * Deliberately not a user repository; no JPA entity is exposed.
 */
public interface RequesterLookup {

    boolean existsById(Long requesterId);
}
