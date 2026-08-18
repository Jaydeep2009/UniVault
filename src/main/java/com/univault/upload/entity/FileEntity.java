package com.univault.upload.entity;

import com.univault.upload.enums.FileStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Table(name = "files")
public class FileEntity {
    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;
    private UUID folderId;

    private String name;
    private long size;
    private String mimeType;

    private boolean isChunked;
    private int  totalChunks;

    @Enumerated(EnumType.STRING)
    private FileStatus status;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
