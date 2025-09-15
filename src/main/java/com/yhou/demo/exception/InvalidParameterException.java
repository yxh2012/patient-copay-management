package com.yhou.demo.exception;

/**
 * Exception for unsupported or invalid query parameters.
 */
public class InvalidParameterException extends ApiException {
    public InvalidParameterException(String paramName) {
        super(ErrorCode.PARAMETER_INVALID,
                String.format("Unsupported parameter '%s'", paramName));
    }
}
