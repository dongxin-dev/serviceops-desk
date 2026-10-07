package dev.dongxin.serviceops.ticket.application.port;

import dev.dongxin.serviceops.ticket.domain.TicketPriority;

import java.util.Optional;

/**
 * SLA policy lookup needed by create. Returns a lightweight application-level
 * carrier; the SLA persistence model never crosses into the application layer.
 */
public interface SlaPolicyLookup {

    Optional<SlaPolicyData> findEnabledByPriority(TicketPriority priority);
}
