package com.univault.upload.controller;

import com.univault.upload.dto.*;
import com.univault.upload.service.UploadSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {
    private final UploadSessionService uploadSessionService;

    @PostMapping("/init")
    public ResponseEntity<UploadInitResponse> initUpload(@RequestBody UploadInitRequest request) {
        UploadInitResponse response = uploadSessionService.initUpload(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value="/chunk", consumes="multipart/form-data")
    public ResponseEntity<ChunkUploadResponse> uploadChunk(
            @RequestParam("fileId") String fileId,
            @RequestParam("serialNumber") int serialNumber,
            @RequestParam(value="checksum", required=false) String checksum,
            @RequestParam("file") MultipartFile chunkFile)throws Exception {
        byte[] data= chunkFile.getBytes();
        ChunkUploadRequest request = new ChunkUploadRequest(fileId, serialNumber, data, checksum);
        ChunkUploadResponse response = uploadSessionService.uploadChunk(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/complete")
    public ResponseEntity<UploadCompleteResponse> completeUpload(@RequestParam String fileId){
        UploadCompleteResponse response=uploadSessionService.completeUpload(fileId);

        return ResponseEntity.ok(response);
    }

}
