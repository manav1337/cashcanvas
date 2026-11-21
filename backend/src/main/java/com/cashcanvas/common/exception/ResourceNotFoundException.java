package com.cashcanvas.common.exception;

/**
 * Thrown both when a resource does not exist AND when it belongs to another user.
 * Returning 404 (not 403) in the second case avoids leaking which IDs exist.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resourceName + " " + id + " was not found");
    }
}
