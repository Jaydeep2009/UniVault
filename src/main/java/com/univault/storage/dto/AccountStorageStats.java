package com.univault.storage.dto;

import com.univault.entity.StorageProviderAccount;
import com.univault.providers.ProviderType;

import java.util.UUID;

public record AccountStorageStats(
        UUID accountId,
        ProviderType providerType,
        String accountLabel,
        StorageProviderAccount.AccountStatus status,
        long totalQuotaBytes,
        long usedQuotaBytes,
        long availableQuotaBytes
) {}