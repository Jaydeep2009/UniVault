package com.univault.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChunkUploadRequest {
    String fileId;
    int serialNumber;
    byte[] data;
    String ClientChecksum;
}
