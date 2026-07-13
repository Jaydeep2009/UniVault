package com.univault.upload.service;

import com.univault.common.exception.ChunkUploadFailedException;
import com.univault.providers.StorageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChunkUploadRetryHandler {

    private final StorageProvider storageProvider;

    @Value("${univault.upload.max-retries:3}")
    private int maxRetries;

    @Value("${univault.upload.retry-base-delay-ms:200}")
    private long baseDelayMs;

    /**
     * Attempts to upload a chunk with exponential backoff retry.
     * Returns the providerFileId and how many attempts it took on success.
     * Throws ChunkUploadFailedException (carrying attempt count) if all attempts fail.
     */
    public RetryResult uploadWithRetry(byte[] data) {
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String providerFileId = storageProvider.uploadChunk(null, data);
                return new RetryResult(providerFileId, attempt);
            } catch (Exception e) {
                lastException = e;
                log.warn("Chunk upload attempt {} failed: {}", attempt + 1, e.getMessage());

                if (attempt < maxRetries) {
                    long delay = baseDelayMs * (1L << attempt); // baseDelay * 2^attempt
                    sleep(delay);
                }
            }
        }

        throw new ChunkUploadFailedException(
                "Chunk upload failed after " + (maxRetries + 1) + " attempts",
                lastException,
                maxRetries + 1);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record RetryResult(String providerFileId, int retriesUsed) {}
}