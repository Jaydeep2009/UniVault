package com.univault.storage.service;

import com.univault.entity.StorageProviderAccount;
import com.univault.repository.StorageProviderAccountRepository;
import com.univault.storage.dto.AccountStorageStats;
import com.univault.storage.dto.StorageStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StorageStatsService {

    private final StorageProviderAccountRepository accountRepository;

    /**
     * Aggregates quota across ALL of a user's non-disconnected accounts, including
     * ones in ERROR/EXPIRED state — unlike StoragePoolManager's placement logic,
     * stats should surface a broken account rather than silently exclude it, so
     * the user can see something needs reconnecting.
     */
    public StorageStatsResponse getStats(UUID userId) {
        List<StorageProviderAccount> accounts = accountRepository.findByUserId(userId).stream()
                .filter(a -> a.getStatus() != StorageProviderAccount.AccountStatus.DISCONNECTED)
                .toList();

        List<AccountStorageStats> breakdown = accounts.stream()
                .map(this::toAccountStats)
                .collect(Collectors.toList());

        long totalQuota = breakdown.stream().mapToLong(AccountStorageStats::totalQuotaBytes).sum();
        long usedQuota = breakdown.stream().mapToLong(AccountStorageStats::usedQuotaBytes).sum();
        long availableQuota = breakdown.stream().mapToLong(AccountStorageStats::availableQuotaBytes).sum();

        return new StorageStatsResponse(totalQuota, usedQuota, availableQuota, breakdown);
    }

    private AccountStorageStats toAccountStats(StorageProviderAccount account) {
        long total = account.getTotalQuotaBytes() != null ? account.getTotalQuotaBytes() : 0L;
        long used = account.getUsedQuotaBytes() != null ? account.getUsedQuotaBytes() : 0L;
        long available = total - used;

        return new AccountStorageStats(
                account.getId(),
                account.getProviderType(),
                account.getAccountLabel(),
                account.getStatus(),
                total,
                used,
                available
        );
    }
}