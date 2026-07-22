package com.univault.repository;

import com.univault.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {
    List<Folder> findByUserIdAndParentFolderIsNull(UUID userId);
    List<Folder> findByUserIdAndParentFolderId(UUID userId, UUID parentFolderId);
    Optional<Folder> findByIdAndUserId(UUID id, UUID userId);
}