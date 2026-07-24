package com.univault.upload.controller;

import com.univault.common.util.ChecksumUtil;
import com.univault.upload.dto.ChunkUploadResponse;
import com.univault.upload.dto.UploadCompleteResponse;
import com.univault.upload.dto.UploadInitRequest;
import com.univault.upload.dto.UploadInitResponse;
import com.univault.upload.service.UploadSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadSessionService uploadSessionService;

    @PostMapping("/init")
    public ResponseEntity<UploadInitResponse> init(@RequestBody UploadInitRequest request, Authentication authentication) {
        UploadInitResponse response = uploadSessionService.initUpload(request, (UUID) authentication.getPrincipal());
        return ResponseEntity.ok(response);
    }

    // Returning CompletableFuture here is what actually frees the Tomcat
    // thread while a chunk is mid-upload/retry — matches
    // UploadSessionService.uploadChunk() now returning
    // CompletableFuture<ChunkUploadResponse> instead of blocking.
    @PostMapping(value = "/chunk", consumes = "multipart/form-data")
    public CompletableFuture<ResponseEntity<ChunkUploadResponse>> uploadChunk(
            @RequestParam("fileId") String fileId,
            @RequestParam("serialNumber") int serialNumber,
            @RequestParam(value = "checksum", required = false) String checksum,
            @RequestParam("file") MultipartFile chunkFile,
            Authentication authentication) throws IOException {

        byte[] data = chunkFile.getBytes();
        String actualFileName = chunkFile.getOriginalFilename();


        System.out.println(
                "Chunk " + serialNumber +
                        ", MD5 = " + ChecksumUtil.computeMd5(data)
        );
        return uploadSessionService.uploadChunk(fileId, serialNumber, data, checksum, actualFileName,(UUID) authentication.getPrincipal())
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/complete")
    public ResponseEntity<UploadCompleteResponse> complete(@RequestParam String fileId, Authentication authentication) {
        UploadCompleteResponse response = uploadSessionService.completeUpload(fileId,(UUID)authentication.getPrincipal());
        return ResponseEntity.ok(response);
    }
}