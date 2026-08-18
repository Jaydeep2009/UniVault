package com.univault.storage.controller;

import com.univault.storage.dto.CreateFolderRequest;
import com.univault.storage.dto.FolderResponse;
import com.univault.storage.dto.RenameFolderRequest;
import com.univault.storage.service.FileService;
import com.univault.storage.service.FolderService;
import com.univault.upload.entity.FileEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;
    private final FileService fileService;
    // TODO: confirm this matches how ProviderConnectionController resolves the current
    // user — assumed here that JwtAuthFilter sets the JWT subject (a UUID string) as
    // the Authentication name. If it's a custom UserPrincipal instead, swap this line.
    private UUID currentUserId(Authentication authentication) {
        return (UUID) authentication.getPrincipal();
    }

    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(
            @Valid @RequestBody CreateFolderRequest request,
            Authentication authentication) {
        FolderResponse response = folderService.createFolder(
                currentUserId(authentication), request.getName(), request.getParentFolderId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<FolderResponse>> listChildren(
            @RequestParam(required = false) UUID parentFolderId,
            Authentication authentication) {
        return ResponseEntity.ok(folderService.listChildren(currentUserId(authentication), parentFolderId));
    }

    @GetMapping("/{folderId}")
    public ResponseEntity<FolderResponse> getFolder(
            @PathVariable UUID folderId,
            Authentication authentication) {
        return ResponseEntity.ok(folderService.getFolder(currentUserId(authentication), folderId));
    }

    @PutMapping("/{folderId}")
    public ResponseEntity<FolderResponse> renameFolder(
            @PathVariable UUID folderId,
            @Valid @RequestBody RenameFolderRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                folderService.renameFolder(currentUserId(authentication), folderId, request.getName()));
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable UUID folderId,
            Authentication authentication) {
        folderService.deleteFolder(currentUserId(authentication), folderId);
        return ResponseEntity.noContent().build();
    }

    /**
     * List all files in a folder.
     * GET /api/folders/{id}/files
     */
    @GetMapping("/{id}/files")
    public ResponseEntity<List<FileEntity>> listFilesInFolder(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();
        List<FileEntity> files = fileService.listFilesInFolder(userId, id);
        return ResponseEntity.ok(files);
    }

}