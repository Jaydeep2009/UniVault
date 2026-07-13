package com.univault.upload.dto;

import lombok.Data;

@Data
public class UploadInitRequest {
    private String fileName;
    private long fileSize;
    private String folderId; // nullable — root folder if absent
}