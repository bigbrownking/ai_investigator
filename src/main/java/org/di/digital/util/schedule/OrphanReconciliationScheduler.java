package org.di.digital.util.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.service.impl.queue.TaskQueueService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanReconciliationScheduler {

    private final TaskQueueService taskQueueService;


    @Scheduled(
            fixedDelayString = "${scheduler.orphan-reconciliation.delay-seconds}",
            timeUnit = TimeUnit.SECONDS,
            zone = "Asia/Almaty"
    )
    public void reconcile() {
        try {
            TaskQueueService.OrphanCleanupResult result = taskQueueService.reconcileOrphanedTasks(false);
            if (result.orphanedFound() > 0) {
                log.warn("Orphan reconciliation: found={}, deleted={}, ids={}",
                        result.orphanedFound(), result.deleted(), result.orphanedCaseFileIds());
            }
        } catch (Exception e) {
            log.error("Orphan reconciliation failed", e);
        }
    }

    @Scheduled(
            fixedDelayString = "${scheduler.stuck-task.delay-seconds}",
            timeUnit = TimeUnit.SECONDS,
            zone = "Asia/Almaty"
    )
    public void resetStuck() {
        try {
            int n = taskQueueService.resetStuckProcessingTasks();
            if (n > 0) log.warn("Stuck-task reset: {} tasks returned to PENDING", n);
        } catch (Exception e) {
            log.error("Stuck-task reset failed", e);
        }
    }
}