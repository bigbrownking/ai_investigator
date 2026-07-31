package org.di.digital.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;


@Component
public class PreAuthTokenUtil {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId, String email, boolean enrollment) {
        long ttlMs = (enrollment ? 10 : 5) * 60_000L;
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("type", "PRE_AUTH")
                .claim("scope", enrollment ? "ENROLL" : "AUTH")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(key())
                .compact();
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
}