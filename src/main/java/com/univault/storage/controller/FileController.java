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
     * Soft delete a file (move to trash).
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
}
