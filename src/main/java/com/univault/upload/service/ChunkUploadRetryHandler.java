package com.univault.upload.service;

import com.univault.providers.StorageProvider;
import com.univault.upload.exception.ChunkUploadFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exponential backoff retry wrapper around StorageProvider.uploadChunk(),
 * now fully non-blocking. The old version used Thread.sleep() for backoff,
 * which parks a thread per chunk mid-retry — fine sequentially, but under
 * real parallel chunk uploads that exhausts the thread pool fast. Backoff
 * here runs via CompletableFuture.delayedExecutor() instead, so a chunk in
 * backoff holds no thread at all.
 *
 * NOTE: switched from Lombok's @RequiredArgsConstructor to an explicit
 * constructor so @Qualifier("chunkUploadExecutor") is honored — Lombok's
 * generated constructors don't copy @Qualifier onto the parameter by
 * default, which would silently break bean resolution if more than one
 * Executor bean is ever added later.
 */
@Component
@Slf4j
public class ChunkUploadRetryHandler {

    private final StorageProvider storageProvider;
    private final Executor uploadExecutor;

    @Value("${univault.upload.max-retries:3}")
    private int maxRetries;

    @Value("${univault.upload.retry-base-delay-ms:200}")
    private long baseDelayMs;

    public ChunkUploadRetryHandler(
            StorageProvider storageProvider,
            @Qualifier("chunkUploadExecutor") Executor uploadExecutor) {
        this.storageProvider = storageProvider;
        this.uploadExecutor = uploadExecutor;
    }

    /**
     * Non-blocking exponential backoff retry. Completes with the
     * RetryResult on success, or exceptionally with
     * ChunkUploadFailedException (carrying the real attempt count) once
     * retries are exhausted.
     */
    public CompletableFuture<RetryResult> uploadWithRetry(byte[] data) {
        return attempt(data, 0, new AtomicInteger(0));
    }

    private CompletableFuture<RetryResult> attempt(byte[] data, int attemptNumber, AtomicInteger attemptsMade) {
        return CompletableFuture
                .supplyAsync(() -> storageProvider.uploadChunk(null, data), uploadExecutor)
                .handle((providerFileId, error) -> {
                    int attemptsSoFar = attemptsMade.incrementAndGet();

                    if (error == null) {
                        return CompletableFuture.completedFuture(new RetryResult(providerFileId, attemptNumber));
                    }

                    // supplyAsync wraps thrown exceptions in a CompletionException;
                    // unwrap so logs/cause show the real provider failure.
                    Throwable cause = (error instanceof CompletionException && error.getCause() != null)
                            ? error.getCause()
                            : error;

                    log.warn("Chunk upload attempt {} failed: {}", attemptsSoFar, cause.getMessage());

                    if (attemptNumber >= maxRetries) {
                        CompletableFuture<RetryResult> failed = new CompletableFuture<>();
                        failed.completeExceptionally(
                                new ChunkUploadFailedException(
                                        "Chunk upload failed after " + attemptsSoFar + " attempts",
                                        cause,
                                        attemptsSoFar));
                        return failed;
                    }

                    long delay = baseDelayMs * (1L << attemptNumber); // baseDelay * 2^attempt
                    Executor delayedExecutor =
                            CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS, uploadExecutor);

                    return CompletableFuture
                            .supplyAsync(() -> null, delayedExecutor)
                            .thenCompose(ignored -> attempt(data, attemptNumber + 1, attemptsMade));
                })
                .thenCompose(future -> future);
    }

    public record RetryResult(String providerFileId, int retriesUsed) {}
}