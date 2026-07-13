package com.univault.upload.controller;

import com.univault.upload.dto.ChunkUploadResponse;
import com.univault.upload.dto.UploadCompleteResponse;
import com.univault.upload.dto.UploadInitRequest;
import com.univault.upload.dto.UploadInitResponse;
import com.univault.upload.service.UploadSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadSessionService uploadSessionService;

    @PostMapping("/init")
    public ResponseEntity<UploadInitResponse> init(@RequestBody UploadInitRequest request) {
        UploadInitResponse response = uploadSessionService.initUpload(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/chunk", consumes = "multipart/form-data")
    public ResponseEntity<ChunkUploadResponse> uploadChunk(
            @RequestParam("fileId") String fileId,
            @RequestParam("serialNumber") int serialNumber,
            @RequestParam(value = "checksum", required = false) String checksum,
            @RequestParam("file") MultipartFile chunkFile) throws IOException {

        byte[] data = chunkFile.getBytes();
        String actualFileName = chunkFile.getOriginalFilename();

        ChunkUploadResponse response = uploadSessionService.uploadChunk(
                fileId, serialNumber, data, checksum, actualFileName);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/complete")
    public ResponseEntity<UploadCompleteResponse> complete(@RequestParam String fileId) {
        UploadCompleteResponse response = uploadSessionService.completeUpload(fileId);
        return ResponseEntity.ok(response);
    }
}