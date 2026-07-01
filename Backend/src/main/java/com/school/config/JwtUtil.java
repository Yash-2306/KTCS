package com.school.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JwtUtil — Creates and reads JWT login tokens.
 *
 * ── What is a JWT? (for interviews) ─────────────────────────────────────────
 * JWT = JSON Web Token. It's a string that looks like: xxxxx.yyyyy.zzzzz
 *   - Part 1 (header): says which algorithm was used (HS256)
 *   - Part 2 (payload): contains the user's id, username, role, expiry time
 *   - Part 3 (signature): the server signs parts 1+2 with a secret key
 *
 * When the backend gets a token back from the frontend, it re-checks the
 * signature. If anyone changed even one character, the signature won't match
 * and we reject it. That's how we know the token is genuine.
 *
 * ── Secret key ───────────────────────────────────────────────────────────────
 * Read from the JWT_SECRET environment variable.
 * On your laptop (local dev), it uses a default fallback key.
 * On Railway (production), you set JWT_SECRET to a long random string.
 * The secret NEVER goes to the frontend or into GitHub — it only lives on the server.
 */
@Component
public class JwtUtil {

    // Token is valid for 8 hours. After that, the user needs to log in again.
    private static final long EIGHT_HOURS_MS = 8 * 60 * 60 * 1000L;

    private final SecretKey signingKey;

    public JwtUtil() {
        // Read the secret from the environment variable (set on Railway)
        String secret = System.getenv("JWT_SECRET");

        if (secret == null || secret.isBlank()) {
            // No secret set — we're in local development. Use a fallback.
            // This is fine locally, but Railway MUST have JWT_SECRET set.
            secret = "local-dev-secret-key-do-not-use-in-prod-at-least-32chars";
            System.out.println("WARNING: JWT_SECRET not set. Using dev fallback. Set it on Railway before going live.");
        }

        // JJWT requires at least 32 characters (256 bits) for HS256
        // If the secret is shorter, pad it to avoid errors
        if (secret.length() < 32) {
            secret = String.format("%-32s", secret); // pad with spaces to reach 32 chars
        }

        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * generateToken — call this after a successful login.
     * Returns a JWT string to send to the frontend.
     */
    public String generateToken(Long userId, String username, String role) {
        return Jwts.builder()
                .subject(username)                              // who this token belongs to
                .claim("userId", userId)                        // their database ID
                .claim("role", role)                            // ADMIN / TEACHER / STUDENT
                .issuedAt(new Date())                           // when was it created
                .expiration(new Date(System.currentTimeMillis() + EIGHT_HOURS_MS)) // when it expires
                .signWith(signingKey)                           // sign it with our secret
                .compact();                                     // build the final string
    }

    /**
     * parseToken — reads a token and returns its contents (claims).
     * Throws an exception if the token is invalid, expired, or tampered with.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)     // check the signature
                .build()
                .parseSignedClaims(token)  // decode and verify
                .getPayload();             // get the data inside
    }

    /**
     * isValid — returns true if the token can be trusted, false otherwise.
     */
    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            // Token is expired, tampered, or garbage
            return false;
        }
    }
}
