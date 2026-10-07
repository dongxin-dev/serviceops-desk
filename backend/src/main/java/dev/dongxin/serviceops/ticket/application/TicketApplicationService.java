package dev.dongxin.serviceops.ticket.application;

import dev.dongxin.serviceops.ticket.application.exception.RequesterNotFoundException;
import dev.dongxin.serviceops.ticket.application.exception.SlaPolicyNotConfiguredException;
import dev.dongxin.serviceops.ticket.application.exception.TicketNotFoundException;
import dev.dongxin.serviceops.ticket.application.port.RequesterLookup;
import dev.dongxin.serviceops.ticket.application.port.SlaPolicyData;
import dev.dongxin.serviceops.ticket.application.port.SlaPolicyLookup;
import dev.dongxin.serviceops.ticket.application.port.TicketRepository;
import dev.dongxin.serviceops.ticket.domain.Ticket;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Use-case orchestration for the Ticket module (V1 slice: create + get).
 *
 * <p>Depends only on the domain, application ports / commands / exceptions and
 * Spring application-level annotations. Persistence details live behind the
 * ports and never leak in here.
 */
@Service
public class TicketApplicationService {

    private final TicketRepository ticketRepository;
    private final RequesterLookup requesterLookup;
    private final SlaPolicyLookup slaPolicyLookup;

    public TicketApplicationService(TicketRepository ticketRepository,
                                    RequesterLookup requesterLookup,
                                    SlaPolicyLookup slaPolicyLookup) {
        this.ticketRepository = ticketRepository;
        this.requesterLookup = requesterLookup;
        this.slaPolicyLookup = slaPolicyLookup;
    }

    /**
     * Creates a ticket in one transaction: validate requester, resolve SLA policy,
     * snapshot deadlines, generate ticket_no, persist, return the saved domain object.
     */
    @Transactional
    public Ticket create(CreateTicketCommand command) {
        if (!requesterLookup.existsById(command.requesterId())) {
            throw new RequesterNotFoundException(command.requesterId());
        }

        SlaPolicyData policy = slaPolicyLookup
                .findEnabledByPriority(command.priority())
                .orElseThrow(() -> new SlaPolicyNotConfiguredException(command.priority()));

        OffsetDateTime createdAt = OffsetDateTime.now();
        String ticketNo = TicketNumberGenerator.next(createdAt);

        Ticket ticket = Ticket.open(
                ticketNo,
                command.title(),
                command.description(),
                command.category(),
                command.priority(),
                command.requesterId(),
                policy.id(),
                createdAt,
                createdAt.plusMinutes(policy.responseMinutes()),
                createdAt.plusMinutes(policy.resolutionMinutes()));

        return ticketRepository.saveNew(ticket);
    }

    @Transactional(readOnly = true)
    public Ticket get(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
    }
}
