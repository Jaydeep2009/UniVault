package com.univault.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class ChunkUploadResponse {
    private String fileId;
    private int serialNumber;

    private String status; // COMPLETE or FAILED
    private String checksum;
}