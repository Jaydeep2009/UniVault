package com.univault.controller;

import com.univault.entity.ActivityLog;
import com.univault.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
@Slf4j
public class ActivityController {

    private final ActivityLogService activityLogService;

    /**
     * Get user activity logs with optional filtering
     * GET /api/activity?type=UPLOAD&provider=GOOGLE_DRIVE&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<ActivityLog>> getActivities(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();
        log.info("Fetching activities for user {}", userId);

        ActivityLog.ActivityType activityType = null;
        if (type != null && !type.equals("all")) {
            try {
                activityType = ActivityLog.ActivityType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid activity type: {}", type);
            }
        }

        Page<ActivityLog> activities = activityLogService.getFilteredActivities(
                userId, activityType, provider, page, size);

        return ResponseEntity.ok(activities);
    }

    /**
     * Get activity statistics
     * GET /api/activity/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getActivityStats(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        log.info("Fetching activity stats for user {}", userId);

        Map<String, Long> stats = activityLogService.getActivityStats(userId);
        return ResponseEntity.ok(stats);
    }
}
