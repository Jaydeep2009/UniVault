package com.univault.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts and decrypts strings using AES-GCM.
 *
 * The secret key is read once from the TOKEN_ENCRYPTION_KEY environment
 * variable (a Base64-encoded 256-bit key) and never written to disk or
 * logged. Generate one locally with: openssl rand -base64 32
 *
 * Each call to encrypt() uses a fresh random IV (12 bytes), which is
 * required for GCM's security guarantees — reusing an IV with the same
 * key breaks confidentiality. The IV is prepended to the ciphertext and
 * the whole thing is Base64-encoded into a single string, since that's
 * what gets stored in the TEXT column.
 */
public final class AesGcmService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;   // 96-bit IV, standard for GCM
    private static final int TAG_LENGTH_BITS = 128;  // GCM authentication tag length

    private static final SecretKey KEY = loadKey();

    private AesGcmService() {
        // utility class, no instances
    }

    public static String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt value", e);
        }
    }

    public static String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored);

            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH_BYTES);
            byte[] cipherText = Arrays.copyOfRange(combined, IV_LENGTH_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt value", e);
        }
    }

    private static SecretKey loadKey() {
        String base64Key = System.getenv("TOKEN_ENCRYPTION_KEY");
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "TOKEN_ENCRYPTION_KEY environment variable is not set. " +
                            "Generate one with: openssl rand -base64 32"
            );
        }
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(decoded, "AES");
    }
}