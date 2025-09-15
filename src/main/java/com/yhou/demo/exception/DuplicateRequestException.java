package com.yhou.demo.exception;

/**
 * Exception for duplicate request detection based on idempotency keys.
 */
public class DuplicateRequestException extends ApiException {
    public DuplicateRequestException(String requestKey) {
        super(ErrorCode.DUPLICATE_REQUEST,
                String.format("Duplicate request detected for key: %s", requestKey));
    }
}
