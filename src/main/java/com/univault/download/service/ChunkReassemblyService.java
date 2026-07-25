package com.univault.download.service;

import com.univault.download.exception.ChunkRetrievalException;
import com.univault.entity.StorageProviderAccount;
import com.univault.providers.ProviderFactory;
import com.univault.providers.StorageProvider;
import com.univault.repository.ChunkRepository;
import com.univault.repository.StorageProviderAccountRepository;
import com.univault.upload.entity.ChunkEntity;
import com.univault.upload.enums.ChunkStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkReassemblyService {
    private final ChunkRepository chunkRepository;
    private final StorageProviderAccountRepository storageAccountRepository;
    private final ProviderFactory providerFactory;

    @Value("${univault.download.max-retries:3}")
    private int maxRetries;

    @Value("${univault.download.retry-base-delay-ms:200}")
    private long retryBaseDelayMs;

    /*
     * Reassembles a file from its chunks stored across multiple providers.
     *
     * @param fileId The file's UUID
     * @return InputStream of the complete file
     * @throws ChunkRetrievalException if any chunk cannot be retrieved
     * @throws IllegalStateException if chunks are incomplete
     */
    public InputStream reassembleFile(UUID fileId) {
        log.info("Reassembling file with ID: {}", fileId);

        //fetch all chunks in the correct order

        List<ChunkEntity> chunks = chunkRepository.findByFileIdOrderBySerialNumber(fileId);

        if (chunks.isEmpty()) {
            throw new IllegalStateException("No chunks found for file ID: " + fileId);
        }

        //Validate that all chunks are Complete
        boolean allComplete = chunks.stream()
                .allMatch(chunk -> chunk.getStatus() == ChunkStatus.COMPLETE);

        if (!allComplete) {
            List<Integer> incompleteChunks = chunks.stream()
                    .filter(chunk -> chunk.getStatus() != ChunkStatus.COMPLETE)
                    .map(ChunkEntity::getSerialNumber)
                    .toList();

            throw new IllegalStateException("Chunks are incomplete for file ID: " + fileId + ". Incomplete chunks: " + incompleteChunks);
        }

        //download each chunk and collect them into a single InputStream
        List<InputStream> chunkStreams = new ArrayList<>();

        for (ChunkEntity chunk : chunks) {
            log.info("Downloading chunk {} for file ID: {}", chunk.getSerialNumber(), fileId);
            InputStream chunkStream = downloadChunkWithRetry(chunk);
            chunkStreams.add(chunkStream);
        }

        log.info("Successfully reassembled all {} chunks for file ID: {}", chunks.size(), fileId);

        // Combine all chunk InputStreams into a single InputStream
        return new SequenceInputStream(Collections.enumeration(chunkStreams));
    }

    /*
     * Downloads a single chunk with retry logic.
     */
    private InputStream downloadChunkWithRetry(ChunkEntity chunk) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            try {
                //Resolve provider account and get the provider
                StorageProviderAccount account = storageAccountRepository.findById(chunk.getProviderId())
                        .orElseThrow(() -> new IllegalStateException("No provider account found for ID: " + chunk.getProviderId()));

                //get provider instance
                StorageProvider provider = providerFactory.getProvider(account);
                InputStream result = provider.downloadChunk(chunk.getProviderFileId());

                System.out.println("DEBUG: Download successful!");
                return result;
            } catch (Exception e) {
                attempt++;
                lastException = e;

                log.warn("Failed to download chunk {} (attempt {}/{}): {}",
                        chunk.getProviderFileId(), attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    //exponential backoff before retrying
                    long delay = retryBaseDelayMs * (long) Math.pow(2, attempt - 1);
                    try {
                        Thread.sleep(delay);
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
                chunk.getProviderFileId(), maxRetries);

        throw new ChunkRetrievalException(
                "Failed to download chunk after " + maxRetries + " attempts",
                chunk.getProviderFileId(),
                maxRetries,
                (InterruptedException) lastException
        );

    }
}