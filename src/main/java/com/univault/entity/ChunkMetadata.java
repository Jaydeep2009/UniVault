package com.univault.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "chunk_metadata")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata file;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "checksum_md5")
    private String checksumMd5;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storage_provider_account_id", nullable = false)
    private StorageProviderAccount storageProviderAccount;

    @Column(name = "remote_file_id")
    private String remoteFileId; // the providerFileId used in StorageProvider calls

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChunkStatus status;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = ChunkStatus.PENDING;
        }
    }

    public enum ChunkStatus {
        PENDING, UPLOADED, FAILED
    }
}