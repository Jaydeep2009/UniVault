package com.univault.upload.exception;

public class InsufficientStorageException extends RuntimeException {
    public InsufficientStorageException(String message) {
        super(message);
    }
}
