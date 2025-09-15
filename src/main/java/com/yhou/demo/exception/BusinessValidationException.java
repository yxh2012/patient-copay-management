package com.yhou.demo.exception;

/**
 * Exception for business rule validation failures.
 */
public class BusinessValidationException extends ApiException {
    public BusinessValidationException(String ruleName, String message) {
        super(ErrorCode.BUSINESS_VALIDATION_ERROR,
                String.format("Validation failed for rule '%s': %s", ruleName, message));
    }
}
