package com.univault.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO representing a verified Supabase user extracted from token verification.
 * Contains user identity information from Supabase authentication.
 */
@Data
@AllArgsConstructor
public class SupabaseUser {
    /**
     * Supabase user ID (unique identifier from Supabase)
     */
    private String id;
    
    /**
     * Verified email address from Supabase
     */
    private String email;
    
    /**
     * Display name from Google profile (user_metadata.full_name)
     */
    private String displayName;
    
    /**
     * Profile picture URL from Google profile (user_metadata.avatar_url)
     */
    private String profilePicture;
}
