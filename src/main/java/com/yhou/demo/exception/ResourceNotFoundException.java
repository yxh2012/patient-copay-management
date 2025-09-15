package com.yhou.demo.exception;

/**
 * Exception for when requested resources cannot be found by their identifiers.
 */
public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(ErrorCode.RESOURCE_NOT_FOUND,
                String.format("1 or more %s not found with ID(s): %s", resourceType, resourceId));
    }
}
