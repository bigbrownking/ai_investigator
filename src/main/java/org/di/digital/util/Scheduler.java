package org.di.digital.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.TaskStatus;
import org.di.digital.repository.TaskQueueRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
@Slf4j
@Component
@RequiredArgsConstructor
public class Scheduler {
    private final TaskQueueRepository taskQueueRepository;
    @Scheduled(cron = "0 0 2 * * ?") // Каждый день в 2 ночи
    public void cleanupOldTasks() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        taskQueueRepository.deleteByStatusAndCompletedAtBefore(
                TaskStatus.COMPLETED, cutoffDate
        );
    }
}
