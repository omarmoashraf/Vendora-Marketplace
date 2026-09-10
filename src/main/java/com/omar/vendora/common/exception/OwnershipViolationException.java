package com.omar.vendora.common.exception;

/**
 * Exception thrown when an authenticated user attempts to access or mutate
 * a resource that belongs to another owner without sufficient administrative authority.
 */
public class OwnershipViolationException extends RuntimeException {

    public OwnershipViolationException(String message) {
        super(message);
    }

    public OwnershipViolationException() {
        super("Access denied: you do not have permission to access or modify this resource");
    }
}
