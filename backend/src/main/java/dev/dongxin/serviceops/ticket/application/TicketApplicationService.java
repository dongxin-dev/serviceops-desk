package dev.dongxin.serviceops.ticket.application;

import dev.dongxin.serviceops.ticket.application.exception.ActorNotFoundException;
import dev.dongxin.serviceops.ticket.application.exception.AssigneeNotFoundException;
import dev.dongxin.serviceops.ticket.application.exception.RequesterNotFoundException;
import dev.dongxin.serviceops.ticket.application.exception.SlaPolicyNotConfiguredException;
import dev.dongxin.serviceops.ticket.application.exception.TicketNotFoundException;
import dev.dongxin.serviceops.ticket.application.exception.TicketStaleRevisionException;
import dev.dongxin.serviceops.ticket.application.port.NewTicketAssignment;
import dev.dongxin.serviceops.ticket.application.port.PageResult;
import dev.dongxin.serviceops.ticket.application.port.SlaPolicyData;
import dev.dongxin.serviceops.ticket.application.port.SlaPolicyLookup;
import dev.dongxin.serviceops.ticket.application.port.TicketAssignmentHistory;
import dev.dongxin.serviceops.ticket.application.port.TicketRepository;
import dev.dongxin.serviceops.ticket.application.port.UserLookup;
import dev.dongxin.serviceops.ticket.domain.AssignmentType;
import dev.dongxin.serviceops.ticket.domain.Ticket;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Use-case orchestration for the Ticket module.
 *
 * <p>Depends only on the domain, application ports / commands / exceptions and
 * Spring application-level annotations. Persistence details live behind the
 * ports and never leak in here.
 */
@Service
public class TicketApplicationService {

    private final TicketRepository ticketRepository;
    private final UserLookup userLookup;
    private final SlaPolicyLookup slaPolicyLookup;
    private final TicketAssignmentHistory assignmentHistory;

    public TicketApplicationService(TicketRepository ticketRepository,
                                    UserLookup userLookup,
                                    SlaPolicyLookup slaPolicyLookup,
                                    TicketAssignmentHistory assignmentHistory) {
        this.ticketRepository = ticketRepository;
        this.userLookup = userLookup;
        this.slaPolicyLookup = slaPolicyLookup;
        this.assignmentHistory = assignmentHistory;
    }

    /**
     * Creates a ticket in one transaction: validate requester, resolve SLA policy,
     * snapshot deadlines, generate ticket_no, persist, return the saved domain object.
     */
    @Transactional
    public Ticket create(CreateTicketCommand command) {
        if (!userLookup.existsById(command.requesterId())) {
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

    @Transactional(readOnly = true)
    public PageResult<Ticket> list(ListTicketsQuery query) {
        return ticketRepository.search(query);
    }

    /**
     * Basic-info patch. Pre-checks the optimistic-lock token for a clear
     * business error; JPA @Version remains the final concurrency guard
     * (races surface as a Spring OptimisticLocking failure mapped to 409
     * by the web layer). Version is never incremented by business code.
     */
    @Transactional
    public Ticket patch(UpdateTicketCommand command) {
        Ticket ticket = ticketRepository.findById(command.id())
                .orElseThrow(() -> new TicketNotFoundException(command.id()));

        requireVersion(ticket, command.version());

        ticket.updateBasicInfo(command.title(), command.description(), command.category(),
                OffsetDateTime.now());
        return ticketRepository.update(ticket);
    }

    /**
     * Assigns the ticket and records the assignment history atomically: one
     * transaction covers ticket update + closeActive + append; any failure
     * rolls the whole thing back. The ASSIGN vs REASSIGN decision is made by
     * the domain (returned from ticket.assign), never derived here.
     * closeActive runs before append because the partial unique index allows
     * only one active row per ticket.
     */
    @Transactional
    public Ticket assign(Long id, Long assigneeId, Long actorId, Integer version) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
        if (!userLookup.existsById(assigneeId)) {
            throw new AssigneeNotFoundException(assigneeId);
        }
        if (!userLookup.existsById(actorId)) {
            throw new ActorNotFoundException(actorId);
        }
        requireVersion(ticket, version);

        OffsetDateTime now = OffsetDateTime.now();
        AssignmentType type = ticket.assign(assigneeId, now);
        Ticket updated = ticketRepository.update(ticket);

        assignmentHistory.closeActive(id, now);
        assignmentHistory.append(new NewTicketAssignment(id, assigneeId, actorId, type, now, now));
        return updated;
    }

    @Transactional
    public Ticket start(Long id, Integer version) {
        return transition(id, version, ticket -> ticket.startProgress(OffsetDateTime.now()));
    }

    @Transactional
    public Ticket resolve(Long id, Integer version) {
        return transition(id, version, ticket -> ticket.resolve(OffsetDateTime.now()));
    }

    @Transactional
    public Ticket close(Long id, Integer version) {
        return transition(id, version, ticket -> ticket.close(OffsetDateTime.now()));
    }

    @Transactional
    public Ticket reopen(Long id, Integer version) {
        return transition(id, version, ticket -> ticket.reopen(OffsetDateTime.now()));
    }

    /**
     * Shared lifecycle-action shape: load, version pre-check, ONE domain
     * behaviour call, persist. No status inspection happens here - illegal
     * transitions are thrown by the aggregate and mapped by the web layer.
     */
    private Ticket transition(Long id, Integer expectedVersion,
                              java.util.function.Consumer<Ticket> action) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
        requireVersion(ticket, expectedVersion);
        action.accept(ticket);
        return ticketRepository.update(ticket);
    }

    private static void requireVersion(Ticket ticket, Integer expectedVersion) {
        if (!ticket.getVersion().equals(expectedVersion)) {
            throw new TicketStaleRevisionException(ticket.getId(), ticket.getVersion(), expectedVersion);
        }
    }
}
