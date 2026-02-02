package org.di.digital.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.DocumentProcessingMessage;
import org.di.digital.model.CaseFile;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.TaskQueue;
import org.di.digital.repository.CaseFileRepository;
import org.di.digital.service.impl.DocumentQueueService;
import org.di.digital.service.impl.NotificationService;
import org.di.digital.service.impl.TaskQueueService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoundRobinScheduler {

    private final TaskQueueService taskQueueService;
    private final DocumentQueueService documentQueueService;
    private final CaseFileRepository caseFileRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processTasksRoundRobin() {
        TaskQueue task = taskQueueService.getNextTaskByRoundRobin();

        if (task != null) {
            log.info("Processing task {} (fileId: {}) for user {} in case {}",
                    task.getFileName(),
                    task.getCaseFileId(),
                    task.getUserEmail(),
                    task.getCaseNumber());

            CaseFile caseFile = caseFileRepository.findById(task.getCaseFileId())
                    .orElseThrow(() -> new RuntimeException("CaseFile not found: " + task.getCaseFileId()));

            caseFile.setStatus(CaseFileStatusEnum.PENDING);
            caseFileRepository.save(caseFile);

            try {
                DocumentProcessingMessage message = DocumentProcessingMessage.builder()
                        .caseId(task.getCaseId())
                        .caseFileId(task.getCaseFileId())
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
                        task.getFileName(),
                        task.getCaseFileId(),
                        task.getCaseNumber());

            } catch (Exception e) {
                log.error("Error processing task {} (fileId: {}) in case {}: {}",
                        task.getFileName(),
                        task.getCaseFileId(),
                        task.getCaseNumber(),
                        e.getMessage(),
                        e);

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
}