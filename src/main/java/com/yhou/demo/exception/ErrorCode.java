package com.yhou.demo.exception;

import org.springframework.http.HttpStatus;

/**
 * Enumeration of standardized error codes with corresponding HTTP status mappings.
 */
public enum ErrorCode {
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    INPUT_INVALID(HttpStatus.BAD_REQUEST),
    PARAMETER_INVALID(HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MISSING_HEADER(HttpStatus.BAD_REQUEST),
    BUSINESS_VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus status) {
        this.httpStatus = status;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
