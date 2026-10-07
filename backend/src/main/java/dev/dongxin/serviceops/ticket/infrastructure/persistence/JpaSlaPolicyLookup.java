package dev.dongxin.serviceops.ticket.infrastructure.persistence;

import dev.dongxin.serviceops.ticket.application.port.SlaPolicyData;
import dev.dongxin.serviceops.ticket.application.port.SlaPolicyLookup;
import dev.dongxin.serviceops.ticket.domain.TicketPriority;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Maps the sla_policy row to the application-level {@link SlaPolicyData} carrier.
 * The JPA entity never leaves this package.
 */
@Component
public class JpaSlaPolicyLookup implements SlaPolicyLookup {

    private final SpringDataSlaPolicyRepository slaPolicyTable;

    public JpaSlaPolicyLookup(SpringDataSlaPolicyRepository slaPolicyTable) {
        this.slaPolicyTable = slaPolicyTable;
    }

    @Override
    public Optional<SlaPolicyData> findEnabledByPriority(TicketPriority priority) {
        return slaPolicyTable.findByPriorityAndEnabled(priority, true)
                .map(policy -> new SlaPolicyData(
                        policy.getId(),
                        policy.getResponseMinutes(),
                        policy.getResolutionMinutes()));
    }
}
