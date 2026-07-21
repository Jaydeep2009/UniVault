package com.univault.providers;

import com.univault.entity.StorageProviderAccount;

public interface ProviderFactory {
    StorageProvider getProvider(StorageProviderAccount account);
}