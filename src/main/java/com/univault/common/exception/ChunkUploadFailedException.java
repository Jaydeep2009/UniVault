package com.univault.common.exception;

public class ChunkUploadFailedException extends RuntimeException {

    private final int attemptsMade;

    public ChunkUploadFailedException(String message, Throwable cause, int attemptsMade) {
        super(message, cause);
        this.attemptsMade = attemptsMade;
    }

    public int getAttemptsMade() {
        return attemptsMade;
    }
}