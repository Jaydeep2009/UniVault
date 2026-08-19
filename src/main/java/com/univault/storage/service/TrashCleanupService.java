package com.univault.storage.service;

import com.univault.upload.entity.FileEntity;
import com.univault.upload.enums.FileStatus;
import com.univault.upload.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service for automatically cleaning up old deleted files from trash.
 * Files in trash (DELETED status) older than 30 days are permanently deleted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrashCleanupService {

    private final FileRepository fileRepository;
    private final FileService fileService;

    private static final int TRASH_RETENTION_DAYS = 30;

    /**
     * Scheduled task that runs daily at 2 AM to clean up old trash files.
     * Cron expression: "0 0 2 * * *" = Every day at 2:00 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTrashFiles() {
        log.info("Starting scheduled trash cleanup job...");

        try {
            // Calculate the cutoff date (30 days ago)
            Instant cutoffDate = Instant.now().minus(TRASH_RETENTION_DAYS, ChronoUnit.DAYS);
            log.info("Deleting files deleted before: {}", cutoffDate);

            // Find all deleted files older than 30 days
            List<FileEntity> expiredFiles = fileRepository.findAll().stream()
                    .filter(file -> file.getStatus() == FileStatus.DELETED)
                    .filter(file -> file.getUpdatedAt() != null && file.getUpdatedAt().isBefore(cutoffDate))
                    .toList();

            if (expiredFiles.isEmpty()) {
                log.info("No expired trash files found");
                return;
            }

            log.info("Found {} expired files to permanently delete", expiredFiles.size());

            int successCount = 0;
            int failCount = 0;

            // Permanently delete each expired file
            for (FileEntity file : expiredFiles) {
                try {
                    log.info("Permanently deleting expired file: {} (user: {}, deleted: {})",
                            file.getId(), file.getUserId(), file.getUpdatedAt());

                    fileService.permanentlyDeleteFile(file.getId(), file.getUserId());
                    successCount++;

                    log.info("Successfully deleted file: {}", file.getId());
                } catch (Exception e) {
                    failCount++;
                    log.error("Failed to delete expired file {}: {}",
                            file.getId(), e.getMessage(), e);
                    // Continue with other files even if one fails
                }
            }

            log.info("Trash cleanup completed: {} files deleted, {} failed",
                    successCount, failCount);

        } catch (Exception e) {
            log.error("Error during trash cleanup job: {}", e.getMessage(), e);
        }
    }

    /**
     * Manual cleanup method for testing or on-demand cleanup.
     * Can be called from a REST endpoint if needed.
     */
    @Transactional
    public void manualCleanup() {
        log.info("Manual trash cleanup triggered");
        cleanupExpiredTrashFiles();
    }
}
