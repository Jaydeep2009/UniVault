package com.univault.storage.service;

import com.univault.entity.StorageProviderAccount;
import com.univault.repository.StorageProviderAccountRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class StoragePoolManager {

    private final StorageProviderAccountRepository accountRepository;

    public StoragePoolManager(StorageProviderAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Picks the best connected account for a chunk of the given size, using a simple
     * greedy-by-most-free-quota strategy. Considers all accounts except DISCONNECTED.
     * Uses stored quota fields rather than live provider calls, to avoid a Drive API
     * call per candidate account on every chunk placement decision.
     *
     * @return the chosen account, or Optional.empty() if no eligible account has enough
     *         free space for a chunk this size. Caller decides what to do next.
     */
    public Optional<StorageProviderAccount> selectAccountForChunk(UUID userId, long chunkSizeBytes) {
        List<StorageProviderAccount> candidates = accountRepository.findByUserId(userId);

        return candidates.stream()
                .filter(this::isEligible)
                .filter(account -> accountFreeSpace(account) >= chunkSizeBytes)
                .max((a, b) -> Long.compare(accountFreeSpace(a), accountFreeSpace(b)));
    }

    /**
     * Total free space across ALL of a user's eligible connected accounts combined —
     * used by Member 2's upload-init flow to reject a file up front if the user's whole
     * pool can't fit it, before any chunking/placement happens. NOT the same check as
     * selectAccountForChunk, which looks at a single account's capacity for one chunk.
     */
    public long getAvailableSpaceBytes(UUID userId) {
        List<StorageProviderAccount> candidates = accountRepository.findByUserId(userId);

        return candidates.stream()
                .filter(this::isEligible)
                .mapToLong(this::accountFreeSpace)
                .sum();
    }

    private boolean isEligible(StorageProviderAccount account) {
        return account.getStatus() != StorageProviderAccount.AccountStatus.DISCONNECTED;
    }

    private long accountFreeSpace(StorageProviderAccount account) {
        long total = account.getTotalQuotaBytes() != null ? account.getTotalQuotaBytes() : 0L;
        long used = account.getUsedQuotaBytes() != null ? account.getUsedQuotaBytes() : 0L;
        return total - used;
    }
}