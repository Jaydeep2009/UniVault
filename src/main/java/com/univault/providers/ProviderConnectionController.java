package com.univault.providers;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.univault.auth.JwtService;
import com.univault.entity.StorageProviderAccount;
import com.univault.entity.User;
import com.univault.repository.StorageProviderAccountRepository;
import com.univault.repository.UserRepository;
import com.univault.security.AesGcmService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/providers/google")
public class ProviderConnectionController {

    private final StorageProviderAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;


    private static final List<String> SCOPES = List.of(
            "https://www.googleapis.com/auth/drive.file",
            "https://www.googleapis.com/auth/drive.metadata.readonly"
    );
    private final GoogleDriveProvider.Factory driveProviderFactory;

    public ProviderConnectionController(
            StorageProviderAccountRepository accountRepository,
            UserRepository userRepository,
            GoogleDriveProvider.Factory driveProviderFactory,
            JwtService jwtService,
            @Value("${GOOGLE_CLIENT_ID}") String clientId,
            @Value("${GOOGLE_CLIENT_SECRET}") String clientSecret,
            @Value("${GOOGLE_REDIRECT_URI}") String redirectUri) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.driveProviderFactory = driveProviderFactory;
        this.jwtService = jwtService;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    private GoogleAuthorizationCodeFlow buildFlow() throws IOException {
        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
        details.setClientId(clientId);
        details.setClientSecret(clientSecret);
        GoogleClientSecrets secrets = new GoogleClientSecrets().setWeb(details);

        return new GoogleAuthorizationCodeFlow.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                secrets,
                SCOPES)
                .setAccessType("offline")   // required to get a refresh token
                .build();
    }

    @GetMapping("/authorize")
    public ResponseEntity<?> authorize(
            @RequestParam(value = "token", required = false) String jwtToken,
            Authentication authentication) throws IOException {
        
        // Get user ID - try JWT token from query param first (for browser redirects),
        // then fall back to the authentication context (for API calls with header)
        String userId = null;
        
        if (jwtToken != null && !jwtToken.isEmpty()) {
            // Validate JWT token from query parameter
            if (jwtService.isTokenValid(jwtToken)) {
                UUID userIdFromToken = jwtService.extractUserId(jwtToken);
                userId = userIdFromToken.toString();
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Invalid or expired token");
            }
        } else if (authentication != null && authentication.isAuthenticated()) {
            // JWT token was in Authorization header and already validated by filter
            userId = authentication.getName();
        }
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authentication required");
        }

        String url = buildFlow().newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setState(userId)
                .set("prompt", "consent")   // forces refresh_token on repeat connects too
                .build();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", url)
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestParam("code") String code,
            @RequestParam("state") String userId,
            @RequestParam(value = "error", required = false) String error) throws IOException {

        if (error != null) {
            return ResponseEntity.badRequest().body("Google OAuth error: " + error);
        }

        GoogleAuthorizationCodeTokenRequest tokenRequest = new GoogleAuthorizationCodeTokenRequest(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                clientId,
                clientSecret,
                code,
                redirectUri);

        TokenResponse tokenResponse = tokenRequest.execute();

        String accessToken = tokenResponse.getAccessToken();
        String refreshToken = tokenResponse.getRefreshToken();
        Long expiresInSeconds = tokenResponse.getExpiresInSeconds();

        if (refreshToken == null) {
            // Happens if the user already granted consent before and Google skips issuing
            // a new refresh token. The "prompt=consent" param above guards against this,
            // but worth surfacing clearly if it still happens.
            return ResponseEntity.badRequest().body(
                    "No refresh token returned — user may need to revoke prior access at " +
                            "myaccount.google.com/permissions and reconnect."
            );
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        StorageProviderAccount account = new StorageProviderAccount();
        account.setUser(user);
        account.setProviderType(ProviderType.GOOGLE_DRIVE);
        account.setAuthType(StorageProviderAccount.AuthType.OAUTH2);
        account.setAccessToken(accessToken);
        account.setRefreshToken(refreshToken);
        account.setTokenExpiresAt(
                expiresInSeconds != null
                        ? Instant.now().plusSeconds(expiresInSeconds)
                        : null
        );
        account.setStatus(StorageProviderAccount.AccountStatus.ACTIVE);
        account.setLastHealthCheckAt(Instant.now());

        accountRepository.save(account);

        try {
            GoogleDriveProvider provider = driveProviderFactory.create(account);
            GoogleDriveProvider.QuotaInfo quota = provider.fetchQuota();

            account.setTotalQuotaBytes(quota.totalBytes());
            account.setUsedQuotaBytes(quota.usedBytes());
            accountRepository.save(account);
        } catch (RuntimeException e) {
            // Connection succeeded and tokens are valid — don't fail the whole flow over a
            // quota-read hiccup. Leave quota at 0 for now; a health-check job can retry later.
            // TODO: log this properly once logging is wired in
            e.printStackTrace();
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "http://localhost:5173/storage?success=true")
                .build();

    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> disconnect(@PathVariable UUID accountId, Authentication authentication) {

        StorageProviderAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account not found: " + accountId));

        // Soft-delete only — never hard-delete an account chunks may still reference.
        account.setStatus(StorageProviderAccount.AccountStatus.DISCONNECTED);
        if (!account.getUser().getId().equals(UUID.fromString(authentication.getName()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        accountRepository.save(account);

        return ResponseEntity.noContent().build();
    }
}