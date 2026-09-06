package com.univault.repository;

import com.univault.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {
    
    Page<ActivityLog> findByUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);
    
    Page<ActivityLog> findByUserIdAndTypeOrderByTimestampDesc(UUID userId, ActivityLog.ActivityType type, Pageable pageable);
    
    Page<ActivityLog> findByUserIdAndProviderOrderByTimestampDesc(UUID userId, String provider, Pageable pageable);
    
    Page<ActivityLog> findByUserIdAndTypeAndProviderOrderByTimestampDesc(UUID userId, ActivityLog.ActivityType type, String provider, Pageable pageable);
    
    Page<ActivityLog> findByUserIdAndTimestampAfterOrderByTimestampDesc(UUID userId, Instant after, Pageable pageable);
    
    List<ActivityLog> findByUserIdAndTimestampAfter(UUID userId, Instant after);
    
    long countByUserIdAndType(UUID userId, ActivityLog.ActivityType type);
}
