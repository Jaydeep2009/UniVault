package com.univault.repository;

import com.univault.upload.entity.ChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChunkRepository extends JpaRepository<ChunkEntity, UUID> {
    // Additional query methods can be defined here if needed
    List<ChunkEntity> findByFileIdOrderBySerialNumber(UUID fileId);
    Optional<ChunkEntity> findByFileIdAndSerialNumber(UUID fileId, int serialNumber);
    
    // Delete all chunks for a file (returns count of deleted records)
    int deleteByFileId(UUID fileId);
}
