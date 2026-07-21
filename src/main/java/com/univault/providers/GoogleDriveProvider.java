package com.univault.providers;

import com.univault.entity.StorageProviderAccount;
import org.springframework.stereotype.Component;
import java.io.InputStream;

public class GoogleDriveProvider implements StorageProvider {

    private final StorageProviderAccount account;
    // Later: Google Drive SDK client, built from account's decrypted credentials

    private GoogleDriveProvider(StorageProviderAccount account) {
        this.account = account;
    }

    @Override
    public String uploadChunk(String providerFileId, byte[] data) {
        throw new UnsupportedOperationException("Pending Step G: OAuth2 connect flow not yet implemented");
    }

    @Override
    public InputStream downloadChunk(String providerFileId) {
        throw new UnsupportedOperationException("Pending Step G: OAuth2 connect flow not yet implemented");
    }

    @Override
    public boolean deleteChunk(String providerFileId) {
        throw new UnsupportedOperationException("Pending Step G: OAuth2 connect flow not yet implemented");
    }

    @Override
    public boolean isHealthy() {
        throw new UnsupportedOperationException("Pending Step G: OAuth2 connect flow not yet implemented");
    }

    @Override
    public long getAvailableSpaceBytes() {
        throw new UnsupportedOperationException("Pending Step G: OAuth2 connect flow not yet implemented");
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.GOOGLE_DRIVE;
    }

    @Component
    public static class Factory {
        public GoogleDriveProvider create(StorageProviderAccount account) {
            return new GoogleDriveProvider(account);
        }
    }
}