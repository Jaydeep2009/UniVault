package com.univault.storage.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of a Folder returned to the client. Deliberately flat —
 * no nested parent object, just the parent's id, to avoid recursively
 * serializing an entire folder tree on every response.
 */
public record FolderResponse(
        UUID id,
        String name,
        UUID parentFolderId,
        Instant createdAt
) {}