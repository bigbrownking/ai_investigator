package org.di.digital;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.service.impl.MinioService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@Slf4j
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class DigitalApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigitalApplication.class, args);
    }

    @Component
    @RequiredArgsConstructor
    static class StartupRunner implements ApplicationRunner {

        private final MinioService minioService;

        @Override
        public void run(ApplicationArguments args) {
            log.info("PREVIEW URL IS {}", minioService.generatePresignedUrlForPreview("cases/002/088c954c-53a2-4f1f-a319-db19124dd6ca.pdf"));
            log.info("DOWNLOAD URL IS {}", minioService.generatePresignedUrlForDownload("cases/002/088c954c-53a2-4f1f-a319-db19124dd6ca.pdf", "\uD83D\uDCD8 Финальный проект.pdf"));
        }
    }
}