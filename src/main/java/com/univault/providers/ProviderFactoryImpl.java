package com.univault.providers;

import com.univault.entity.StorageProviderAccount;
import org.springframework.stereotype.Component;

@Component
public class ProviderFactoryImpl implements ProviderFactory {

    private final GoogleDriveProvider.Factory googleDriveProviderFactory;
    // Later: OneDriveProvider.Factory, etc.

    public ProviderFactoryImpl(GoogleDriveProvider.Factory googleDriveProviderFactory) {
        this.googleDriveProviderFactory = googleDriveProviderFactory;
    }

    @Override
    public StorageProvider getProvider(StorageProviderAccount account) {
        return switch (account.getProviderType()) {
            case GOOGLE_DRIVE -> googleDriveProviderFactory.create(account);
            case MOCK -> throw new IllegalStateException(
                    "MOCK provider type is not reachable via ProviderFactory — Member 2 wires MockStorageProvider directly in tests."
            );
            default -> throw new UnsupportedOperationException(
                    account.getProviderType() + " is not yet implemented"
            );
        };
    }
}