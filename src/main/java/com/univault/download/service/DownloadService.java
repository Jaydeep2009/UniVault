package com.univault.download.service;

import com.univault.download.dto.FileMetadataResponse;
import com.univault.download.exception.FileNotReadyException;
import com.univault.upload.entity.FileEntity;
import com.univault.upload.enums.FileStatus;
import com.univault.upload.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DownloadService {
    private final FileRepository fileRepository;
    private final ChunkReassemblyService chunkReassemblyService;

    /*
     * Gets file metadata with ownership validation.
     *
     * @param fileId The file's ID (string format)
     * @param userId The authenticated user's ID
     * @return FileMetadataResponse
     * @throws IllegalArgumentException if file not found or doesn't belong to user
     */
    public FileMetadataResponse getFileMetadata(String fileId, UUID userId){
        UUID fileUUID = UUID.fromString(fileId);
        FileEntity file = fileRepository.findById(fileUUID)
                .orElseThrow(() -> new IllegalArgumentException("File not found with ID: " + fileId));

        // Validate ownership
        if(!file.getUserId().equals(userId)){
            throw new IllegalArgumentException("File does not belong to the authenticated user.");
        }

        return new FileMetadataResponse(
                file.getId(),
                file.getName(),
                file.getSize(),
                file.getMimeType(),
                file.getStatus().name(),
                file.getTotalChunks(),
                file.getCreatedAt(),
                file.getFolderId()
        );
    }

    /*
     * Downloads a file as an InputStream.
     *
     * @param fileId The file's ID (string format)
     * @param userId The authenticated user's ID
     * @return InputStream of the complete file
     * @throws IllegalArgumentException if file not found or doesn't belong to user
     * @throws FileNotReadyException if file is not in READY status
     */

    public InputStream downloadFile(String fileId, UUID userId){
        log.info("Download request for file ID: {} by user ID: {}", fileId, userId);
        UUID fileUUID = UUID.fromString(fileId);
        FileEntity file = fileRepository.findById(fileUUID)
                .orElseThrow(() -> new IllegalArgumentException("File not found with ID: " + fileId));

        // Validate ownership
        if(!file.getUserId().equals(userId)){
            log.warn("User {} attempted to access file {} owned by user {}",
                    userId, fileId, file.getUserId());
            throw new IllegalArgumentException("File: "+fileId+" does not belong to the authenticated user.");
        }

        // Validate file status
        if (file.getStatus() != FileStatus.READY) {
            log.warn("Download attempted for file {} with status {}", fileId, file.getStatus());
            throw new FileNotReadyException(
                    "File is not ready for download",
                    fileId,
                    file.getStatus().name()
            );
        }

        log.info("Starting download for file {}: {} ({} bytes)",
                fileId, file.getName(), file.getSize());

        // Delegate to reassembly service
        return chunkReassemblyService.reassembleFile(fileUUID);
    }

    /**
     * Gets FileEntity with ownership validation (internal use).
     */
    public FileEntity getFile(String fileId, UUID userId) {
        UUID fileUuid = UUID.fromString(fileId);
        FileEntity file = fileRepository.findById(fileUuid)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        if (!file.getUserId().equals(userId)) {
            throw new IllegalArgumentException("File not found: " + fileId);
        }

        return file;
    }
}
