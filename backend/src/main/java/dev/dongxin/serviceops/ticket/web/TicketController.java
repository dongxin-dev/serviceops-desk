package dev.dongxin.serviceops.ticket.web;

import dev.dongxin.serviceops.ticket.application.CreateTicketCommand;
import dev.dongxin.serviceops.ticket.application.TicketApplicationService;
import dev.dongxin.serviceops.ticket.domain.Ticket;
import dev.dongxin.serviceops.ticket.web.dto.CreateTicketRequest;
import dev.dongxin.serviceops.ticket.web.dto.TicketResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
