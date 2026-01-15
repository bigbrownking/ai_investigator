package org.di.digital.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.config.RabbitMQConfig;
import org.di.digital.dto.message.ProcessingResultMessage;
import org.di.digital.model.CaseFile;
import org.di.digital.model.CaseFileStatusEnum;
import org.di.digital.repository.CaseFileRepository;
import org.di.digital.service.CaseFileService;
import org.di.digital.service.impl.NotificationService;
import org.di.digital.service.impl.TaskQueueService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingResultConsumer {

    private final CaseFileService caseFileService;
    private final NotificationService notificationService;
    private final CaseFileRepository caseFileRepository;
    private final TaskQueueService taskQueueService;

    @RabbitListener(queues = RabbitMQConfig.RESULT_QUEUE)
    public void handleProcessingResult(ProcessingResultMessage message) {
        log.info("Received {} notification for file {} from user {}",
                message.getStatus(), message.getCaseFileId(), message.getUserEmail());

        try {
            CaseFile caseFile = caseFileRepository.findById(message.getCaseFileId())
                    .orElseThrow(() -> new RuntimeException("CaseFile not found: " + message.getCaseFileId()));

            switch (message.getStatus()) {
                case PROCESSING -> handleProcessing(message, caseFile);
                case COMPLETED -> handleCompletion(message);
                case FAILED -> handleFailure(message);
            }
        } catch (Exception e) {
            log.error("Error handling processing result: {}", e.getMessage(), e);
        }
    }

    private void handleProcessing(ProcessingResultMessage message, CaseFile caseFile) {
        caseFile.setStatus(CaseFileStatusEnum.PROCESSING);
        caseFileRepository.save(caseFile);

        notificationService.sendNotification(
                message.getUserEmail(),
                message.getCaseNumber(),
                caseFile,
                "Начата обработка файла",
                null
        );

        log.info("File {} marked as PROCESSING", message.getCaseFileId());
    }

    private void handleCompletion(ProcessingResultMessage message) {
        CaseFile caseFile = caseFileService.markAsCompleted(message.getCaseFileId(), message.getResult());

        notificationService.sendNotification(
                message.getUserEmail(),
                message.getCaseNumber(),
                caseFile,
                message.getResult(),
                null
        );

        log.info("File {} marked as COMPLETED ({}s)",
                message.getCaseFileId(), message.getProcessingDurationSeconds());
    }

    private void handleFailure(ProcessingResultMessage message) {
        CaseFile caseFile = caseFileService.markAsFailed(message.getCaseFileId(), message.getErrorMessage());

        notificationService.sendNotification(
                message.getUserEmail(),
                message.getCaseNumber(),
                caseFile,
                null,
                message.getErrorMessage()
        );

        log.error("File {} marked as FAILED ({}s): {}",
                message.getCaseFileId(), message.getProcessingDurationSeconds(), message.getErrorMessage());
    }
}