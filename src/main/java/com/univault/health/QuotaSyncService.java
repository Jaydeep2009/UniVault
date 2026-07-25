package com.univault.health;

import com.univault.entity.StorageProviderAccount;
import com.univault.entity.StorageProviderAccount.AccountStatus;
import com.univault.providers.GoogleDriveProvider;
import com.univault.providers.ProviderFactory;
import com.univault.providers.StorageProvider;
import com.univault.repository.StorageProviderAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Periodically pulls real quota numbers (total/used bytes) from each connected provider and
 * writes them onto {@code StorageProviderAccount.totalQuotaBytes/usedQuotaBytes} — the same
 * fields {@code StorageStatsService} reads from on-demand. Without this, those columns are
 * only ever as fresh as whatever was written at connect-time; a user filling up their Drive
 * from outside UniVault (e.g. uploading directly in the Drive web UI) would never be reflected.
 *
 * Only ACTIVE accounts are synced. ERROR/EXPIRED accounts are skipped on purpose: a provider
 * call is exactly what's already failing for them, so retrying it here just duplicates
 * AccountHealthCheckService's job and burns an extra API call for no new information. Once
 * AccountHealthCheckService flips one back to ACTIVE, it picks back up on the next quota sync.
 *
 * {@link StorageProvider#getAvailableSpaceBytes()} is on the interface, but there is no
 * matching "getTotalSpaceBytes()" — only {@code GoogleDriveProvider.fetchQuota()} exposes both
 * numbers together, and it's deliberately provider-specific rather than added to the locked
 * interface. So this service special-cases GoogleDriveProvider today; adding a real second
 * provider (OneDrive, B2, R2) should either give it an equivalent quota-pair method or, if this
 * pattern repeats, promote a total/used quota pair onto the StorageProvider interface itself.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaSyncService {

    private final StorageProviderAccountRepository accountRepository;
    private final ProviderFactory providerFactory;

    public void syncAllQuotas() {
        List<StorageProviderAccount> activeAccounts = loadActiveAccounts();

        log.info("Quota sync starting for {} active account(s)", activeAccounts.size());

        int synced = 0;
        for (StorageProviderAccount account : activeAccounts) {
            try {
                if (syncAccount(account)) {
                    synced++;
                }
            } catch (Exception e) {
                log.warn("Quota sync failed for account {} ({}): {}",
                        account.getId(), account.getProviderType(), e.getMessage());
            }
        }

        log.info("Quota sync complete: {}/{} account(s) updated", synced, activeAccounts.size());
    }

    private List<StorageProviderAccount> loadActiveAccounts() {
        return accountRepository.findAll().stream()
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .toList();
    }

    private boolean syncAccount(StorageProviderAccount account) {
        StorageProvider provider = providerFactory.getProvider(account);

        if (provider instanceof GoogleDriveProvider googleDriveProvider) {
            GoogleDriveProvider.QuotaInfo quota = googleDriveProvider.fetchQuota();
            account.setTotalQuotaBytes(quota.totalBytes());
            account.setUsedQuotaBytes(quota.usedBytes());
            accountRepository.save(account);
            return true;
        }

        log.debug("Skipping quota sync for account {} — no quota-pair support for provider type {}",
                account.getId(), account.getProviderType());
        return false;
    }
}
