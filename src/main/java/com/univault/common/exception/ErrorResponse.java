package com.univault.common.exception;

import java.time.Instant;

/**
 * Uniform error body returned by GlobalExceptionHandler for every mapped
 * exception, so clients get a consistent shape instead of ad-hoc 500s.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path);
    }
}