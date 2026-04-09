package com.fintrack.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.validation.FieldError;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.time.Instant;
import java.util.*;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    public record ErrorResponse(
            int status,
            String error,
            String message,
            String path,
            Instant timestamp,
            Map<String, String> fieldErrors
    ) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        log.warn("API Exception: {}", ex.getMessage());
        return build(ex.getStatus(), ex.getMessage(), null, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field = err instanceof FieldError fe ? fe.getField() : err.getObjectName();
            errors.put(field, err.getDefaultMessage());
        });
        log.warn("Validation failed: {}", errors);
        return build(HttpStatus.BAD_REQUEST, "Validation failed", null, errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        String message = "Missing required parameter: " + ex.getParameterName();
        log.warn(message);
        return build(HttpStatus.BAD_REQUEST, message, null, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials attempt");
        return build(HttpStatus.UNAUTHORIZED, "Invalid email or password", null, null);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex) {
        log.warn("Disabled account access attempt: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Account is disabled", null, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Access denied", null, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded statement is too large. Keep files under 50MB.", null, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        if ("statementDate".equals(ex.getName())) {
            log.warn("Invalid statement date format");
            return build(HttpStatus.BAD_REQUEST, "Statement date must use the YYYY-MM-DD format.", null, null);
        }
        log.warn("Type mismatch for parameter: {}", ex.getName());
        return build(HttpStatus.BAD_REQUEST, "One of the request values is invalid.", null, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Resource not found", ex.getResourcePath(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("HTTP method not supported: {}", ex.getMessage());
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), null, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred: " + ex.getMessage(), null, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message,
                                                  String path, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                Instant.now(),
                fieldErrors
        ));
    }
}

