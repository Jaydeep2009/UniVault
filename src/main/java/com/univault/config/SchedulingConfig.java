package com.univault.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Enables the health-monitor's @Scheduled tasks (com.univault.health.HealthMonitorScheduler).
 *
 * Spring's default @Scheduled executor is a single-threaded scheduler, which means the health
 * check, token refresh, and quota sync tasks would queue behind each other even though they're
 * independent and have no reason to serialize. A small dedicated pool lets them run concurrently
 * without needing a bigger general-purpose async executor for the whole app.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("univault-health-");
        scheduler.setErrorHandler(t -> {
            // Belt-and-suspenders: HealthMonitorScheduler already catches per-task, but this
            // ensures nothing thrown from scheduling infrastructure itself is ever swallowed.
            org.slf4j.LoggerFactory.getLogger(SchedulingConfig.class)
                    .error("Uncaught error in scheduled task execution", t);
        });
        return scheduler;
    }
}
