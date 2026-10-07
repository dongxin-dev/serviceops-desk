package dev.dongxin.serviceops.ticket.application.port;

import java.util.List;

/**
 * Pure application-level pagination carrier. Spring Data Page / Pageable are
 * infrastructure-only types and must never surface through a port.
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PageResult {
        content = List.copyOf(content);
    }
}
