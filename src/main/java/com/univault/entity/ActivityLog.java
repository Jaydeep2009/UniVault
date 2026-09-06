package com.univault.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_logs")
@Data
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 500)
    private String itemName;

    @Column(length = 50)
    private String itemType; // file, folder

    private UUID itemId; // Reference to file or folder

    @Column(length = 100)
    private String location;

    @Column(length = 50)
    private String provider; // GOOGLE_DRIVE, ONEDRIVE, etc.

    @Column(length = 1000)
    private String metadata; // Additional info as JSON or text

    @Column(length = 100)
    private String ipAddress;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    public enum ActivityType {
        UPLOAD,
        DOWNLOAD,
        SHARE,
        DELETE,
        RESTORE,
        LOGIN,
        LOGOUT,
        PROVIDER_CONNECT,
        PROVIDER_DISCONNECT,
        FOLDER_CREATE,
        FOLDER_DELETE,
        FILE_RENAME,
        FILE_MOVE
    }
}
