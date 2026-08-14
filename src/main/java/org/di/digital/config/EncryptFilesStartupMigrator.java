package org.di.digital.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.security.crypto.FileCipher;
import org.di.digital.util.EncryptFilesMigrationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EncryptFilesStartupMigrator {

    private final FileCipher fileCipher;
    private final EncryptFilesMigrationService migrationService;

    @Value("${minio.encryption.migrate-on-startup:true}")
    private boolean migrateOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!migrateOnStartup || !fileCipher.isEnabled()) {
            return;
        }
        try {
            migrationService.migrate();
        } catch (Exception e) {
            log.error("Encrypt files migration failed on startup", e);
        }
    }
}