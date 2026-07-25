package com.univault.download.exception;

import lombok.Getter;

@Getter
public class ChunkRetrievalException extends RuntimeException {
    private final String providerFileId;
    private final int attemptsMade;
    public ChunkRetrievalException(String message, String providerFileId, int attemptsMade, InterruptedException ie) {
        super(message);
        this.providerFileId = providerFileId;
        this.attemptsMade = attemptsMade;
    }

}
