package com.univault.storage.controller;

import com.univault.storage.service.FileService;
import com.univault.upload.entity.FileEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileService fileService;

    /**
     * List all files for the authenticated user.
     * GET /api/files
     */
    @GetMapping
    public ResponseEntity<List<FileEntity>> listFiles(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        log.info("Listing files for user {}", userId);

        List<FileEntity> files = fileService.listUserFiles(userId);
        return ResponseEntity.ok(files);
    }

    /**
     * List all deleted files (trash) for the authenticated user.
     * GET /api/files/trash
     */
    @GetMapping("/trash")
    public ResponseEntity<List<FileEntity>> listTrash(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        log.info("Listing trash files for user {}", userId);

        List<FileEntity> deletedFiles = fileService.listDeletedFiles(userId);
        return ResponseEntity.ok(deletedFiles);
    }

    /**
     * Restore a deleted file from trash.
     * POST /api/files/{id}/restore
     */
    @PostMapping("/{id}/restore")
    public ResponseEntity<FileEntity> restoreFile(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();
        log.info("Restoring file {} for user {}", id, userId);

        FileEntity file = fileService.restoreFile(id, userId);
        return ResponseEntity.ok(file);
    }

    /**
     * Permanently delete a file from trash.
     * DELETE /api/files/{id}/permanent
     */
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> permanentlyDeleteFile(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();
        log.info("Permanently deleting file {} for user {}", id, userId);

        fileService.permanentlyDeleteFile(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get a single file by ID.
     * GET /api/files/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<FileEntity> getFile(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();
        log.info("Getting file {} for user {}", id, userId);

        FileEntity file = fileService.getFile(id, userId);
        return ResponseEntity.ok(file);
    }

    /**
     * Delete a file (soft delete - marks as DELETED immediately).
     * Chunks remain in cloud storage and can be restored.
     * Use permanent delete to actually remove chunks.
     * 
     * DELETE /api/files/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();
        log.info("Soft deleting file {} for user {}", id, userId);

        fileService.softDeleteFile(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Hard delete a file and all its chunks immediately.
     * This is used for cancelled/incomplete uploads.
     * Does NOT require file to be in trash first.
     * 
     * DELETE /api/files/{id}/hard
     */
    @DeleteMapping("/{id}/hard")
    public ResponseEntity<Void> hardDeleteFile(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();
        log.info("Hard deleting file {} for user {} (cancelled upload)", id, userId);

        // Delete chunks and file record immediately
        fileService.deleteFileAndChunks(id, userId);
        
        // Delete the file entity from database
        fileService.hardDeleteFileRecord(id, userId);
        
        return ResponseEntity.noContent().build();
    }
}
