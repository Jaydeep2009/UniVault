package com.univault.auth;

import com.univault.auth.dto.AuthResponse;
import com.univault.auth.dto.GoogleAuthRequest;
import com.univault.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for Google OAuth 2.0 authentication via Supabase Auth.
 * 
 * This controller exposes the /auth/google endpoint that:
 * - Accepts Supabase access tokens from the frontend callback
 * - Verifies the token with Supabase
 * - Creates/links/finds the corresponding UniVault user
 * - Issues a UniVault JWT for session management
 * 
 * Flow:
 * 1. Frontend initiates Google OAuth via Supabase SDK
 * 2. Google redirects back to Supabase
 * 3. Supabase redirects to frontend callback with session
 * 4. Frontend sends Supabase access token to this endpoint
 * 5. Backend verifies token and returns UniVault JWT
 * 
 * Security:
 * - This endpoint does NOT trust any user data from frontend
 * - All user information is extracted from cryptographically verified Supabase token
 * - Supabase handles OAuth state, nonce, and token validation
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthController {

    private final GoogleAuthService googleAuthService;
    private final JwtService jwtService;

    /**
     * Authenticate user with Google via Supabase.
     * 
     * Accepts a Supabase access token from the frontend, verifies it with Supabase,
     * creates or finds the corresponding UniVault user, and issues a UniVault JWT.
     * 
     * This endpoint handles three scenarios:
     * 1. New user: Creates new User with auth_type='GOOGLE'
     * 2. Returning user: Finds existing user by Supabase ID
     * 3. Account linking: Links Google to existing email/password user (auth_type='BOTH')
     * 
     * @param request GoogleAuthRequest containing Supabase access token
     * @return AuthResponse with UniVault JWT
     * @throws SecurityException if Supabase token verification fails (returns 401)
     */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> authenticateWithGoogle(
            @Valid @RequestBody GoogleAuthRequest request) {
        
        log.info("Google authentication request received");
        
        try {
            // Verify Supabase token and get/create/link user
            User user = googleAuthService.authenticateSupabaseUser(request);
            
            // Issue UniVault JWT for session management
            String token = jwtService.generateToken(user);
            
            log.info("Successfully authenticated user: {} (auth_type: {})", user.getEmail(), user.getAuthType());
            
            return ResponseEntity.ok(new AuthResponse(token));
            
        } catch (SecurityException e) {
            log.error("Google authentication failed: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Unexpected error during Google authentication", e);
            return ResponseEntity.status(500).build();
        }
    }
}
