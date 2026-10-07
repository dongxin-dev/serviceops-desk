package dev.dongxin.serviceops.ticket.application;

import dev.dongxin.serviceops.ticket.domain.TicketValidationException;

/**
 * Command for the basic-info patch use case.
 * null business field = keep current value (explicit nulls are rejected at the
 * web boundary). version is the optimistic-lock token, NOT a business field:
 * a body carrying only version is still an empty patch.
 */
public record UpdateTicketCommand(
        Long id,
        String title,
        String description,
        String category,
        Integer version) {

    public UpdateTicketCommand {
        if (title == null && description == null && category == null) {
            throw new TicketValidationException(
                    "PATCH must provide at least one of: title, description, category");
        }
    }
}
