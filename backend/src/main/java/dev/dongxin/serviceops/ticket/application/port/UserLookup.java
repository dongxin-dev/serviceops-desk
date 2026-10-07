package dev.dongxin.serviceops.ticket.application.port;

/**
 * Business capability "does this user exist?" against app_user. Shared by
 * create (requester check) and assign (assignee / actor check) - existence
 * ONLY: V1 defines no ACTIVE or role restrictions, none are invented here.
 */
public interface UserLookup {

    boolean existsById(Long userId);
}
