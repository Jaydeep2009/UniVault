package com.univault.download.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadataResponse {
    private UUID fileId;
    private String name;
    private long size;
    private String mimeType;
    private String status;
    private int totalChunks;
    private Instant createdAt;
    private UUID folderId;
}
