package com.univault.service;

import com.univault.entity.ActivityLog;
import com.univault.repository.ActivityLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    /**
     * Log an activity
     */
    public void logActivity(UUID userId, ActivityLog.ActivityType type, String description,
                           String itemName, String itemType, UUID itemId,
                           String location, String provider, String metadata) {
        try {
            ActivityLog activityLog = new ActivityLog();
            activityLog.setUserId(userId);
            activityLog.setType(type);
            activityLog.setDescription(description);
            activityLog.setItemName(itemName);
            activityLog.setItemType(itemType);
            activityLog.setItemId(itemId);
            activityLog.setLocation(location);
            activityLog.setProvider(provider);
            activityLog.setMetadata(metadata);
            activityLog.setIpAddress(getClientIpAddress());

            activityLogRepository.save(activityLog);
            log.info("Activity logged: {} - {} for user {}", type, description, userId);
        } catch (Exception e) {
            log.error("Failed to log activity: {}", e.getMessage(), e);
            // Don't throw exception - activity logging should not break main functionality
        }
    }

    /**
     * Get user activities with pagination
     */
    public Page<ActivityLog> getUserActivities(UUID userId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 20);
        return activityLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
    }

    /**
     * Get filtered activities
     */
    public Page<ActivityLog> getFilteredActivities(UUID userId, ActivityLog.ActivityType type,
                                                    String provider, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 20);

        if (type != null && provider != null && !provider.equals("all")) {
            return activityLogRepository.findByUserIdAndTypeAndProviderOrderByTimestampDesc(
                    userId, type, provider, pageable);
        } else if (type != null) {
            return activityLogRepository.findByUserIdAndTypeOrderByTimestampDesc(userId, type, pageable);
        } else if (provider != null && !provider.equals("all")) {
            return activityLogRepository.findByUserIdAndProviderOrderByTimestampDesc(userId, provider, pageable);
        } else {
            return activityLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
        }
    }

    /**
     * Get activity statistics for the last 30 days
     */
    public Map<String, Long> getActivityStats(UUID userId) {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        Map<String, Long> stats = new HashMap<>();
        stats.put("uploads", activityLogRepository.countByUserIdAndType(userId, ActivityLog.ActivityType.UPLOAD));
        stats.put("downloads", activityLogRepository.countByUserIdAndType(userId, ActivityLog.ActivityType.DOWNLOAD));
        stats.put("shares", activityLogRepository.countByUserIdAndType(userId, ActivityLog.ActivityType.SHARE));
        stats.put("deletes", activityLogRepository.countByUserIdAndType(userId, ActivityLog.ActivityType.DELETE));
        stats.put("restores", activityLogRepository.countByUserIdAndType(userId, ActivityLog.ActivityType.RESTORE));
        stats.put("logins", activityLogRepository.countByUserIdAndType(userId, ActivityLog.ActivityType.LOGIN));

        return stats;
    }

    /**
     * Get client IP address from request
     */
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.warn("Failed to get client IP address: {}", e.getMessage());
        }
        return "Unknown";
    }
}
