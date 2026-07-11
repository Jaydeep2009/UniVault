package com.univault.upload.repository;

import com.univault.upload.entity.ChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChunkRepository extends JpaRepository<ChunkEntity, UUID> {
    // Additional query methods can be defined here if needed
    List<ChunkEntity> findByFileIdOrderBySerialNumber(UUID fileId);
}
