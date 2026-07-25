package com.univault.storage.service;

import com.univault.common.exception.ResourceNotFoundException;
import com.univault.entity.Folder;
import com.univault.entity.User;
import com.univault.repository.FolderRepository;
import com.univault.storage.dto.FolderResponse;
import com.univault.upload.entity.FileEntity;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileService fileService;


    public FolderResponse createFolder(UUID userId, String name, UUID parentFolderId) {
        Folder folder = new Folder();
        User user = new User();
        user.setId(userId); // reference-only, avoids a round-trip fetch just to set the FK
        folder.setUser(user);
        folder.setName(name);

        if (parentFolderId != null) {
            Folder parent = folderRepository.findByIdAndUserId(parentFolderId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent folder not found: " + parentFolderId));
            folder.setParentFolder(parent);
        }

        Folder saved = folderRepository.save(folder);
        return toResponse(saved);
    }

    public List<FolderResponse> listChildren(UUID userId, UUID parentFolderId) {
        List<Folder> folders = (parentFolderId == null)
                ? folderRepository.findByUserIdAndParentFolderIsNull(userId)
                : folderRepository.findByUserIdAndParentFolderId(userId, parentFolderId);

        return folders.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public FolderResponse getFolder(UUID userId, UUID folderId) {
        Folder folder = folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));
        return toResponse(folder);
    }

    public FolderResponse renameFolder(UUID userId, UUID folderId, String newName) {
        Folder folder = folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));
        folder.setName(newName);
        return toResponse(folderRepository.save(folder));
    }

    @Transactional
    public void deleteFolder(UUID userId, UUID folderId) {
        Folder folder = folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));

        // Check for subfolders
        boolean hasSubfolders = !folderRepository.findByUserIdAndParentFolderId(userId, folderId).isEmpty();
        if (hasSubfolders) {
            throw new IllegalStateException("Cannot delete folder " + folderId + " — it still contains subfolders");
        }

        // Check for files in folder
        List<FileEntity> filesInFolder = fileService.listFilesInFolder(userId, folderId);
        if (!filesInFolder.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot delete folder " + folderId + " — it contains " + filesInFolder.size() + " file(s). " +
                            "Delete the files first.");
        }

        // Safe to delete
        folderRepository.delete(folder);
    }


    private FolderResponse toResponse(Folder folder) {
        return new FolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getParentFolder() != null ? folder.getParentFolder().getId() : null,
                folder.getCreatedAt()
        );
    }
}