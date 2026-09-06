package com.univault.providers;

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.UserCredentials;
import com.univault.entity.StorageProviderAccount;
import com.univault.repository.StorageProviderAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.time.Instant;

public class GoogleDriveProvider implements StorageProvider {

    private static final HttpTransport HTTP_TRANSPORT;
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Failed to initialize Google HTTP transport", e);
        }
    }

    private final StorageProviderAccount account;
    private final StorageProviderAccountRepository accountRepository;
    private final String clientId;
    private final String clientSecret;

    private GoogleDriveProvider(StorageProviderAccount account,
                                StorageProviderAccountRepository accountRepository,
                                String clientId,
                                String clientSecret) {
        this.account = account;
        this.accountRepository = accountRepository;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Builds a fresh Drive client on every call. Deliberately NOT cached on the instance —
     * if the token was refreshed since construction, a cached client would keep using the
     * stale access token until the instance was thrown away.
     */
    /*
    private Drive buildDriveClient() {
        refreshTokenIfExpired();

        AccessToken accessToken = new AccessToken(account.getAccessToken(), null); // null = no known expiry on this object; we manage expiry ourselves via tokenExpiresAt
        GoogleCredentials credentials = GoogleCredentials.create(accessToken);
        HttpCredentialsAdapter credentialsAdapter = new HttpCredentialsAdapter(credentials);

        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentialsAdapter)
                .setApplicationName("UniVault")
                .build();
    }*/
    private Drive buildDriveClient() {
        refreshTokenIfExpired(); // Proactive refresh before building client

        // Note: We're intentionally NOT using UserCredentials auto-refresh here because
        // when it refreshes, we have no callback to persist the new token to our database.
        // Instead, we handle refresh explicitly in refreshTokenIfExpired() above.
        AccessToken accessToken = new AccessToken(account.getAccessToken(), 
                                                  java.util.Date.from(account.getTokenExpiresAt()));
        
        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(account.getRefreshToken())
                .setAccessToken(accessToken)
                .build();

        HttpCredentialsAdapter credentialsAdapter = new HttpCredentialsAdapter(credentials);

        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentialsAdapter)
                .setApplicationName("UniVault")
                .build();
    }

    /**
     * Token refresh handled internally per your locked design (not part of the interface).
     * Checks expiry, refreshes via Google's token endpoint, persists the new access token
     * + expiry back onto the account. Refresh token itself doesn't change on a normal refresh.
     */
    private void refreshTokenIfExpired() {
        Instant expiresAt = account.getTokenExpiresAt();
        if (expiresAt == null || expiresAt.isAfter(Instant.now().plusSeconds(60))) {
            return; // still valid (60s buffer to avoid racing expiry mid-request)
        }
        
        System.out.println("[GoogleDrive] Token expired for account " + account.getId() + 
                         ", attempting refresh... (expired at: " + expiresAt + ", now: " + Instant.now() + ")");
        
        try {
            GoogleTokenResponse tokenResponse = new GoogleRefreshTokenRequest(
                    HTTP_TRANSPORT, JSON_FACTORY,
                    account.getRefreshToken(), clientId, clientSecret
            ).execute();

            account.setAccessToken(tokenResponse.getAccessToken());
            account.setTokenExpiresAt(Instant.now().plusSeconds(tokenResponse.getExpiresInSeconds()));
            accountRepository.save(account); // EncryptedStringConverter re-encrypts on write
            
            System.out.println("[GoogleDrive] Token refresh successful for account " + account.getId() + 
                             ", new expiry: " + account.getTokenExpiresAt());

        } catch (IOException e) {
            System.err.println("[GoogleDrive] Token refresh FAILED for account " + account.getId() + 
                             ": " + e.getMessage());
            throw new IllegalStateException(
                    "Failed to refresh Google Drive token for account " + account.getId(), e);
        }
    }
    public record QuotaInfo(long totalBytes, long usedBytes) {}

    public QuotaInfo fetchQuota() {
        try {
            Drive drive = buildDriveClient();
            About about = drive.about().get().setFields("storageQuota").execute();
            About.StorageQuota quota = about.getStorageQuota();

            long total = quota.getLimit() != null ? quota.getLimit() : Long.MAX_VALUE;
            long used = quota.getUsage() != null ? quota.getUsage() : 0L;
            return new QuotaInfo(total, used);
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch Google Drive quota for account " + account.getId(), e);
        }
    }

    @Override
    public String uploadChunk(String providerFileId, byte[] data) {
        try {
            Drive drive = buildDriveClient();
            File fileMetadata = new File();
            // providerFileId here is used as the Drive file's display name, not its remote ID —
            // the actual Drive file ID is generated by Google and returned below.
            fileMetadata.setName(providerFileId);

            ByteArrayContent content = new ByteArrayContent("application/octet-stream", data);

            File uploaded = drive.files().create(fileMetadata, content)
                    .setFields("id")
                    .execute();

            return uploaded.getId();
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload chunk to Google Drive: " + providerFileId, e);
        }
    }

    @Override
    public InputStream downloadChunk(String providerFileId) {
        try {
            System.out.println("DEBUG: Building Drive client for download...");
            Drive drive = buildDriveClient();
            System.out.println("DEBUG: Drive client built. Starting download for: " + providerFileId);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            System.out.println("DEBUG: Calling Google Drive API executeMediaAndDownloadTo...");
            drive.files().get(providerFileId).executeMediaAndDownloadTo(out);
            System.out.println("DEBUG: Download complete. Bytes received: " + out.size());

            return new java.io.ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            System.out.println("DEBUG: Download failed with IOException: " + e.getMessage());
            throw new RuntimeException("Failed to download chunk from Google Drive: " + providerFileId, e);
        }
    }

    @Override
    public boolean deleteChunk(String providerFileId) {
        try {
            Drive drive = buildDriveClient();
            drive.files().delete(providerFileId).execute();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            Drive drive = buildDriveClient();
            drive.about().get().setFields("user").execute();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long getAvailableSpaceBytes() {
        try {
            Drive drive = buildDriveClient();
            About about = drive.about().get().setFields("storageQuota").execute();
            About.StorageQuota quota = about.getStorageQuota();

            Long limit = quota.getLimit();   // null == unlimited (rare, mostly Workspace accounts)
            Long usage = quota.getUsage();

            if (limit == null) {
                return Long.MAX_VALUE;
            }
            return limit - (usage != null ? usage : 0L);
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch Google Drive quota for account " + account.getId(), e);
        }
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.GOOGLE_DRIVE;
    }

    @Component
    public static class Factory {

        private final StorageProviderAccountRepository accountRepository;
        private final String clientId;
        private final String clientSecret;

        public Factory(StorageProviderAccountRepository accountRepository,
                       @Value("${GOOGLE_CLIENT_ID}") String clientId,
                       @Value("${GOOGLE_CLIENT_SECRET}") String clientSecret) {
            this.accountRepository = accountRepository;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        public GoogleDriveProvider create(StorageProviderAccount account) {
            return new GoogleDriveProvider(account, accountRepository, clientId, clientSecret);
        }

    }

}