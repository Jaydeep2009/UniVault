package com.univault.auth;

import com.univault.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Generates and validates JWTs. This is the only class that ever touches
 * the signing key — everything else (the filter, the controller) just
 * calls generateToken()/isTokenValid()/extractUserId() without knowing
 * anything about how the token is actually signed.
 *
 * The signing key comes from the JWT_SECRET environment variable (Base64,
 * generate the same way as TOKEN_ENCRYPTION_KEY) — never hardcoded.
 */
@Component
public class JwtService {

    private static final long EXPIRATION_MS = 24L * 60 * 60 * 1000; // 24 hours

    private final SecretKey key;

    public JwtService() {
        this.key = loadKey();
    }

    /** Called once at login/signup — issues a new signed token for this user. */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION_MS);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /** Called on every request — false for a bad signature, tampering, or expiry. */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            // covers ExpiredJwtException, SignatureException, MalformedJwtException, etc.
            return false;
        }
    }

    /** Only call this after isTokenValid() has returned true. */
    public UUID extractUserId(String token) {
        Claims claims = parseClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static SecretKey loadKey() {
        String base64Key = System.getenv("JWT_SECRET");
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable is not set. " +
                            "Generate one the same way as TOKEN_ENCRYPTION_KEY " +
                            "(PowerShell RNGCryptoServiceProvider snippet or openssl rand -base64 32)."
            );
        }
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        return Keys.hmacShaKeyFor(decoded);
    }
}