package com.univault.storage.service;

import com.univault.common.exception.ResourceNotFoundException;
import com.univault.entity.StorageProviderAccount;
import com.univault.providers.ProviderFactory;
import com.univault.providers.StorageProvider;
import com.univault.repository.ChunkRepository;
import com.univault.repository.StorageProviderAccountRepository;
import com.univault.upload.entity.ChunkEntity;
import com.univault.upload.entity.FileEntity;
import com.univault.upload.enums.FileStatus;
import com.univault.upload.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
     * Soft delete - moves file to trash.
     * File is marked as DELETED but chunks remain on providers.
     * Space is NOT freed (file still counts against quota).
     * Can be restored within 30 days.
     */
    @Transactional
    public void softDeleteFile(UUID fileId, UUID userId) {
        log.info("Soft deleting file {} for user {}", fileId, userId);

        // Get file and validate ownership
        FileEntity file = getFile(fileId, userId);

        // Check if already deleted
        if (file.getStatus() == FileStatus.DELETED) {
            throw new IllegalStateException("File is already deleted");
        }

        // Mark as deleted with timestamp
        file.setStatus(FileStatus.DELETED);
        file.setDeletedAt(Instant.now());
        fileRepository.save(file);

        log.info("File {} moved to trash", fileId);
    }

    /**
     * Permanent delete - removes file and all chunks from providers.
     * Frees storage space and updates provider quotas.
     * Cannot be undone.
     */
    @Transactional
    public void permanentDeleteFile(UUID fileId, UUID userId) {
        log.info("Permanently deleting file {} for user {}", fileId, userId);

        // Get file and validate ownership
        FileEntity file = getFile(fileId, userId);

        // Fetch all chunks
        List<ChunkEntity> chunks = chunkRepository.findByFileIdOrderBySerialNumber(fileId);

        if (chunks.isEmpty()) {
            log.warn("File {} has no chunks, deleting metadata only", fileId);
            fileRepository.delete(file);
            return;
        }

        // Group chunks by provider for batch deletion
        Map<UUID, List<ChunkEntity>> chunksByProvider = chunks.stream()
                .collect(Collectors.groupingBy(ChunkEntity::getProviderId));

        log.info("Deleting {} chunks across {} providers", chunks.size(), chunksByProvider.size());

        // Delete chunks from each provider and FREE SPACE
        for (Map.Entry<UUID, List<ChunkEntity>> entry : chunksByProvider.entrySet()) {
            UUID providerId = entry.getKey();
            List<ChunkEntity> providerChunks = entry.getValue();

            try {
                StorageProviderAccount account = accountRepository.findById(providerId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Provider account not found: " + providerId));

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
                            log.warn("Failed to delete chunk {} from provider {}",
                                    chunk.getSerialNumber(), providerId);
                        }
                    } catch (Exception e) {
                        log.error("Error deleting chunk {}: {}",
                                chunk.getSerialNumber(), e.getMessage());
                    }
                }

                // Update provider quota - FREE THE SPACE
                if (freedBytes > 0) {
                    long currentUsed = account.getUsedQuotaBytes() != null
                            ? account.getUsedQuotaBytes() : 0L;
                    long newUsed = Math.max(0, currentUsed - freedBytes);
                    account.setUsedQuotaBytes(newUsed);
                    accountRepository.save(account);
                    log.info("Freed {} bytes from provider {}", freedBytes, providerId);
                }

            } catch (Exception e) {
                log.error("Error processing provider {}: {}", providerId, e.getMessage(), e);
            }
        }

        // Delete chunks from database
        chunkRepository.deleteAll(chunks);
        log.info("Deleted {} chunk records from database", chunks.size());

        // Delete file metadata (hard delete)
        fileRepository.delete(file);

        log.info("File {} permanently deleted", fileId);
    }

    /**
     * Restore a file from trash.
     */
    @Transactional
    public void restoreFile(UUID fileId, UUID userId) {
        log.info("Restoring file {} for user {}", fileId, userId);

        FileEntity file = getFile(fileId, userId);

        if (file.getStatus() != FileStatus.DELETED) {
            throw new IllegalStateException("File is not in trash");
        }

        // Restore to READY status
        file.setStatus(FileStatus.READY);
        file.setDeletedAt(null);
        fileRepository.save(file);

        log.info("File {} restored from trash", fileId);
    }

    /**
     * List files in trash (soft deleted files).
     */
    public List<FileEntity> listTrashFiles(UUID userId) {
        return fileRepository.findByUserIdAndStatus(userId, FileStatus.DELETED);
    }

}
