package dev.dongxin.serviceops.ticket.web.dto;

import dev.dongxin.serviceops.ticket.application.port.PageResult;
import dev.dongxin.serviceops.ticket.domain.Ticket;

import java.util.List;

/**
 * Stable pagination envelope. Maps the application-level PageResult; the
 * Spring Data Page type never reaches this layer.
 */
public record TicketListResponse(
        List<TicketSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static TicketListResponse from(PageResult<Ticket> result) {
        return new TicketListResponse(
                result.content().stream().map(TicketSummaryResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}
