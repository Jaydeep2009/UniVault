// storage/StoragePoolManager.java — MINIMAL STUB (Member 1 owns the real one)
// Replace with Member 1's actual implementation once agreed — this exists
// only so UploadSessionService compiles and can be tested against the mock.
package com.univault.storage;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StoragePoolManager {

    /**
     * TEMPORARY STUB: returns a large fixed value so space checks pass
     * during local testing. Replace with real aggregation across a user's
     * connected StorageProviderAccount rows once Member 1's version lands.
     */
    public long getAvailableSpaceBytes(UUID userId) {
        return 50L * 1024 * 1024 * 1024; // 50GB placeholder
    }
}