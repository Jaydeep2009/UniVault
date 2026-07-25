package com.univault.health;

import com.univault.entity.StorageProviderAccount;
import com.univault.entity.StorageProviderAccount.AccountStatus;
import com.univault.providers.ProviderFactory;
import com.univault.providers.StorageProvider;
import com.univault.repository.StorageProviderAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Runs {@link StorageProvider#isHealthy()} against every connected account on a schedule
 * and keeps {@code status} / {@code lastHealthCheckAt} on {@link StorageProviderAccount} in
 * sync with reality, instead of only finding out an account is broken the next time a user
 * happens to trigger an upload/download through it.
 *
 * Deliberately does NOT touch DISCONNECTED accounts — those were removed on purpose (user
 * clicked disconnect), so probing them would be pointless and, for OAuth2 accounts whose
 * tokens may have been revoked, likely to just log noisy failures.
 *
 * ACTIVE, ERROR and EXPIRED accounts are all checked: ACTIVE to catch new breakage, ERROR/EXPIRED
 * so an account that recovers (e.g. transient Google outage, or a token refresh that succeeded
 * via {@link TokenRefreshService} in the meantime) gets flipped back to ACTIVE automatically
 * rather than staying stuck until a human intervenes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountHealthCheckService {

    private final StorageProviderAccountRepository accountRepository;
    private final ProviderFactory providerFactory;

    public void checkAllAccounts() {
        List<StorageProviderAccount> accounts = accountRepository.findAll().stream()
                .filter(a -> a.getStatus() != AccountStatus.DISCONNECTED)
                .toList();

        log.info("Health check starting for {} account(s)", accounts.size());

        int healthy = 0;
        int unhealthy = 0;

        for (StorageProviderAccount account : accounts) {
            try {
                if (checkAccount(account)) {
                    healthy++;
                } else {
                    unhealthy++;
                }
            } catch (Exception e) {
                // One account's failure (e.g. unimplemented provider, factory error) must never
                // stop the rest of the batch from being checked.
                unhealthy++;
                log.warn("Health check failed unexpectedly for account {} ({}): {}",
                        account.getId(), account.getProviderType(), e.getMessage());
            }
        }

        log.info("Health check complete: {} healthy, {} unhealthy", healthy, unhealthy);
    }

    /**
     * @return true if the account was found healthy, false otherwise.
     */
    private boolean checkAccount(StorageProviderAccount account) {
        StorageProvider provider = providerFactory.getProvider(account);
        boolean isHealthy = provider.isHealthy();

        account.setLastHealthCheckAt(Instant.now());

        AccountStatus previousStatus = account.getStatus();
        AccountStatus newStatus = resolveStatus(account, isHealthy);

        if (previousStatus != newStatus) {
            log.info("Account {} ({}) status changed: {} -> {}",
                    account.getId(), account.getProviderType(), previousStatus, newStatus);
        }

        account.setStatus(newStatus);
        accountRepository.save(account);

        return isHealthy;
    }

    /**
     * isHealthy() only returns a boolean — no reason code — so EXPIRED vs ERROR is inferred
     * from our own token bookkeeping rather than from the provider call itself:
     *   - unhealthy + token already past expiry  -> EXPIRED (token problem, likely fixable by
     *     TokenRefreshService or requires the user to reconnect if the refresh token itself
     *     was revoked)
     *   - unhealthy + token still valid          -> ERROR (something else: outage, network,
     *     revoked scope, etc.)
     *   - healthy                                -> ACTIVE
     */
    private AccountStatus resolveStatus(StorageProviderAccount account, boolean isHealthy) {
        if (isHealthy) {
            return AccountStatus.ACTIVE;
        }
        Instant expiresAt = account.getTokenExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            return AccountStatus.EXPIRED;
        }
        return AccountStatus.ERROR;
    }
}
