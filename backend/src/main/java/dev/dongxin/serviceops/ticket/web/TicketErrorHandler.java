package dev.dongxin.serviceops.ticket.web;

import dev.dongxin.serviceops.ticket.application.exception.RequesterNotFoundException;
import dev.dongxin.serviceops.ticket.application.exception.SlaPolicyNotConfiguredException;
import dev.dongxin.serviceops.ticket.application.exception.TicketNotFoundException;
import dev.dongxin.serviceops.ticket.web.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Compact error mapping for the Ticket API. Scoped to this module's controllers
 * so Spring's default handling stays untouched elsewhere.
 */
@RestControllerAdvice(assignableTypes = TicketController.class)
public class TicketErrorHandler {

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> onTicketNotFound(TicketNotFoundException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "TicketNotFound", ex.getMessage(), request);
    }

    @ExceptionHandler(RequesterNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> onRequesterNotFound(RequesterNotFoundException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "RequesterNotFound", ex.getMessage(), request);
    }

    @ExceptionHandler(SlaPolicyNotConfiguredException.class)
    public ResponseEntity<ApiErrorResponse> onSlaNotConfigured(SlaPolicyNotConfiguredException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "SlaPolicyNotConfigured", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> onValidation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "ValidationError", message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> onUnreadable(HttpMessageNotReadableException ex,
                                                         HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MalformedRequest", "Request body is missing or malformed", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> onDataIntegrity(DataIntegrityViolationException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "DataIntegrityConflict",
                "Request conflicts with database constraints", request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String error,
                                                   String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status.value(), error, message, request.getRequestURI()));
    }
}
