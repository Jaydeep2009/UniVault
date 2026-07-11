package com.univault.config;

import com.univault.providers.MockStorageProvider;
import com.univault.providers.StorageProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProviderConfig {
    // TEMPORARY: single hardcoded mock bean until Member 1's
    // ProviderFactory + real GoogleDriveProvider exist.
    // UploadSessionService depends only on the StorageProvider interface,
    // so swapping this out later is a one-line change here, not in upload logic.
    @Bean
    public StorageProvider mockStorageProvider() {
        return new MockStorageProvider(0.1, 150, 15L * 1024 * 1024 * 1024);
    }
}
