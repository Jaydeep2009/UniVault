package com.univault.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadInitResponse {
    private String fileId;
    private int expectedChunks;
    private int chunkSizeBytes;
}