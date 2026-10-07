package dev.dongxin.serviceops.ticket.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Shared minimal body for the start / resolve / close / reopen actions:
 * only the optimistic-lock token. No actorId - this slice writes no audit
 * record, so an actor field would have no persistence consumer.
 */
public record TicketActionRequest(

        @NotNull
        @Min(0)
        Integer version) {
}
