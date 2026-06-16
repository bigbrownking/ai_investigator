package org.di.digital.util.schedule;

import lombok.RequiredArgsConstructor;
import org.di.digital.service.LogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class LogCleanupScheduler {

    @Value("${old.log.cleanup}")
    private int cleanup;

    private final LogService logService;
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldLogs() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(cleanup);
        logService.deleteOldLogs(cutoffDate);
    }
}
