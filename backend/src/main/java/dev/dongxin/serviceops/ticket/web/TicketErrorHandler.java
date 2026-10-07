package dev.dongxin.serviceops.ticket.web;

import dev.dongxin.serviceops.ticket.application.exception.ActorNotFoundException;
import dev.dongxin.serviceops.ticket.application.exception.AssigneeNotFoundException;
import dev.dongxin.serviceops.ticket.application.exception.RequesterNotFoundException;
import dev.dongxin.serviceops.ticket.application.exception.SlaPolicyNotConfiguredException;
import dev.dongxin.serviceops.ticket.application.exception.TicketNotFoundException;
import dev.dongxin.serviceops.ticket.application.exception.TicketStaleRevisionException;
import dev.dongxin.serviceops.ticket.domain.IllegalTicketStateTransitionException;
import dev.dongxin.serviceops.ticket.domain.TicketValidationException;
import dev.dongxin.serviceops.ticket.web.dto.ApiErrorResponse;
import dev.dongxin.serviceops.ticket.web.exception.InvalidTicketPatchRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(AssigneeNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> onAssigneeNotFound(AssigneeNotFoundException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "AssigneeNotFound", ex.getMessage(), request);
    }

    @ExceptionHandler(ActorNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> onActorNotFound(ActorNotFoundException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "ActorNotFound", ex.getMessage(), request);
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

    /**
     * Aggregate input / invariant violations only. IllegalArgumentException is
     * deliberately NOT mapped: programming errors must stay 500, not be
     * disguised as client 400s.
     */
    @ExceptionHandler(TicketValidationException.class)
    public ResponseEntity<ApiErrorResponse> onTicketValidation(TicketValidationException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "TicketValidation", ex.getMessage(), request);
    }

    /** PATCH body contract violation (unknown / forbidden field) - a web concern. */
    @ExceptionHandler(InvalidTicketPatchRequestException.class)
    public ResponseEntity<ApiErrorResponse> onInvalidPatchRequest(InvalidTicketPatchRequestException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "InvalidPatchRequest", ex.getMessage(), request);
    }

    @ExceptionHandler(TicketStaleRevisionException.class)
    public ResponseEntity<ApiErrorResponse> onStaleRevision(TicketStaleRevisionException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "TicketStaleRevision", ex.getMessage(), request);
    }

    /**
     * The frozen state machine rejected the action for this ticket's current
     * lifecycle state: the request is well-formed but conflicts with the
     * resource state, hence 409 - not 400.
     */
    @ExceptionHandler(IllegalTicketStateTransitionException.class)
    public ResponseEntity<ApiErrorResponse> onIllegalTransition(IllegalTicketStateTransitionException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "IllegalTicketStateTransition", ex.getMessage(), request);
    }

    /** Real race window hit by JPA @Version after the application pre-check. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> onOptimisticLock(OptimisticLockingFailureException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "TicketStaleRevision",
                "Ticket was modified concurrently; reload and retry", request);
    }

    /** Invalid enum query parameter (status / priority) or non-numeric page / size. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> onTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                           HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "InvalidParameter",
                "Query parameter '" + ex.getName() + "' has an invalid value", request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String error,
                                                   String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status.value(), error, message, request.getRequestURI()));
    }
}
