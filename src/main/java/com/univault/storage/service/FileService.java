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
     * List all files for a user (excluding deleted files).
     */
    public List<FileEntity> listUserFiles(UUID userId) {
        return fileRepository.findByUserId(userId).stream()
                .filter(file -> file.getStatus() != FileStatus.DELETED)
                .collect(Collectors.toList());
    }

    /**
     * List all deleted files (trash) for a user.
     */
    public List<FileEntity> listDeletedFiles(UUID userId) {
        return fileRepository.findByUserId(userId).stream()
                .filter(file -> file.getStatus() == FileStatus.DELETED)
                .collect(Collectors.toList());
    }

    /**
     * Restore a deleted file from trash.
     */
    @Transactional
    public FileEntity restoreFile(UUID fileId, UUID userId) {
        log.info("Restoring file {} for user {}", fileId, userId);

        FileEntity file = getFile(fileId, userId);

        if (file.getStatus() != FileStatus.DELETED) {
            throw new IllegalStateException("File is not deleted: " + fileId);
        }

        file.setStatus(FileStatus.READY);
        return fileRepository.save(file);
    }

    /**
     * Permanently delete a file (hard delete - removes chunks and database record).
     */
    @Transactional
    public void permanentlyDeleteFile(UUID fileId, UUID userId) {
        log.info("Permanently deleting file {} for user {}", fileId, userId);

        FileEntity file = getFile(fileId, userId);

        if (file.getStatus() != FileStatus.DELETED) {
            throw new IllegalStateException("File must be in trash before permanent deletion: " + fileId);
        }

        // Delete chunks from cloud storage
        deleteFileAndChunks(fileId, userId);

        // Hard delete the file record
        fileRepository.delete(file);

        log.info("File {} permanently deleted", fileId);
    }

    /**
     * List files in a specific folder (excluding deleted files).
     */
    public List<FileEntity> listFilesInFolder(UUID userId, UUID folderId) {
        return fileRepository.findByUserIdAndFolderId(userId, folderId).stream()
                .filter(file -> file.getStatus() != FileStatus.DELETED)
                .collect(Collectors.toList());
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
     * Soft delete a file (instant - just marks as DELETED).
     * Chunks remain in cloud storage and can be restored.
     */
    @Transactional
    public void softDeleteFile(UUID fileId, UUID userId) {
        log.info("Soft deleting file {} for user {}", fileId, userId);

        FileEntity file = getFile(fileId, userId);
        
        if (file.getStatus() == FileStatus.DELETED) {
            log.warn("File {} is already deleted", fileId);
            return;
        }

        // Simply mark as deleted - chunks stay intact
        file.setStatus(FileStatus.DELETED);
        fileRepository.save(file);

        log.info("File {} soft deleted (chunks preserved for restore)", fileId);
    }

    /**
     * Delete a file and all its chunks from all providers.
     * This is now only used for PERMANENT deletion from trash.
     * Renamed from deleteFile to clarify it's the old hard-delete logic.
     */
    @Transactional
    public void deleteFileAndChunks(UUID fileId, UUID userId) {
        log.info("Hard deleting file {} and chunks for user {}", fileId, userId);

        // Get file and validate ownership
        FileEntity file = getFile(fileId, userId);

        // CRITICAL: Mark file as DELETED first to prevent race condition
        // This prevents new chunks from being uploaded while we're deleting
        if (file.getStatus() != FileStatus.DELETED) {
            file.setStatus(FileStatus.DELETED);
            fileRepository.save(file);
            log.info("Marked file {} as DELETED to prevent new chunk uploads", fileId);
        }

        // Small delay to allow any in-flight chunk uploads to complete
        // This helps prevent orphaned chunks from uploads that were in-progress
        try {
            Thread.sleep(500); // 500ms should be enough for in-flight requests
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for in-flight uploads");
        }

        // Fetch all chunks - get fresh data from database after the delay
        List<ChunkEntity> chunks = chunkRepository.findByFileIdOrderBySerialNumber(fileId);

        if (chunks.isEmpty()) {
            log.warn("File {} has no chunks to delete", fileId);
            return;
        }

        log.info("Found {} chunks to delete for file {}", chunks.size(), fileId);

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

        // Delete chunks from database using repository method to avoid stale state
        try {
            int deletedCount = chunkRepository.deleteByFileId(fileId);
            log.info("Deleted {} chunk records from database for file {}", deletedCount, fileId);
            
            // Verify no orphaned chunks remain (race condition check)
            List<ChunkEntity> remainingChunks = chunkRepository.findByFileIdOrderBySerialNumber(fileId);
            if (!remainingChunks.isEmpty()) {
                log.warn("Found {} orphaned chunks after deletion, cleaning up...", remainingChunks.size());
                for (ChunkEntity orphanedChunk : remainingChunks) {
                    try {
                        // Try to delete from provider if we have the provider info
                        if (orphanedChunk.getProviderId() != null && orphanedChunk.getProviderFileId() != null) {
                            try {
                                StorageProviderAccount account = accountRepository.findById(orphanedChunk.getProviderId())
                                        .orElse(null);
                                if (account != null) {
                                    StorageProvider provider = providerFactory.getProvider(account);
                                    boolean deleted = provider.deleteChunk(orphanedChunk.getProviderFileId());
                                    if (deleted) {
                                        log.info("Deleted orphaned chunk {} from provider", orphanedChunk.getSerialNumber());
                                        // Update quota
                                        long currentUsed = account.getUsedQuotaBytes() != null ? account.getUsedQuotaBytes() : 0L;
                                        account.setUsedQuotaBytes(Math.max(0, currentUsed - orphanedChunk.getSize()));
                                        accountRepository.save(account);
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Failed to delete orphaned chunk from provider: {}", e.getMessage());
                            }
                        }
                        chunkRepository.delete(orphanedChunk);
                        log.info("Deleted orphaned chunk {} from database", orphanedChunk.getId());
                    } catch (Exception ex) {
                        log.warn("Failed to cleanup orphaned chunk {}: {}", orphanedChunk.getId(), ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error deleting chunks from database: {}", e.getMessage(), e);
            // Try alternative: delete one by one
            log.info("Attempting individual chunk deletion...");
            // Refresh chunk list
            List<ChunkEntity> remainingChunks = chunkRepository.findByFileIdOrderBySerialNumber(fileId);
            for (ChunkEntity chunk : remainingChunks) {
                try {
                    chunkRepository.delete(chunk);
                    log.debug("Deleted chunk {} individually", chunk.getId());
                } catch (Exception ex) {
                    log.warn("Failed to delete chunk {}: {}", chunk.getId(), ex.getMessage());
                }
            }
        }

        log.info("File {} chunks successfully deleted", fileId);
    }

    /**
     * Hard delete the file record from database.
     * Used after deleteFileAndChunks for cancelled/incomplete uploads.
     * Handles concurrent delete requests gracefully.
     */
    @Transactional
    public void hardDeleteFileRecord(UUID fileId, UUID userId) {
        log.info("Hard deleting file record {} for user {}", fileId, userId);
        
        // Check if file exists first to handle concurrent deletes
        if (!fileRepository.existsById(fileId)) {
            log.warn("File {} already deleted (concurrent request), skipping", fileId);
            return;
        }
        
        FileEntity file = getFile(fileId, userId);
        fileRepository.delete(file);
        
        log.info("File record {} deleted from database", fileId);
    }
}
