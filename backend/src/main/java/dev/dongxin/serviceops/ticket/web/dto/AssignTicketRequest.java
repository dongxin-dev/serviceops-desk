package dev.dongxin.serviceops.ticket.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Assign action body: who takes the ticket, who acts, and the optimistic-lock
 * token. Existence of both users is verified application-side; V1 defines no
 * ACTIVE / role requirement, so none is checked here.
 */
public record AssignTicketRequest(

        @NotNull
        @Positive
        Long assigneeId,

        @NotNull
        @Positive
        Long actorId,

        @NotNull
        @Min(0)
        Integer version) {
}
