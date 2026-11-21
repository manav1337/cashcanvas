package com.cashcanvas.common.exception;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;


import jakarta.validation.ConstraintViolationException;

/**
 * Every error leaves the API as application/problem+json (RFC 7807) with a stable "code".
 * Extending ResponseEntityExceptionHandler gives us correct statuses for Spring MVC's own
 * exceptions (405, 415, 413, missing params, unreadable JSON ...); we only add our fields.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String RETRY_AFTER_SECONDS = "60";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        ErrorCode code = ex.getErrorCode();
        if (code.status().is5xxServerError()) {
            log.error("API error code={}", code, ex);
        } else {
            log.debug("API error code={} detail={}", code, ex.getMessage());
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (code.status() == HttpStatus.TOO_MANY_REQUESTS) {
            response.header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        }
        return response.body(ProblemDetails.of(code, ex.getMessage()));
    }

    /** Violations on @Validated method parameters, e.g. ?month=2026-13 (used from Day 4). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetails.of(ErrorCode.VALIDATION_FAILED, "One or more parameters are invalid");
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of(
                        "field", v.getPropertyPath().toString(),
                        "message", v.getMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /**
     * Spring Security's StrictHttpFirewall rejects suspicious requests, e.g. a header value containing
     * a newline (log/header injection). It checks header values lazily, when they are first read,
     * so the exception can surface inside the controller layer. It is the client's fault: 400, not 500.
     */
    @ExceptionHandler(RequestRejectedException.class)
    public ResponseEntity<ProblemDetail> handleRejectedRequest(RequestRejectedException ex) {
        log.warn("Request rejected by firewall: {}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemDetails.of(ErrorCode.MALFORMED_REQUEST, "The request contains invalid characters"));
    }

    /** Last resort: log the stack trace, return a generic message. Never leak internals. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemDetails.of(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred"));
    }

    /** @Valid @RequestBody failures: list each bad field so the UI can highlight it. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetails.of(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", Objects.requireNonNullElse(fe.getDefaultMessage(), "is invalid")))
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Adds code/timestamp/correlationId to the ProblemDetails Spring builds for its own exceptions. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            boolean alreadyDecorated = problem.getProperties() != null && problem.getProperties().containsKey("code");
            if (!alreadyDecorated) {
                ErrorCode code = ex instanceof MaxUploadSizeExceededException
                        ? ErrorCode.FILE_TOO_LARGE
                        : ErrorCode.fromStatus(statusCode);
                ProblemDetails.decorate(problem, code);
            }
        }
        return response;
    }
}
