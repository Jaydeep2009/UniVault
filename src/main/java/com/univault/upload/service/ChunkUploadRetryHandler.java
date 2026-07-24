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
 * Non-blocking exponential backoff retry, same as the earlier rewrite, but
 * now takes StorageProvider as a per-call parameter instead of a single
 * injected bean — matching the shift to per-account provider resolution
 * (ProviderFactory.getProvider(account) happens in UploadSessionService,
 * one call per chunk, since different chunks can land on different
 * connected accounts).
 *
 * Still no Thread.sleep() anywhere — backoff runs via
 * CompletableFuture.delayedExecutor(), so a chunk sitting in backoff holds
 * no thread.
 */
@Component
@Slf4j
public class ChunkUploadRetryHandler {

    private final Executor uploadExecutor;

    @Value("${univault.upload.max-retries:3}")
    private int maxRetries;

    @Value("${univault.upload.retry-base-delay-ms:200}")
    private long baseDelayMs;

    public ChunkUploadRetryHandler(@Qualifier("chunkUploadExecutor") Executor uploadExecutor) {
        this.uploadExecutor = uploadExecutor;
    }

    /**
     * Returns a future that completes with the provider's returned chunk id
     * on success, or completes exceptionally with ChunkUploadFailedException
     * (carrying the real attempt count) once retries are exhausted.
     */
    public CompletableFuture<RetryResult> uploadWithRetry(
            StorageProvider storageProvider, String providerFileId, byte[] data) {
        return attempt(storageProvider, providerFileId, data, 0, new AtomicInteger(0));
    }

    private CompletableFuture<RetryResult> attempt(
            StorageProvider storageProvider, String providerFileId, byte[] data,
            int attemptNumber, AtomicInteger attemptsMade) {

        return CompletableFuture
                .supplyAsync(() -> storageProvider.uploadChunk(providerFileId, data), uploadExecutor)
                .handle((returnedId, error) -> {
                    int attemptsSoFar = attemptsMade.incrementAndGet();

                    if (error == null) {
                        return CompletableFuture.completedFuture(new RetryResult(returnedId, attemptNumber));
                    }

                    Throwable cause = unwrap(error);
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
                            .thenCompose(ignored ->
                                    attempt(storageProvider, providerFileId, data, attemptNumber + 1, attemptsMade));
                })
                .thenCompose(future -> future);
    }

    // supplyAsync/thenCompose wrap thrown exceptions in CompletionException,
    // possibly nested through the recursive retry chain — unwrap to the real cause.
    private static Throwable unwrap(Throwable t) {
        while (t instanceof CompletionException && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    public record RetryResult(String providerFileId, int retriesUsed) {}
}