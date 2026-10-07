package dev.dongxin.serviceops.ticket.web.dto;

import java.time.OffsetDateTime;

/**
 * Single compact API error shape for this module.
 * Deliberately minimal: timestamp / status / error / message / path.
 */
public record ApiErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path) {

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(OffsetDateTime.now(), status, error, message, path);
    }
}
