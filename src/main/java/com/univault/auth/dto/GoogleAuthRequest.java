package com.univault.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO for Google authentication request from frontend.
 * Contains ONLY the Supabase access token - backend extracts all user info securely.
 */
@Data
public class GoogleAuthRequest {
    /**
     * Supabase access token obtained from frontend after Google OAuth flow.
     * Backend verifies this token with Supabase to extract verified user information.
     */
    @NotBlank(message = "Supabase access token is required")
    private String supabaseAccessToken;
}
