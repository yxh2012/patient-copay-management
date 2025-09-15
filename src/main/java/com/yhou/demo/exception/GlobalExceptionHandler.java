package com.yhou.demo.exception;

import com.yhou.demo.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Global exception handler for standardized API error responses and logging.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();

        // structured logging per type
        if (ex instanceof ResourceNotFoundException) {
            log.warn("Resource not found: {}", ex.getMessage());
        } else if (ex instanceof InvalidInputException) {
            log.warn("Invalid input: {}", ex.getMessage());
        } else if (ex instanceof InvalidParameterException) {
            log.warn("Invalid parameter: {}", ex.getMessage());
        } else if (ex instanceof BusinessValidationException) {
            log.warn("Business validation failed: {}", ex.getMessage());
        } else if (ex instanceof DuplicateRequestException) {
            log.info("Duplicate request: {}", ex.getMessage());
        } else {
            log.warn("API exception: {}", ex.getMessage());
        }

        return buildErrorResponse(code, ex.getMessage(), request);
    }

    /**
     * Missing required header
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex,
            HttpServletRequest request
    ) {
        String message = String.format("Missing required header: %s", ex.getHeaderName());
        log.warn("Missing request header: {}", message);

        return buildErrorResponse(ErrorCode.MISSING_HEADER, message, request);
    }

    /**
     * Malformed JSON or invalid enum values
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        log.warn("Invalid request body: {}", ex.getMessage());
        return buildErrorResponse(ErrorCode.INPUT_INVALID, "Malformed or unsupported input value", request);
    }

    /**
     * Bean validation failures (e.g. @NotNull, @Min, @Size)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> String.format("Field '%s': %s", error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining("; "));

        log.warn("Validation failed: {}", message);
        return buildErrorResponse(ErrorCode.VALIDATION_ERROR, message, request);
    }

    /**
     * Constraint violations outside of request body (e.g. service layer checks)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> String.format("%s %s", v.getPropertyPath(), v.getMessage()))
                .collect(Collectors.joining("; "));

        log.warn("Constraint violation: {}", message);
        return buildErrorResponse(ErrorCode.VALIDATION_ERROR, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error", ex);
        return buildErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(ErrorCode code, String message, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .errorCode(code.name())
                .message(message)
                .retryable(isRetryable(code))
                .build();

        return ResponseEntity.status(code.getHttpStatus()).body(response);
    }

    private boolean isRetryable(ErrorCode code) {
        return switch (code) {
            // Retryable errors (temporary/server issues)
            case INTERNAL_SERVER_ERROR -> true;

            // Non-retryable errors (client/permanent issues)
            case RESOURCE_NOT_FOUND -> false;
            case VALIDATION_ERROR -> false;
            case INPUT_INVALID -> false;
            case MISSING_HEADER -> false;
            case DUPLICATE_REQUEST -> false;
            case BUSINESS_VALIDATION_ERROR -> false;

            // Default to non-retryable for safety
            default -> false;
        };
    }
}
