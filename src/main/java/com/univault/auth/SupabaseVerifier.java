package com.univault.auth;

import com.univault.auth.dto.SupabaseUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Component for verifying Supabase access tokens.
 * Calls Supabase's /auth/v1/user endpoint to cryptographically verify tokens
 * and extract verified user information.
 */
@Component
@Slf4j
public class SupabaseVerifier {

    private final String supabaseUrl;
    private final String supabaseAnonKey;
    private final RestTemplate restTemplate;
    
    public SupabaseVerifier(
        @Value("${SUPABASE_URL}") String supabaseUrl,
        @Value("${SUPABASE_ANON_KEY}") String supabaseAnonKey
    ) {
        this.supabaseUrl = supabaseUrl;
        this.supabaseAnonKey = supabaseAnonKey;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Verifies a Supabase access token by calling Supabase's user endpoint.
     * Supabase performs cryptographic verification and returns verified user data.
     * 
     * @param accessToken The Supabase access token to verify
     * @return SupabaseUser containing verified user information
     * @throws SecurityException if token verification fails
     */
    public SupabaseUser verifyToken(String accessToken) {
        try {
            // Call Supabase's user endpoint with the access token
            // Supabase verifies the token cryptographically and returns verified user info
            String url = supabaseUrl + "/auth/v1/user";
            
            log.debug("Verifying Supabase token at endpoint: {}", url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("apikey", supabaseAnonKey);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Supabase token verification failed with status: {}", response.getStatusCode());
                throw new SecurityException("Supabase token verification failed");
            }
            
            Map<String, Object> userData = response.getBody();
            
            // Extract user metadata (contains Google profile information)
            @SuppressWarnings("unchecked")
            Map<String, Object> userMetadata = (Map<String, Object>) userData.get("user_metadata");
            
            String userId = (String) userData.get("id");
            String email = (String) userData.get("email");
            
            // Extract display name and profile picture from user metadata
            String displayName = null;
            String profilePicture = null;
            
            if (userMetadata != null) {
                displayName = (String) userMetadata.get("full_name");
                profilePicture = (String) userMetadata.get("avatar_url");
            }
            
            log.info("Successfully verified Supabase token for user: {} ({})", email, userId);
            
            return new SupabaseUser(
                userId,
                email,
                displayName,
                profilePicture
            );
            
        } catch (SecurityException e) {
            // Re-throw security exceptions
            throw e;
        } catch (Exception e) {
            log.error("Supabase token verification failed", e);
            throw new SecurityException("Invalid Supabase token: " + e.getMessage());
        }
    }
}
