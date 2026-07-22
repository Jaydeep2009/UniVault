
package com.univault.upload.service;

import com.univault.common.exception.ChunkUploadFailedException;
import com.univault.common.exception.InsufficientStorageException;
import com.univault.common.util.ChecksumUtil;
import com.univault.common.util.MimeTypeUtil;
import com.univault.entity.StorageProviderAccount;
import com.univault.providers.ProviderFactory;
import com.univault.providers.StorageProvider;
import com.univault.storage.StoragePoolManager;
import com.univault.upload.dto.ChunkUploadResponse;
import com.univault.upload.dto.UploadCompleteResponse;
import com.univault.upload.dto.UploadInitRequest;
import com.univault.upload.dto.UploadInitResponse;
import com.univault.upload.entity.ChunkEntity;
import com.univault.upload.entity.FileEntity;
import com.univault.upload.enums.ChunkStatus;
import com.univault.upload.enums.FileStatus;
import com.univault.upload.repository.ChunkRepository;
import com.univault.upload.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;


@Service
@RequiredArgsConstructor
public class UploadSessionService {

    private final FileRepository fileRepository;
    private final ChunkRepository chunkRepository;
    private final ChunkUploadRetryHandler retryHandler;
    private final StoragePoolManager storagePoolManager;
    private final ProviderFactory providerFactory;

    @Value("${univault.chunk.size-bytes:4194304}")
    private int chunkSizeBytes;

    public UploadInitResponse initUpload(UploadInitRequest request, UUID userId) {
        // TODO: replace with real userId once JWT principal is wired in (Member 1's auth)
        //UUID userId = UUID.randomUUID(); // placeholder — every init currently looks like a different user

        long declaredSize = request.getFileSize();
        long availableSpace = storagePoolManager.getAvailableSpaceBytes(userId);

        if (declaredSize > availableSpace) {
            throw new InsufficientStorageException(
                    "Not enough space: file is " + declaredSize + " bytes, only " + availableSpace + " available");
        }

        int expectedChunks = (int) Math.ceil((double) declaredSize / chunkSizeBytes);

        FileEntity file = new FileEntity();
        file.setUserId(userId);
        file.setName(request.getFileName());
        file.setSize(declaredSize); // provisional — overwritten with real measured size at /complete
        file.setChunked(expectedChunks > 1);
        file.setTotalChunks(expectedChunks);
        file.setStatus(FileStatus.UPLOADING);
        // TODO: file.setFolderId(...) if request.getFolderId() present

        file = fileRepository.save(file);

        return new UploadInitResponse(file.getId().toString(), expectedChunks, chunkSizeBytes);
    }

