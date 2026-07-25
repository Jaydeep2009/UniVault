package com.univault.upload.repository;

import com.univault.upload.entity.FileEntity;
import com.univault.upload.enums.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FileRepository extends JpaRepository<FileEntity, UUID> {
    List<FileEntity> findByUserId(UUID userId);

    List<FileEntity> findByUserIdAndFolderId(UUID userId, UUID folderId);

    List<FileEntity> findByUserIdAndStatus(UUID userId, FileStatus status);

    List<FileEntity> findByStatusAndDeletedAtBefore(FileStatus status, Instant threshold);


}
