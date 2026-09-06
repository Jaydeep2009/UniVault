package com.univault.auth;

import com.univault.auth.dto.GoogleAuthRequest;
import com.univault.auth.dto.SupabaseUser;
import com.univault.entity.User;
import com.univault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for handling Google OAuth 2.0 authentication via Supabase Auth.
 * This service is responsible for:
 * - Verifying Supabase access tokens
 * - Creating new users from verified Google identities
 * - Linking Google accounts to existing email/password users
 * - Handling returning Google users
 * 
 * Note: This service handles authentication only (openid, profile, email scopes).
 * Google Drive storage provider integration is handled separately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final SupabaseVerifier supabaseVerifier;

    /**
     * Authenticate user with Supabase access token.
     * 
     * This method:
     * 1. Verifies the Supabase access token cryptographically
     * 2. Extracts verified user ID and email from the token
     * 3. Handles three scenarios:
     *    a) Returning user: finds existing user by Supabase ID
     *    b) Account linking: links Google to existing email/password user
     *    c) New user: creates new user with Google authentication
     * 
     * @param request GoogleAuthRequest containing Supabase access token
     * @return User entity (existing or newly created)
     * @throws SecurityException if token verification fails
     */
    public User authenticateSupabaseUser(GoogleAuthRequest request) {
        // 1. Cryptographically verify Supabase access token
        // This extracts the verified user ID and email from the token itself
        SupabaseUser supabaseUser = supabaseVerifier.verifyToken(
            request.getSupabaseAccessToken()
        );
        
        log.info("Verified Supabase user: {} ({})", supabaseUser.getEmail(), supabaseUser.getId());
        
        // 2. Find existing user by Supabase ID (returning user case)
        Optional<User> existingBySupabase = userRepository
            .findBySupabaseUserId(supabaseUser.getId());
        
        if (existingBySupabase.isPresent()) {
            log.info("Returning existing Google user: {}", existingBySupabase.get().getEmail());
            return existingBySupabase.get();
        }
        
        // 3. Check if email/password user exists with this verified email (account linking case)
        // IMPORTANT: Email comes from VERIFIED Supabase token, not from frontend
        Optional<User> existingByEmail = userRepository
            .findByEmail(supabaseUser.getEmail());
        
        if (existingByEmail.isPresent()) {
            // Link Google to existing account
            User user = existingByEmail.get();
            user.setSupabaseUserId(supabaseUser.getId());
            // Update auth_type: if user has password, it's BOTH; otherwise GOOGLE
            user.setAuthType(user.getPasswordHash() != null ? "BOTH" : "GOOGLE");
            
            // Mark email as verified since Google has verified it
            user.setEmailVerified(true);
            
            // Update profile information from Google if available
            if (supabaseUser.getDisplayName() != null) {
                user.setDisplayName(supabaseUser.getDisplayName());
            }
            if (supabaseUser.getProfilePicture() != null) {
                user.setProfilePictureUrl(supabaseUser.getProfilePicture());
            }
            
            userRepository.save(user);
            log.info("Linked Google to existing user: {} (auth_type: {})", user.getEmail(), user.getAuthType());
            return user;
        }
        
        // 4. Create new user with verified information (new user case)
        User newUser = new User();
        newUser.setEmail(supabaseUser.getEmail());
        newUser.setSupabaseUserId(supabaseUser.getId());
        newUser.setAuthType("GOOGLE");
        newUser.setEmailVerified(true);  // Google has verified the email
        newUser.setDisplayName(supabaseUser.getDisplayName());
        newUser.setProfilePictureUrl(supabaseUser.getProfilePicture());
        newUser.setPasswordHash(null);  // No password for Google-only users
        
        userRepository.save(newUser);
        log.info("Created new Google user: {}", newUser.getEmail());
        return newUser;
    }
}
