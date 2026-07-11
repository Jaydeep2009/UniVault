package com.univault.upload.dto;

import lombok.Data;

@Data
public class ChunkUploadRequest {
    String fileId;
    int serialNumber;
    byte[] data;
    String ClientChecksum;
}
