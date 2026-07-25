package com.univault.download.controller;

import com.univault.download.dto.FileMetadataResponse;
import com.univault.download.service.DownloadService;
import com.univault.upload.entity.FileEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class DownloadController {

    private final DownloadService downloadService;

    /**
     * Download a file by ID.
     * Returns the file as a binary stream with proper headers.
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<InputStreamResource> downloadFile(
            @PathVariable String fileId,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();
        log.info("Download request for file {} from user {}", fileId, userId);

        // Get file metadata
        FileEntity file = downloadService.getFile(fileId, userId);

        // Get file stream
        InputStream fileStream = downloadService.downloadFile(fileId, userId);

        // Determine content type
        String contentType = file.getMimeType() != null && !file.getMimeType().isBlank()
                ? file.getMimeType()
                : "application/octet-stream";

        // Build response with proper headers
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getName() + "\"")
                .contentLength(file.getSize())
                .body(new InputStreamResource(fileStream));
    }

    /**
     * Get file metadata without downloading.
     * Useful for previewing file info before download.
     */
    @GetMapping("/{fileId}/metadata")
    public ResponseEntity<FileMetadataResponse> getFileMetadata(
            @PathVariable String fileId,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();
        log.info("Metadata request for file {} from user {}", fileId, userId);

        FileMetadataResponse metadata = downloadService.getFileMetadata(fileId, userId);
        return ResponseEntity.ok(metadata);
    }
}
