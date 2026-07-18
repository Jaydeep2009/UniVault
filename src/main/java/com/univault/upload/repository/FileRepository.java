package com.univault.upload.repository;

import com.univault.upload.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FileRepository extends JpaRepository<FileEntity, UUID> {
    // Additional query methods can be defined here if needed
}
