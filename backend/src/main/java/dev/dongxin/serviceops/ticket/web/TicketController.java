package dev.dongxin.serviceops.ticket.web;

import dev.dongxin.serviceops.ticket.application.CreateTicketCommand;
import dev.dongxin.serviceops.ticket.application.ListTicketsQuery;
import dev.dongxin.serviceops.ticket.application.TicketApplicationService;
import dev.dongxin.serviceops.ticket.application.port.PageResult;
import dev.dongxin.serviceops.ticket.domain.Ticket;
import dev.dongxin.serviceops.ticket.domain.TicketPriority;
import dev.dongxin.serviceops.ticket.domain.TicketStatus;
import dev.dongxin.serviceops.ticket.web.dto.AssignTicketRequest;
import dev.dongxin.serviceops.ticket.web.dto.CreateTicketRequest;
import dev.dongxin.serviceops.ticket.web.dto.TicketActionRequest;
import dev.dongxin.serviceops.ticket.web.dto.TicketListResponse;
import dev.dongxin.serviceops.ticket.web.dto.TicketResponse;
import dev.dongxin.serviceops.ticket.web.dto.UpdateTicketRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Thin HTTP layer. All business decisions live in application / domain.
 */
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketApplicationService ticketService;

    public TicketController(TicketApplicationService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        Ticket ticket = ticketService.create(new CreateTicketCommand(
                request.title(),
                request.description(),
                request.category(),
                request.priority(),
                request.requesterId()));

        URI location = UriComponentsBuilder.fromPath("/api/v1/tickets/{id}")
                .buildAndExpand(ticket.getId())
                .toUri();
        return ResponseEntity.created(location).body(TicketResponse.from(ticket));
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable Long id) {
        return TicketResponse.from(ticketService.get(id));
    }

    /**
     * Paginated list. Sorting is NOT client-controllable in V1; the fixed
     * deterministic order lives in the infrastructure adapter. Page-size bounds
     * are re-checked by the ListTicketsQuery constructor (-> 400 via
     * TicketValidationException mapping).
     */
    @GetMapping
    public TicketListResponse list(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<Ticket> result = ticketService.list(new ListTicketsQuery(status, priority, page, size));
        return TicketListResponse.from(result);
    }

    /**
     * Basic-info patch (title / description / category only). Presence-vs-null
     * and unknown-field rules are enforced by UpdateTicketRequest itself; the
     * version token is pre-checked in the application service.
     */
    @PatchMapping("/{id}")
    public TicketResponse patch(@PathVariable Long id,
                                @Valid @RequestBody UpdateTicketRequest request) {
        return TicketResponse.from(ticketService.patch(request.toCommand(id)));
    }

    /**
     * Lifecycle actions. Each endpoint is one explicit business action - there
     * is no generic status patch; legality is decided solely by the domain.
     * Success returns the full ticket with the freshly incremented version.
     */
    @PostMapping("/{id}/assign")
    public TicketResponse assign(@PathVariable Long id,
                                 @Valid @RequestBody AssignTicketRequest request) {
        return TicketResponse.from(ticketService.assign(id, request.assigneeId(),
                request.actorId(), request.version()));
    }

    @PostMapping("/{id}/start")
    public TicketResponse start(@PathVariable Long id,
                                @Valid @RequestBody TicketActionRequest request) {
        return TicketResponse.from(ticketService.start(id, request.version()));
    }

    @PostMapping("/{id}/resolve")
    public TicketResponse resolve(@PathVariable Long id,
                                  @Valid @RequestBody TicketActionRequest request) {
        return TicketResponse.from(ticketService.resolve(id, request.version()));
    }

    @PostMapping("/{id}/close")
    public TicketResponse close(@PathVariable Long id,
                                @Valid @RequestBody TicketActionRequest request) {
        return TicketResponse.from(ticketService.close(id, request.version()));
    }

    @PostMapping("/{id}/reopen")
    public TicketResponse reopen(@PathVariable Long id,
                                 @Valid @RequestBody TicketActionRequest request) {
        return TicketResponse.from(ticketService.reopen(id, request.version()));
    }
}
