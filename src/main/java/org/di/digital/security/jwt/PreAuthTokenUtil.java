package org.di.digital.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Component
public class PreAuthTokenUtil {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateFace(Long userId, String email, boolean enrollment) {
        long ttlMs = (enrollment ? 10 : 5) * 60_000L;
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(email)
                .claim("userId", userId)
                .claim("type", "PRE_AUTH")
                .claim("scope", enrollment ? "ENROLL" : "AUTH")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(key())
                .compact();
    }

    public String generatePasswordReset(Long userId, String email) {
        long ttlMs = 10 * 60_000L;
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(email)
                .claim("userId", userId)
                .claim("type", "PRE_AUTH")
                .claim("scope", "PASSWORD_RESET")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(key())
                .compact();
    }

    public String scope(Claims c) {
        return c.get("scope", String.class);
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload();
    }

    public boolean isPreAuth(Claims c) {
        return "PRE_AUTH".equals(c.get("type", String.class));
    }

    public Long userId(Claims c) {
        return c.get("userId", Long.class);
    }
    public String email(Claims c) {
        return c.getSubject();
    }

    public String jti(Claims c) {
        return c.getId();
    }

    public LocalDateTime expiresAt(Claims c) {
        Date exp = c.getExpiration();
        return exp == null
                ? LocalDateTime.now()
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(exp.getTime()), ZoneId.systemDefault());
    }

    public String validateAndGetEmail(String token) {
        try {
            Claims c = parse(token);
            if (!isPreAuth(c)) return null;
            return c.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public Claims validatePasswordReset(String token) {
        Claims c = parse(token);
        if (!isPreAuth(c) || !"PASSWORD_RESET".equals(scope(c))) {
            throw new IllegalStateException("Недействительный токен смены пароля");
        }
        return c;
    }
}