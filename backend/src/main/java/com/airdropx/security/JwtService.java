package com.airdropx.security;

import com.airdropx.common.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Owns the access token (a real, self-contained JWT — role and companyId are embedded as claims so
 * every request is authorized without a DB round trip) and a couple of generic helpers that AuthService
 * uses for refresh tokens. Refresh tokens are deliberately NOT JWTs: they're opaque random strings whose
 * SHA-256 hash is stored in refresh_tokens.token_hash, so a leaked database dump doesn't hand out usable
 * tokens, and a single row delete is enough to revoke one (a stateless JWT refresh token can't be revoked
 * that simply). This split is worth being able to explain in an interview.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_COMPANY_ID = "companyId";

    private final SecretKey signingKey;
    private final long accessTokenTtlMinutes;
    private final long refreshTokenTtlDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(
            @Value("${airdropx.jwt.secret}") String secret,
            @Value("${airdropx.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes,
            @Value("${airdropx.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays
    ) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "airdropx.jwt.secret must be at least 32 bytes (256 bits) for HS256 — set JWT_SECRET.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    public String generateAccessToken(UUID userId, UserRole role, UUID companyId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtlMinutes, ChronoUnit.MINUTES)));
        if (companyId != null) {
            builder.claim(CLAIM_COMPANY_ID, companyId.toString());
        }
        return builder.signWith(signingKey).compact();
    }

    /** Throws JwtException (expired, malformed, or bad signature) — callers treat that as "unauthenticated". */
    public Claims parseAndValidate(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public UserRole getRole(Claims claims) {
        return UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
    }

    public String generateOpaqueToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JVM — this branch is unreachable in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public long getAccessTokenTtlMinutes() {
        return accessTokenTtlMinutes;
    }

    public long getRefreshTokenTtlDays() {
        return refreshTokenTtlDays;
    }
}
