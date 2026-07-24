package com.univault.upload.service;

import com.univault.common.util.ChecksumUtil;
import com.univault.common.util.MimeTypeUtil;
import com.univault.entity.Folder;
import com.univault.entity.StorageProviderAccount;
import com.univault.providers.ProviderFactory;
import com.univault.providers.StorageProvider;
import com.univault.repository.FolderRepository;
import com.univault.repository.StorageProviderAccountRepository;
import com.univault.storage.StoragePoolManager;
import com.univault.upload.dto.ChunkUploadResponse;
import com.univault.upload.dto.UploadCompleteResponse;
import com.univault.upload.dto.UploadInitRequest;
import com.univault.upload.dto.UploadInitResponse;
import com.univault.upload.entity.ChunkEntity;
import com.univault.upload.entity.FileEntity;
import com.univault.upload.enums.ChunkStatus;
import com.univault.upload.enums.FileStatus;
import com.univault.upload.exception.ChunkUploadFailedException;
import com.univault.upload.exception.InsufficientStorageException;
import com.univault.upload.repository.ChunkRepository;
import com.univault.upload.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class UploadSessionService {

    private final FileRepository fileRepository;
    private final ChunkRepository chunkRepository;
    private final ChunkUploadRetryHandler retryHandler;
    private final StoragePoolManager storagePoolManager;
    private final ProviderFactory providerFactory;
    private final StorageProviderAccountRepository accountRepository;
    private final FolderRepository folderRepository;
    @Value("${univault.chunk.size-bytes:4194304}")
    private int chunkSizeBytes;

    public UploadInitResponse initUpload(UploadInitRequest request, UUID userId) {
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

        // Assign folder if provided, validate it belongs to this user
        if (request.getFolderId() != null && !request.getFolderId().isBlank()) {
            UUID folderUuid = UUID.fromString(request.getFolderId());
            Folder folder = folderRepository.findByIdAndUserId(folderUuid, userId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Folder not found or does not belong to user: " + request.getFolderId()));
            file.setFolderId(folder.getId());
        }


        file = fileRepository.save(file);

        return new UploadInitResponse(file.getId().toString(), expectedChunks, chunkSizeBytes);
    }

    /**
     * Returns CompletableFuture<ChunkUploadResponse> instead of blocking.
     * Everything through account selection is unchanged and still runs
     * synchronously on the calling thread — it's cheap (DB reads, a
     * checksum, StoragePoolManager's account pick). Only the actual
     * provider upload + retry is async, via ChunkUploadRetryHandler.
     * Synchronous early-return paths (checksum mismatch) are wrapped with
     * CompletableFuture.completedFuture() for a consistent return type.
     *
     * Preserves original behavior: ChunkUploadFailedException (retries
     * exhausted) is still caught here and turned into a normal FAILED
     * response, never rethrown to the controller.
     */
    public CompletableFuture<ChunkUploadResponse> uploadChunk(String fileId, int serialNumber, byte[] data,
                                                              String clientChecksum, String actualFileName, UUID callerId) {
        UUID fileUuid = UUID.fromString(fileId);
        FileEntity file = fileRepository.findById(fileUuid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown fileId: " + fileId));

        if (!file.getUserId().equals(callerId)) {
            throw new IllegalArgumentException("Unknown fileId: " + fileId); // or a dedicated 403/404
        }

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
            return CompletableFuture.completedFuture(
                    new ChunkUploadResponse(fileId, serialNumber, "FAILED", actualChecksum));
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

        // Per-chunk account selection — this is what makes multi-account
        // pooling real: different chunks of the same file can resolve to
        // different connected accounts depending on available space.
        StorageProviderAccount account = storagePoolManager
                .selectAccountForChunk(file.getUserId(), data.length)
                .orElseThrow(() -> new InsufficientStorageException(
                        "No connected account has room for this chunk"));

        StorageProvider provider = providerFactory.getProvider(account);
        String providerFileId = fileId + "-chunk-" + serialNumber;

        return retryHandler.uploadWithRetry(provider, providerFileId, data)
                .handle((result, error) -> {
                    if (error == null) {
                        chunk.setProviderFileId(result.providerFileId());
                        chunk.setProviderId(account.getId());
                        chunk.setStatus(ChunkStatus.COMPLETE);
                        chunk.setRetryCount(result.retriesUsed());

                        account.setUsedQuotaBytes(
                                (account.getUsedQuotaBytes() != null ? account.getUsedQuotaBytes() : 0L) + data.length);
                        accountRepository.save(account);
                    } else {
                        Throwable cause = unwrap(error);
                        chunk.setStatus(ChunkStatus.FAILED);
                        chunk.setRetryCount(
                                cause instanceof ChunkUploadFailedException cufe ? cufe.getAttemptsMade() : -1);
                    }
                    chunkRepository.save(chunk);
                    return new ChunkUploadResponse(fileId, serialNumber, chunk.getStatus().name(), actualChecksum);
                });
    }

    // CompletableFuture wraps async-stage exceptions in CompletionException,
    // possibly nested through the retry handler's recursive backoff chain —
    // unwrap down to the real cause (e.g. ChunkUploadFailedException).
    private static Throwable unwrap(Throwable t) {
        while (t instanceof CompletionException && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    public UploadCompleteResponse completeUpload(String fileId, UUID callerId) {
        UUID fileUuid = UUID.fromString(fileId);
        FileEntity file = fileRepository.findById(fileUuid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown fileId: " + fileId));
        if (!file.getUserId().equals(callerId)) {
            throw new IllegalArgumentException("Unknown fileId: " + fileId);
        }

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