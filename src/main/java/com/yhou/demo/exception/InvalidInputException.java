package com.yhou.demo.exception;

/**
 * Exception for invalid field values in request data.
 */
public class InvalidInputException extends ApiException {
    public InvalidInputException(String fieldName, Object invalidValue) {
        super(ErrorCode.INPUT_INVALID,
                String.format("Invalid value '%s' for field '%s'", invalidValue, fieldName));
    }
}
