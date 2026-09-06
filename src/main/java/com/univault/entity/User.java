package com.univault.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "supabase_user_id", unique = true)
    private String supabaseUserId;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "auth_type", nullable = false)
    private String authType;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}