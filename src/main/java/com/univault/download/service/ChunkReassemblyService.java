package com.univault.download.service;

import com.univault.download.exception.ChunkRetrievalException;
import com.univault.entity.StorageProviderAccount;
import com.univault.providers.ProviderFactory;
import com.univault.providers.StorageProvider;
import com.univault.repository.StorageProviderAccountRepository;
import com.univault.upload.entity.ChunkEntity;
import com.univault.upload.enums.ChunkStatus;
import com.univault.repository.ChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkReassemblyService {

    private final ChunkRepository chunkRepository;
    private final StorageProviderAccountRepository accountRepository;
    private final ProviderFactory providerFactory;

    @Value("${univault.download.max-retries:3}")
    private int maxRetries;

    @Value("${univault.download.retry-base-delay-ms:200}")
    private int retryBaseDelayMs;

    @Value("${univault.download.parallelism:4}")
    private int downloadParallelism;

    private ExecutorService downloadExecutor;

    private ExecutorService getExecutor() {
        if (downloadExecutor == null) {
            downloadExecutor = Executors.newFixedThreadPool(downloadParallelism,
                    r -> {
                        Thread t = new Thread(r);
                        t.setName("chunk-download-" + t.getId());
                        t.setDaemon(true);
                        return t;
                    });
        }
        return downloadExecutor;
    }

    /**
     * Reassembles a file from its chunks stored across multiple providers.
     * Uses parallel downloads for better performance.
     *
     * @param fileId The file's UUID
     * @return InputStream of the complete file
     * @throws ChunkRetrievalException if any chunk cannot be retrieved
     * @throws IllegalStateException if chunks are incomplete
     */
    public InputStream reassembleFile(UUID fileId) {
        log.info("Starting reassembly for file: {}", fileId);

        // Fetch all chunks in correct order
        List<ChunkEntity> chunks = chunkRepository.findByFileIdOrderBySerialNumber(fileId);

        if (chunks.isEmpty()) {
            throw new IllegalStateException("No chunks found for file: " + fileId);
        }

        // Validate all chunks are COMPLETE
        boolean allComplete = chunks.stream()
                .allMatch(c -> c.getStatus() == ChunkStatus.COMPLETE);

        if (!allComplete) {
            List<Integer> incompleteChunks = chunks.stream()
                    .filter(c -> c.getStatus() != ChunkStatus.COMPLETE)
                    .map(ChunkEntity::getSerialNumber)
                    .toList();
            throw new IllegalStateException(
                    "File has incomplete chunks: " + incompleteChunks + " for file: " + fileId);
        }

        // Download all chunks in parallel
        log.info("Starting parallel download of {} chunks", chunks.size());
        long startTime = System.currentTimeMillis();

        // Create download futures for all chunks
        List<CompletableFuture<ChunkData>> downloadFutures = new ArrayList<>();

        for (ChunkEntity chunk : chunks) {
            CompletableFuture<ChunkData> future = CompletableFuture.supplyAsync(
                    () -> downloadChunkToMemory(chunk),
                    getExecutor()
            );
            downloadFutures.add(future);
        }

        // Wait for all downloads to complete
        CompletableFuture<Void> allDone = CompletableFuture.allOf(
                downloadFutures.toArray(new CompletableFuture[0])
        );

        try {
            allDone.join(); // Wait for all chunks
        } catch (Exception e) {
            log.error("Parallel download failed", e);
            throw new ChunkRetrievalException(
                    "Failed to download chunks in parallel",
                    "multiple-chunks",
                    maxRetries,
                    (InterruptedException) e
            );
        }

        long downloadTime = System.currentTimeMillis() - startTime;
        log.info("Parallel download completed in {}ms for {} chunks", downloadTime, chunks.size());

        // Collect results in order
        List<ChunkData> chunkDataList = new ArrayList<>();
        for (CompletableFuture<ChunkData> future : downloadFutures) {
            chunkDataList.add(future.join());
        }

        // Sort by serial number to ensure correct order
        chunkDataList.sort(Comparator.comparingInt(ChunkData::serialNumber));

        // Convert to InputStreams
        List<InputStream> streams = chunkDataList.stream()
                .map(cd -> new ByteArrayInputStream(cd.data()))
                .map(s -> (InputStream) s)
                .toList();

        log.info("Successfully retrieved all {} chunks for file: {}", chunks.size(), fileId);

        // Combine all streams into one
        return new SequenceInputStream(Collections.enumeration(streams));
    }

    /**
     * Downloads a single chunk to memory with retry logic.
     */
    private ChunkData downloadChunkToMemory(ChunkEntity chunk) {
        log.debug("Downloading chunk {} (thread: {})",
                chunk.getSerialNumber(), Thread.currentThread().getName());

        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            try {
                // Resolve provider account
                StorageProviderAccount account = accountRepository
                        .findById(chunk.getProviderId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Provider account not found: " + chunk.getProviderId()));

                // Get provider instance
                StorageProvider provider = providerFactory.getProvider(account);

                // Download chunk
                InputStream stream = provider.downloadChunk(chunk.getProviderFileId());
                byte[] data = stream.readAllBytes();

                log.debug("Chunk {} downloaded successfully ({} bytes)",
                        chunk.getSerialNumber(), data.length);

                return new ChunkData(chunk.getSerialNumber(), data);

            } catch (Exception e) {
                attempt++;
                lastException = e;

                log.warn("Failed to download chunk {} (attempt {}/{}): {}",
                        chunk.getSerialNumber(), attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    // Exponential backoff
                    long delayMs = retryBaseDelayMs * (long) Math.pow(2, attempt - 1);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ChunkRetrievalException(
                                "Download interrupted",
                                chunk.getProviderFileId(),
                                attempt,
                                ie
                        );
                    }
                }
            }
        }

        // All retries exhausted
        log.error("Failed to download chunk {} after {} attempts",
                chunk.getSerialNumber(), maxRetries);

        throw new ChunkRetrievalException(
                "Failed to download chunk after " + maxRetries + " attempts",
                chunk.getProviderFileId(),
                maxRetries,
                (InterruptedException) lastException
        );
    }

    /**
     * Record to hold chunk data with serial number for sorting.
     */
    private record ChunkData(int serialNumber, byte[] data) {}
}
