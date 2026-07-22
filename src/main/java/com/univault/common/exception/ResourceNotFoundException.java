package com.univault.common.exception;

/**
 * Thrown when a requested entity doesn't exist, or doesn't belong to the
 * requesting user (folder-not-found and folder-not-yours are deliberately
 * indistinguishable to the client — same 404, no ownership leak).
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}