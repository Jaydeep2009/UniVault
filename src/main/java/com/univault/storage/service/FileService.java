package com.univault.storage.service;

import com.univault.common.exception.ResourceNotFoundException;
import com.univault.entity.StorageProviderAccount;
import com.univault.providers.ProviderFactory;
import com.univault.providers.StorageProvider;
import com.univault.repository.StorageProviderAccountRepository;
import com.univault.upload.entity.ChunkEntity;
import com.univault.upload.entity.FileEntity;
import com.univault.upload.enums.FileStatus;
import com.univault.repository.ChunkRepository;
import com.univault.upload.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final FileRepository fileRepository;
    private final ChunkRepository chunkRepository;
    private final StorageProviderAccountRepository accountRepository;
    private final ProviderFactory providerFactory;

    /**
     * List all files for a user.
     */
    public List<FileEntity> listUserFiles(UUID userId) {
        return fileRepository.findByUserId(userId);
    }

    /**
     * List files in a specific folder.
     */
    public List<FileEntity> listFilesInFolder(UUID userId, UUID folderId) {
        return fileRepository.findByUserIdAndFolderId(userId, folderId);
    }

    /**
     * Get a single file by ID with ownership validation.
     */
    public FileEntity getFile(UUID fileId, UUID userId) {
        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));

        if (!file.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("File not found: " + fileId);
        }

        return file;
    }

    /**
     * Delete a file and all its chunks from all providers.
     * This is a complex operation that:
     * 1. Fetches all chunks
     * 2. Groups chunks by provider
     * 3. Deletes chunks from each provider
     * 4. Updates provider account quotas
     * 5. Deletes chunks from DB
     * 6. Marks file as DELETED (soft delete)
     */
    @Transactional
    public void deleteFile(UUID fileId, UUID userId) {
        log.info("Deleting file {} for user {}", fileId, userId);

        // Get file and validate ownership
        FileEntity file = getFile(fileId, userId);

        // Fetch all chunks
        List<ChunkEntity> chunks = chunkRepository.findByFileIdOrderBySerialNumber(fileId);

        if (chunks.isEmpty()) {
            log.warn("File {} has no chunks, marking as deleted", fileId);
            file.setStatus(FileStatus.DELETED);
            fileRepository.save(file);
            return;
        }

        // Group chunks by provider for batch deletion
        Map<UUID, List<ChunkEntity>> chunksByProvider = chunks.stream()
                .collect(Collectors.groupingBy(ChunkEntity::getProviderId));

        log.info("Deleting {} chunks across {} providers", chunks.size(), chunksByProvider.size());

        // Delete chunks from each provider and update quotas
        for (Map.Entry<UUID, List<ChunkEntity>> entry : chunksByProvider.entrySet()) {
            UUID providerId = entry.getKey();
            List<ChunkEntity> providerChunks = entry.getValue();

            try {
                // Get provider account
                StorageProviderAccount account = accountRepository.findById(providerId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Provider account not found: " + providerId));

                // Get provider instance
                StorageProvider provider = providerFactory.getProvider(account);

                long freedBytes = 0;

                // Delete each chunk from provider
                for (ChunkEntity chunk : providerChunks) {
                    try {
                        boolean deleted = provider.deleteChunk(chunk.getProviderFileId());
                        if (deleted) {
                            freedBytes += chunk.getSize();
                            log.debug("Deleted chunk {} from provider {}",
                                    chunk.getSerialNumber(), providerId);
                        } else {
                            log.warn("Failed to delete chunk {} from provider {} (may not exist)",
                                    chunk.getSerialNumber(), providerId);
                        }
                    } catch (Exception e) {
                        log.error("Error deleting chunk {} from provider {}: {}",
                                chunk.getSerialNumber(), providerId, e.getMessage());
                        // Continue with other chunks - don't fail entire delete
                    }
                }

                // Update provider quota
                if (freedBytes > 0) {
                    long currentUsed = account.getUsedQuotaBytes() != null
                            ? account.getUsedQuotaBytes() : 0L;
                    long newUsed = Math.max(0, currentUsed - freedBytes);
                    account.setUsedQuotaBytes(newUsed);
                    accountRepository.save(account);
                    log.info("Freed {} bytes from provider {}", freedBytes, providerId);
                }

            } catch (Exception e) {
                log.error("Error processing provider {} during file deletion: {}",
                        providerId, e.getMessage(), e);
                // Continue with other providers
            }
        }

        // Delete chunks from database
        chunkRepository.deleteAll(chunks);
        log.info("Deleted {} chunk records from database", chunks.size());

        // Mark file as deleted (soft delete)
        file.setStatus(FileStatus.DELETED);
        fileRepository.save(file);

        log.info("File {} successfully deleted", fileId);
    }
}
