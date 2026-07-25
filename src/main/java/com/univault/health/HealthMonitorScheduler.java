package com.univault.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Entry point for all background production-readiness tasks: account health checks, proactive
 * OAuth2 token refresh, and quota sync from providers.
 *
 * Uses fixedDelay (not fixedRate) everywhere: the delay is measured from the END of the previous
 * run, not a fixed clock tick. If a run takes longer than usual (e.g. Google API is slow, or the
 * account list has grown), the next run simply starts later instead of overlapping the one still
 * in progress. Since each task iterates every connected account and makes a real network call per
 * account, overlapping runs would be the more dangerous failure mode here.
 *
 * initialDelay staggers the three tasks so they don't all fire together on app startup and hit
 * external provider APIs at the same moment.
 *
 * All actual work lives in the injected services — this class is intentionally just wiring, so
 * the schedule (frequency, stagger) can be reasoned about independently of the health-check /
 * token-refresh / quota-sync logic itself.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HealthMonitorScheduler {

    private final AccountHealthCheckService accountHealthCheckService;
    private final TokenRefreshService tokenRefreshService;
    private final QuotaSyncService quotaSyncService;

    /** Default: every hour. */
    @Scheduled(
            initialDelayString = "${univault.health.check-initial-delay-ms:30000}",
            fixedDelayString = "${univault.health.check-interval-ms:3600000}"
    )
    public void runAccountHealthChecks() {
        runSafely("account health check", accountHealthCheckService::checkAllAccounts);
    }

    /** Default: every 30 minutes. */
    @Scheduled(
            initialDelayString = "${univault.health.token-refresh-initial-delay-ms:60000}",
            fixedDelayString = "${univault.health.token-refresh-interval-ms:1800000}"
    )
    public void runTokenRefresh() {
        runSafely("token refresh", tokenRefreshService::refreshExpiringTokens);
    }

    /** Default: every 6 hours — quota drifts far more slowly than health/token state. */
    @Scheduled(
            initialDelayString = "${univault.health.quota-sync-initial-delay-ms:90000}",
            fixedDelayString = "${univault.health.quota-sync-interval-ms:21600000}"
    )
    public void runQuotaSync() {
        runSafely("quota sync", quotaSyncService::syncAllQuotas);
    }

    /**
     * @Scheduled methods that throw silently stop being rescheduled by Spring, which would
     * quietly kill an entire background job forever after one bad run. Every task is wrapped
     * so a single unexpected exception is logged instead of taking down the schedule.
     */
    private void runSafely(String taskName, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("Scheduled task '{}' failed unexpectedly", taskName, e);
        }
    }
}
