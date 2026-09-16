package org.di.digital.service.impl.core;

import lombok.RequiredArgsConstructor;
import org.apache.tika.exception.TikaException;
import org.di.digital.exception.message.IllegalStateMessage;
import org.di.digital.model.enums.MessageConstant;
import org.di.digital.model.file.FileAccessToken;
import org.di.digital.service.core.FileTokenService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.apache.tika.parser.microsoft.onenote.fsshttpb.util.SequenceNumberGenerator.getCurrentToken;
import static org.di.digital.util.requests.UserUtil.getCurrentLang;

@Service
@RequiredArgsConstructor
public class FileTokenServiceImpl implements FileTokenService {

    private static final String PREFIX = "file:token:";
    private static final long TTL_MINUTES = 60;

    private final RedisTemplate<String, Object> redisTemplate;

    public String generateToken(FileAccessToken token) {
        String tokenId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                PREFIX + tokenId,
                token,
                TTL_MINUTES,
                TimeUnit.MINUTES
        );
        return tokenId;
    }

    public FileAccessToken resolveToken(String tokenId) {
        Object value = redisTemplate.opsForValue().get(PREFIX + tokenId);
        if (value == null) {
            throw new IllegalStateException(MessageConstant.TOKEN_INCORRECT.format(getCurrentLang()));
        }
        return (FileAccessToken) value;
    }
}