package com.univault.health;

import com.univault.entity.StorageProviderAccount;
import com.univault.entity.StorageProviderAccount.AccountStatus;
import com.univault.entity.StorageProviderAccount.AuthType;
import com.univault.providers.ProviderFactory;
import com.univault.providers.StorageProvider;
import com.univault.repository.StorageProviderAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Proactively refreshes OAuth2 access tokens before they expire, instead of waiting for a
 * user-triggered upload/download to hit an expired token mid-request.
 *
 * Per the locked design, token refresh is deliberately NOT part of {@link StorageProvider} —
 * each provider handles it internally (see {@code GoogleDriveProvider.refreshTokenIfExpired},
 * which runs automatically inside {@code buildDriveClient()} whenever the current access token
 * is within 60s of expiry). So rather than exposing a new "refresh()" method on the interface
 * (which would break that decision), this service just triggers any authenticated call —
 * {@link StorageProvider#isHealthy()} is the cheapest one available on every provider — for
 * accounts whose token is coming up on expiry. That's enough to make each provider's own
 * internal refresh logic run and persist the new token.
 *
 * Only OAUTH2 accounts are considered; STATIC_KEY accounts (e.g. a future Backblaze B2 app-key
 * setup) have nothing to refresh.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRefreshService {

    private final StorageProviderAccountRepository accountRepository;
    private final ProviderFactory providerFactory;

    /**
     * How far ahead of actual expiry to start proactively refreshing. Wider than the 60s
     * buffer inside GoogleDriveProvider itself, since this only runs periodically (every
     * univault.health.token-refresh-interval-ms) rather than on every request — needs enough
     * lead time that a token doesn't expire in the gap between two scheduled runs.
     */
    @Value("${univault.health.token-refresh-lookahead-minutes:10}")
    private long lookaheadMinutes;

    public void refreshExpiringTokens() {
        Instant threshold = Instant.now().plus(Duration.ofMinutes(lookaheadMinutes));

        List<StorageProviderAccount> candidates = accountRepository.findAll().stream()
                .filter(a -> a.getStatus() != AccountStatus.DISCONNECTED)
                .filter(a -> a.getAuthType() == AuthType.OAUTH2)
                .filter(a -> a.getTokenExpiresAt() != null && a.getTokenExpiresAt().isBefore(threshold))
                .toList();

        if (candidates.isEmpty()) {
            log.debug("Token refresh sweep: no accounts within {} minute lookahead window", lookaheadMinutes);
            return;
        }

        log.info("Token refresh sweep: {} account(s) within {} minute lookahead window",
                candidates.size(), lookaheadMinutes);

        for (StorageProviderAccount account : candidates) {
            try {
                StorageProvider provider = providerFactory.getProvider(account);
                // Triggers the provider's own internal refresh-if-expired logic as a side effect.
                provider.isHealthy();
                log.info("Token refresh triggered for account {} ({})", account.getId(), account.getProviderType());
            } catch (Exception e) {
                // A refresh failure here (e.g. refresh token itself revoked) will surface as
                // an unhealthy result on the next AccountHealthCheckService pass, which will
                // correctly mark the account EXPIRED/ERROR — no need to duplicate that logic here.
                log.warn("Token refresh attempt failed for account {} ({}): {}",
                        account.getId(), account.getProviderType(), e.getMessage());
            }
        }
    }
}
