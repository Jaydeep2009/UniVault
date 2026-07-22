package com.univault.entity;

import com.univault.providers.ProviderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "storage_provider_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StorageProviderAccount {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private ProviderType providerType;

    @Column(name = "account_label")
    private String accountLabel;

    //@Column(name = "auth_type", nullable = false)
    //private String authType; // see AuthType constants: OAUTH2, STATIC_KEY
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false)
    private AuthType authType;

    // --- TEMP: plain columns until EncryptedStringConverter exists (Step D) ---
    // TODO: uncomment @Convert lines and remove plain @Column below once
    // com.univault.security.EncryptedStringConverter is written.
    @Convert(converter = com.univault.security.EncryptedStringConverter.class)
    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Convert(converter = com.univault.security.EncryptedStringConverter.class)
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "total_quota_bytes")
    private Long totalQuotaBytes;

    @Column(name = "used_quota_bytes")
    private Long usedQuotaBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "last_health_check_at")
    private Instant lastHealthCheckAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) {
            this.status = AccountStatus.ACTIVE;
        }
    }
    public enum AuthType {
        OAUTH2, STATIC_KEY
    }
    public enum AccountStatus {
        ACTIVE, EXPIRED, ERROR, DISCONNECTED
    }
}