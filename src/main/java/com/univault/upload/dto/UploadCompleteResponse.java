package com.univault.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UploadCompleteResponse {
    private String fileId;
    private String status;       // READY or INCOMPLETE
    private List<Integer> missingChunks; // empty if READY
}