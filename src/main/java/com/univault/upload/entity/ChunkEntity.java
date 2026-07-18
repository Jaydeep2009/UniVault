package com.univault.upload.entity;

import com.univault.upload.enums.ChunkStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "chunks")
@Getter @Setter
public class ChunkEntity {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID fileId;
    private int serialNumber;
    private long size;
    private String checksum;

    private UUID providerId;       // nullable for now — Member 1's providers table isn't wired yet
    private String providerFileId; // the id MockStorageProvider/GoogleDriveProvider returns

    @Enumerated(EnumType.STRING)
    private ChunkStatus status;

    private int retryCount;
}