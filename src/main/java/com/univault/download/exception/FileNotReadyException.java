package com.univault.download.exception;

import lombok.Getter;

@Getter
public class FileNotReadyException extends RuntimeException {
    private final String fileId;
    private final String currentStatus;
    public FileNotReadyException(String message, String fileId, String currentStatus) {
        super(message);
        this.fileId = fileId;
        this.currentStatus = currentStatus;
    }
}
