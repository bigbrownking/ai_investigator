package org.di.digital.util;

import lombok.RequiredArgsConstructor;
import org.di.digital.model.enums.TaskStatus;
import org.di.digital.repository.queue.TaskQueueRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
@Component
@RequiredArgsConstructor
public class Scheduler {

    @Value("${old.log.cleanup}")
    private int cleanup;

    private final TaskQueueRepository taskQueueRepository;
    @Scheduled(cron = "0 0 2 * * ?") // Каждый день в 2 ночи
    public void cleanupOldTasks() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(cleanup);
        taskQueueRepository.deleteByStatusAndCompletedAtBefore(
                TaskStatus.COMPLETED, cutoffDate
        );
    }
}
