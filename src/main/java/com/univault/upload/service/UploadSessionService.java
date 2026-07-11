package com.univault.upload.service;

import com.univault.common.util.ChecksumUtil;
import com.univault.providers.StorageProvider;
import com.univault.upload.dto.*;
import com.univault.upload.entity.ChunkEntity;
import com.univault.upload.entity.FileEntity;
import com.univault.upload.enums.ChunkStatus;
import com.univault.upload.enums.FileStatus;
import com.univault.upload.repository.ChunkRepository;
import com.univault.upload.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class UploadSessionService {
    private final FileRepository fileRepository;
    private final ChunkRepository chunkRepository;
    private final StorageProvider storageProvider; // currently the Mock bean
    private final int chunkSizeBytes = 4 * 1024 * 1024; //TODO: pull from same @Value as ChunkingService

    public UploadInitResponse initUpload(UploadInitRequest request){
        int expectedChunks = (int) Math.ceil((double) request.getFileSize() / chunkSizeBytes);

        FileEntity file = new FileEntity();
        file.setName(request.getFileName());
        file.setSize(request.getFileSize());
        file.setMimeType(request.getMimeType());
        file.setChunked(expectedChunks > 1);
        file.setTotalChunks(expectedChunks);
        file.setStatus(FileStatus.UPLOADING);
        // TODO: file.setUserId(...) once auth/JWT principal is wired in
        // TODO: file.setFolderId(...) if request.getFolderId() present

        fileRepository.save(file);

        return new UploadInitResponse(file.getId().toString(), expectedChunks, chunkSizeBytes);

    }

    public ChunkUploadResponse uploadChunk(ChunkUploadRequest request){
        UUID fileUuid = UUID.fromString(request.getFileId());
        Optional<FileEntity> optionalFile = fileRepository.findById(fileUuid);
        if(!optionalFile.isPresent()){
            throw new IllegalArgumentException("File not found for ID: " + request.getFileId());
        }

        String actualChecksum = ChecksumUtil.computeMd5(request.getData());
        if(request.getClientChecksum() != null && !request.getClientChecksum().equals(actualChecksum)){
            return new ChunkUploadResponse(request.getFileId(), request.getSerialNumber(), "FAILED",actualChecksum);
        }

        ChunkEntity chunk = new ChunkEntity();
        chunk.setFileId(fileUuid);
        chunk.setSerialNumber(request.getSerialNumber());
        chunk.setSize(request.getData().length);
        chunk.setChecksum(actualChecksum);
        chunk.setStatus(ChunkStatus.UPLOADING);
        chunk.setRetryCount(0);

        try {
            String providerFileId = storageProvider.uploadChunk(null, request.getData());
            chunk.setProviderFileId(providerFileId);
            chunk.setStatus(ChunkStatus.COMPLETE);
        } catch (Exception e) {
            // Retry/backoff logic comes later — for now, mark failed
            chunk.setStatus(ChunkStatus.FAILED);
            chunk.setRetryCount(chunk.getRetryCount() + 1);
        }

        chunkRepository.save(chunk);

        return new ChunkUploadResponse(request.getFileId(), request.getSerialNumber(), chunk.getStatus().name(), actualChecksum);

    }

    public UploadCompleteResponse completeUpload(String fileId){
        UUID fileUuid = UUID.fromString(fileId);
        Optional<FileEntity> optionalFile = fileRepository.findById(fileUuid);
        if(optionalFile.isEmpty()){
            throw new IllegalArgumentException("File not found for ID: " + fileId);
        }
        FileEntity file = optionalFile.get();

        List<ChunkEntity> chunks = chunkRepository.findByFileIdOrderBySerialNumber(fileUuid);
        List<Integer> uploadedSerials = chunks.stream()
                .filter(c -> c.getStatus() == ChunkStatus.COMPLETE)
                .map(ChunkEntity::getSerialNumber)
                .toList();

        List<Integer> missing = IntStream.range(0, file.getTotalChunks())
                .filter(i -> !uploadedSerials.contains(i))
                .boxed()
                .collect(Collectors.toList());

        if (missing.isEmpty()) {
            file.setStatus(FileStatus.READY);
            fileRepository.save(file);
            return new UploadCompleteResponse(fileId, "READY", missing);
        } else {
            return new UploadCompleteResponse(fileId, "INCOMPLETE", missing);
        }
    }

}
