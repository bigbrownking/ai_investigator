package org.di.digital.security.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Component
public class RsaDecryptor {
    private PrivateKey privateKey;
    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("private.pem");
            String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            String key = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(key);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);

            this.privateKey = KeyFactory.getInstance("RSA").generatePrivate(spec);

            log.info("RSA private key loaded successfully");

        } catch (Exception e) {
            log.error("Failed to load RSA private key", e);
            throw new IllegalStateException("RSA key initialization failed");
        }
    }

    public String decrypt(String encrypted) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("RSA decryption failed", e);
        }
    }
}
