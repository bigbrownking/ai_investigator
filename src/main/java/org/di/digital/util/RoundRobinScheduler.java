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
            log.info("Processing task {} for user {}", task.getFileName(), task.getUserEmail());
            CaseFile caseFile = caseFileRepository.findById(task.getCaseFileId()).orElseThrow();

            caseFile.setStatus(CaseFileStatusEnum.PENDING);
            try {
                // Отправить в RabbitMQ
                DocumentProcessingMessage message = DocumentProcessingMessage.builder()
                        .caseId(task.getCaseId())
                        .caseFileId(task.getCaseFileId())
                        .fileUrl(task.getFileUrl())
                        .originalFileName(task.getFileName())
                        .userEmail(task.getUserEmail())
                        .caseNumber(task.getCaseNumber())
                        .build();

                notificationService.sendNotification(
                        task.getUserEmail(),
                        task.getCaseNumber(),
                        caseFile,
                        "Файл добавлен в очередь обработки",
                        null
                );
                documentQueueService.sendDocumentForProcessing(message);
            } catch (Exception e) {
                log.error("Error processing task: {}", e.getMessage());
                taskQueueService.failTask(task.getCaseFileId(), e.getMessage());
            }
        }
    }
}