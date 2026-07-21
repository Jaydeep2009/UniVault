package com.univault.providers;

import java.io.InputStream;

public interface StorageProvider {

    String uploadChunk(String providerFileId, byte[] data);


    InputStream downloadChunk(String providerFileId);

    boolean deleteChunk(String providerFileId);

    boolean isHealthy();

    long getAvailableSpaceBytes();

    ProviderType getProviderType();
}