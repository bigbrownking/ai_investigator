package org.di.digital.util.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.enums.TaskStatus;
import org.di.digital.repository.queue.TaskQueueRepository;
import org.di.digital.service.impl.queue.TaskQueueService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCleanupScheduler {

    @Value("${old.log.cleanup}")
    private int cleanup;

    private final TaskQueueRepository taskQueueRepository;
    private final TaskQueueService taskQueueService;


    @Scheduled(cron = "${scheduler.task.cleanup}", zone = "Asia/Almaty")
    public void cleanupOldTasks() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(cleanup);
        taskQueueRepository.deleteByStatusAndCompletedAtBefore(
                TaskStatus.COMPLETED, cutoffDate
        );
    }
    @Scheduled(cron = "${scheduler.round-robin-state.cleanup}", zone = "Asia/Almaty")
    public void pruneRoundRobinState() {
        try {
            taskQueueService.pruneCasePointers();
        } catch (Exception e) {
            log.error("Round-robin state prune failed", e);
        }
    }
}
