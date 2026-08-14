package org.di.digital.security.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Component
public class FileCipher {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    @Value("${minio.encryption.enabled:true}")
    private boolean enabled;

    @Value("${minio.encryption.key:}")
    private String encodedKey;

    private SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.warn("MinIO file encryption is disabled");
            return;
        }
        if (encodedKey == null || encodedKey.isBlank()) {
            log.error("MinIO encryption is enabled but minio.encryption.key is not set");
            throw new IllegalStateException("MINIO_ENC_KEY must be set when minio.encryption.enabled=true");
        }
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey.trim());
        if (keyBytes.length != 32) {
            throw new IllegalStateException("MINIO_ENC_KEY must be a Base64-encoded 32-byte key (openssl rand -base64 32)");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
        log.info("MinIO file encryption initialized (AES-256-GCM)");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEncryptedName(String objectName) {
        return objectName != null && objectName.endsWith(".enc");
    }

    public byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain);
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt file", e);
        }
    }

    public byte[] decrypt(byte[] stored) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(stored, 0, iv, 0, iv.length);
            byte[] encrypted = new byte[stored.length - iv.length];
            System.arraycopy(stored, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt file", e);
        }
    }
}