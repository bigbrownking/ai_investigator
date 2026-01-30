package org.di.digital.util;

import lombok.RequiredArgsConstructor;
import org.di.digital.service.LogService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class LogCleanupScheduler {

    private final LogService logService;
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldLogs() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
        logService.deleteOldLogs(cutoffDate);
    }
}
