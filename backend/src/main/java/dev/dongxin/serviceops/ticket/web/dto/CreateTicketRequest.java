package dev.dongxin.serviceops.ticket.web.dto;

import dev.dongxin.serviceops.ticket.domain.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Client-supplied fields for ticket creation. Everything else (ticketNo,
 * status, assignee, SLA ids/deadlines, version, audit timestamps) is
 * server-generated and intentionally absent from this contract.
 */
public record CreateTicketRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @NotBlank
        String description,

        @NotBlank
        @Size(max = 64)
        String category,

        @NotNull
        TicketPriority priority,

        @NotNull
        @Positive
        Long requesterId) {
}
