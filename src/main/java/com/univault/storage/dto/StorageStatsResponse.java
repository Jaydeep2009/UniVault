package com.univault.storage.dto;

import java.util.List;

public record StorageStatsResponse(
        long totalQuotaBytes,
        long usedQuotaBytes,
        long availableQuotaBytes,
        List<AccountStorageStats> accounts
) {}