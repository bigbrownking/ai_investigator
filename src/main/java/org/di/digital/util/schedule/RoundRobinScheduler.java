package org.di.digital.util.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.DocumentProcessingMessage;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.queue.TaskQueue;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.service.impl.queue.DocumentQueueService;
import org.di.digital.service.impl.core.NotificationService;
import org.di.digital.service.impl.queue.TaskQueueService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoundRobinScheduler {

    private final TaskQueueService taskQueueService;
    private final DocumentQueueService documentQueueService;
    private final CaseFileRepository caseFileRepository;
    private final NotificationService notificationService;

    @Value("${scheduler.round-robin.max-concurrent}")
    private int maxConcurrent;

    @Scheduled(fixedDelayString = "${scheduler.round-robin.delay-seconds}", timeUnit = java.util.concurrent.TimeUnit.SECONDS, zone = "Asia/Almaty")
    @Transactional
    public void processTasksRoundRobin() {
        long processingCount = taskQueueService.getProcessingTasksCount();
        int freeSlots = (int) (maxConcurrent - processingCount);
        if (freeSlots <= 0) {
            return;
        }

        for (int i = 0; i < freeSlots; i++) {
            List<Long> excludedCaseIds = taskQueueService.getProcessingCaseIds();
            TaskQueue task = taskQueueService.getNextTaskByRoundRobin(excludedCaseIds);
            if (task == null) {
                break;
            }
            dispatchTask(task);
        }
    }

    private void dispatchTask(TaskQueue task) {
        log.info("Processing task {} (fileId: {}) for user {} in case {}",
                task.getFileName(), task.getCaseFileId(), task.getUserEmail(), task.getCaseNumber());

        CaseFile caseFile = caseFileRepository.findById(task.getCaseFileId())
                .orElseThrow(() -> new IllegalStateException("Файл не найден: " + task.getCaseFileId()));

        caseFile.setStatus(CaseFileStatusEnum.PENDING);
        caseFileRepository.save(caseFile);

        try {
            DocumentProcessingMessage message = DocumentProcessingMessage.builder()
                    .caseId(task.getCaseId())
                    .caseFileId(task.getCaseFileId())
                    .language(task.getLanguage())
                    .fileUrl(task.getFileUrl())
                    .originalFileName(task.getFileName())
                    .userEmail(task.getUserEmail())
                    .caseNumber(task.getCaseNumber())
                    .build();

            documentQueueService.sendDocumentForProcessing(message);

            notificationService.sendCaseNotificationToAllUsers(
                    task.getCaseNumber(),
                    "Файл добавлен в очередь обработки: " + task.getFileName(),
                    task.getCaseFileId(),
                    task.getFileName()
            );

            log.info("Task {} (fileId: {}) sent to processing queue for case {}",
                    task.getFileName(), task.getCaseFileId(), task.getCaseNumber());

        } catch (Exception e) {
            log.error("Error processing task {} (fileId: {}) in case {}: {}",
                    task.getFileName(), task.getCaseFileId(), task.getCaseNumber(), e.getMessage(), e);

            taskQueueService.failTask(task.getCaseFileId(), e.getMessage());

            caseFile.setStatus(CaseFileStatusEnum.FAILED);
            caseFileRepository.save(caseFile);

            notificationService.sendCaseNotificationToAllUsers(
                    task.getCaseNumber(),
                    "Ошибка постановки файла в очередь: " + task.getFileName() + " - " + e.getMessage(),
                    task.getCaseFileId(),
                    task.getFileName()
            );
        }
    }
}