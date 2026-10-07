package dev.dongxin.serviceops.ticket.application.port;

/**
 * Minimal SLA facts required by the create-ticket use case.
 * Not a domain entity and not a JPA entity - just a data carrier across the port.
 */
public record SlaPolicyData(
        Long id,
        int responseMinutes,
        int resolutionMinutes) {
}
