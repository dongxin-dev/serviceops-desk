package dev.dongxin.serviceops.ticket.application;

import dev.dongxin.serviceops.ticket.domain.TicketPriority;
import dev.dongxin.serviceops.ticket.domain.TicketStatus;
import dev.dongxin.serviceops.ticket.domain.TicketValidationException;

/**
 * Application query model for the ticket list use case.
 * status / priority are optional filters (null = no filter); sorting is NOT
 * client-controllable in V1 - the infrastructure applies a fixed deterministic
 * order (createdAt DESC, id DESC).
 */
public record ListTicketsQuery(
        TicketStatus status,
        TicketPriority priority,
        int page,
        int size) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public ListTicketsQuery {
        if (page < 0) {
            throw new TicketValidationException("page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new TicketValidationException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
