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
import java.util.concurrent.CompletableFuture;

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

    // Returning CompletableFuture here (instead of ResponseEntity directly) is
    // what actually delivers the parallel-upload win: Spring MVC detaches this
    // request from its Tomcat thread the moment the future is returned, and
    // re-dispatches to write the response only once it completes. Multiple
    // /chunk requests -- including ones sitting in retry backoff -- no longer
    // each pin a servlet thread for the duration of the upload.
    @PostMapping(value = "/chunk", consumes = "multipart/form-data")
    public CompletableFuture<ResponseEntity<ChunkUploadResponse>> uploadChunk(
            @RequestParam("fileId") String fileId,
            @RequestParam("serialNumber") int serialNumber,
            @RequestParam(value = "checksum", required = false) String checksum,
            @RequestParam("file") MultipartFile chunkFile) throws IOException {

        byte[] data = chunkFile.getBytes();
        String actualFileName = chunkFile.getOriginalFilename();

        return uploadSessionService.uploadChunk(fileId, serialNumber, data, checksum, actualFileName)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/complete")
    public ResponseEntity<UploadCompleteResponse> complete(@RequestParam String fileId) {
        UploadCompleteResponse response = uploadSessionService.completeUpload(fileId);
        return ResponseEntity.ok(response);
    }
}