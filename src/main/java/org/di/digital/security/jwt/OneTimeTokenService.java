package org.di.digital.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OneTimeTokenService {

    private static final String KEY_PREFIX = "preauth:used:";

    private final StringRedisTemplate redisTemplate;

    public boolean markUsed(String jti, LocalDateTime tokenExpiresAt) {
        Duration ttl = Duration.between(LocalDateTime.now(), tokenExpiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofSeconds(1);
        }
        Boolean set = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + jti, marker(), ttl);
        return Boolean.TRUE.equals(set);
    }

    private String marker() {
        return String.valueOf(System.currentTimeMillis());
    }
}