    public ChunkUploadResponse uploadChunk(String fileId, int serialNumber, byte[] data,
                                           String clientChecksum, String actualFileName) {
        UUID fileUuid = UUID.fromString(fileId);
        FileEntity file = fileRepository.findById(fileUuid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown fileId: " + fileId));

        if (serialNumber < 0 || serialNumber >= file.getTotalChunks()) {
            throw new IllegalArgumentException(
                    "Invalid serialNumber " + serialNumber + " for file with " + file.getTotalChunks() + " expected chunks");
        }

        // Derive real mimeType from the actual uploaded file's name, only on the first chunk.
        // Client's /init fileName was provisional — this corrects it to the truth.
        if (serialNumber == 0 && actualFileName != null) {
            String resolvedMimeType = MimeTypeUtil.resolve(actualFileName);
            file.setMimeType(resolvedMimeType);
            fileRepository.save(file);
        }

        // Prevent silently overwriting a chunk that's already successfully uploaded.
        Optional<ChunkEntity> existing = chunkRepository.findByFileIdAndSerialNumber(fileUuid, serialNumber);
        ChunkEntity chunk;
        if (existing.isPresent() && existing.get().getStatus() == ChunkStatus.COMPLETE) {
            throw new IllegalStateException(
                    "Chunk " + serialNumber + " already uploaded and marked COMPLETE for file " + fileId);
        } else if (existing.isPresent()) {
            chunk = existing.get(); // previously FAILED — reuse the row for retry
        } else {
            chunk = new ChunkEntity();
            chunk.setFileId(fileUuid);
            chunk.setSerialNumber(serialNumber);
        }

        String actualChecksum = ChecksumUtil.computeMd5(data);
        if (clientChecksum != null && !clientChecksum.equals(actualChecksum)) {
            chunk.setStatus(ChunkStatus.FAILED);
            chunkRepository.save(chunk);
            return new ChunkUploadResponse(fileId, serialNumber, "FAILED", actualChecksum);
        }

        // Enforce declared size as a ceiling — catches a client sending far more
        // data than it declared at /init, before it consumes unbudgeted space.
        long alreadyUploadedBytes = chunkRepository.findByFileIdOrderBySerialNumber(fileUuid).stream()
                .filter(c -> c.getStatus() == ChunkStatus.COMPLETE)
                .mapToLong(ChunkEntity::getSize)
                .sum();
        long projectedTotal = alreadyUploadedBytes + data.length;
        long tolerance = chunkSizeBytes; // allow one chunk's worth of slack for estimation error

        if (projectedTotal > file.getSize() + tolerance) {
            throw new IllegalStateException(
                    "Upload exceeds declared file size — declared " + file.getSize() +
                            " bytes, but received " + projectedTotal + " so far");
        }

        chunk.setSize(data.length);
        chunk.setChecksum(actualChecksum);
        StorageProviderAccount account = storagePoolManager
                .selectAccountForChunk(file.getUserId(), data.length)
                .orElseThrow(() -> new InsufficientStorageException(
                        "No connected account has room for this chunk"));

        StorageProvider provider = providerFactory.getProvider(account);
        String providerFileId = fileId + "-chunk-" + serialNumber;

        try {
            ChunkUploadRetryHandler.RetryResult result = retryHandler.uploadWithRetry(provider, providerFileId, data);
            chunk.setProviderFileId(result.providerFileId());
            chunk.setProviderId(account.getId());   // ChunkEntity field confirmed above
            chunk.setStatus(ChunkStatus.COMPLETE);
            chunk.setRetryCount(result.retriesUsed());
        } catch (ChunkUploadFailedException e) {
            chunk.setStatus(ChunkStatus.FAILED);
            chunk.setRetryCount(e.getAttemptsMade());
        }


        chunkRepository.save(chunk);

        return new ChunkUploadResponse(fileId, serialNumber, chunk.getStatus().name(), actualChecksum);
    }

    public UploadCompleteResponse completeUpload(String fileId) {
        UUID fileUuid = UUID.fromString(fileId);
        FileEntity file = fileRepository.findById(fileUuid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown fileId: " + fileId));

        List<ChunkEntity> chunks = chunkRepository.findByFileIdOrderBySerialNumber(fileUuid);

        List<Integer> uploadedSerials = chunks.stream()
                .filter(c -> c.getStatus() == ChunkStatus.COMPLETE)
                .map(ChunkEntity::getSerialNumber)
                .toList();

        List<Integer> missing = IntStream.range(0, file.getTotalChunks())
                .filter(i -> !uploadedSerials.contains(i))
                .boxed()
                .toList();

        if (missing.isEmpty()) {
            long actualTotalSize = chunks.stream()
                    .filter(c -> c.getStatus() == ChunkStatus.COMPLETE)
                    .mapToLong(ChunkEntity::getSize)
                    .sum();

            file.setSize(actualTotalSize); // overwrite declared estimate with real measured size
            file.setStatus(FileStatus.READY);
            fileRepository.save(file);
            return new UploadCompleteResponse(fileId, "READY", missing);
        } else {
            return new UploadCompleteResponse(fileId, "INCOMPLETE", missing);
        }
    }

}