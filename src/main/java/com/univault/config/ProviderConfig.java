
package com.univault.config;

import com.univault.providers.MockStorageProvider;
import com.univault.providers.StorageProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProviderConfig {

    @Value("${univault.mock.failure-rate:0.1}")
    private double mockFailureRate;

    @Value("${univault.mock.latency-ms:150}")
    private long mockLatencyMs;

    // TEMPORARY: single hardcoded mock bean until Member 1's
    // ProviderFactory + real GoogleDriveProvider exist.
    @Bean
    public StorageProvider mockStorageProvider() {
        return new MockStorageProvider(mockFailureRate, mockLatencyMs, 15L * 1024 * 1024 * 1024);
    }
}