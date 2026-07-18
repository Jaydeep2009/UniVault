package com.univault.providers;

import lombok.Setter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MockStorageProvider implements StorageProvider {

    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();
    private final AtomicLong usedSpace = new AtomicLong(0);

    private final double failureRate;      // e.g. 0.1 = 10% of calls fail
    private final long simulatedLatencyMs; // e.g. 150
    private final long totalSpaceBytes;    // e.g. 15L * 1024 * 1024 * 1024 (15GB, like real Drive free tier)

    // Test hook: let integration tests flip the provider down
    @Setter
    private volatile boolean healthy = true;

    public MockStorageProvider() {
        this(0.9, 150, 15L * 1024 * 1024 * 1024);
    }

    public MockStorageProvider(double failureRate, long simulatedLatencyMs, long totalSpaceBytes) {
        this.failureRate = failureRate;
        this.simulatedLatencyMs = simulatedLatencyMs;
        this.totalSpaceBytes = totalSpaceBytes;
    }

    @Override
    public String uploadChunk(String providerFileId, byte[] data) {
        simulateLatency();
        maybeFail("uploadChunk");

        String id = (providerFileId != null) ? providerFileId : "mock-" + UUID.randomUUID();
        storage.put(id, data);
        usedSpace.addAndGet(data.length);
        return id;
    }

    @Override
    public InputStream downloadChunk(String providerFileId) {
        simulateLatency();
        maybeFail("downloadChunk");

        byte[] data = storage.get(providerFileId);
        if (data == null) {
            throw new IllegalStateException("Mock chunk not found: " + providerFileId);
        }
        return new ByteArrayInputStream(data);
    }

    @Override
    public boolean deleteChunk(String providerFileId) {
        simulateLatency();
        maybeFail("deleteChunk");

        byte[] removed = storage.remove(providerFileId);
        if (removed != null) {
            usedSpace.addAndGet(-removed.length);
            return true;
        }
        return false;
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    @Override
    public long getAvailableSpaceBytes() {
        return totalSpaceBytes - usedSpace.get();
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.MOCK;
    }

    private void simulateLatency() {
        if (simulatedLatencyMs > 0) {
            try {
                Thread.sleep(simulatedLatencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void maybeFail(String operation) {
        if (Math.random() < failureRate) {
            throw new RuntimeException("Simulated failure in MockStorageProvider." + operation);
        }
    }
}