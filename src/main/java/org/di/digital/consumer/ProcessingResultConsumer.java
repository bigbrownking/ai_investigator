package org.di.digital.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.config.RabbitMQConfig;
import org.di.digital.dto.message.ProcessingResultMessage;
import org.di.digital.model.CaseFile;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.repository.CaseFileRepository;
import org.di.digital.service.CaseFileService;
import org.di.digital.service.impl.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingResultConsumer {

    private final CaseFileService caseFileService;
    private final NotificationService notificationService;
    private final CaseFileRepository caseFileRepository;

    @RabbitListener(queues = RabbitMQConfig.RESULT_QUEUE)
    public void handleProcessingResult(ProcessingResultMessage message) {
        log.info("Received {} notification for file {} in case {}",
                message.getStatus(), message.getCaseFileId(), message.getCaseNumber());

        try {
            CaseFile caseFile = caseFileRepository.findById(message.getCaseFileId())
                    .orElseThrow(() -> new RuntimeException("CaseFile not found: " + message.getCaseFileId()));

            switch (message.getStatus()) {
                case PENDING -> handlePending(message, caseFile);
                case PROCESSING -> handleProcessing(message, caseFile);
                case COMPLETED -> handleCompletion(message);
                case FAILED -> handleFailure(message);
            }
        } catch (Exception e) {
            log.error("Error handling processing result: {}", e.getMessage(), e);
        }
    }
    private void handlePending(ProcessingResultMessage message, CaseFile caseFile) {
        caseFile.setStatus(CaseFileStatusEnum.PENDING);
        caseFileRepository.save(caseFile);

        notificationService.notifyFilePending(message.getCaseNumber(), caseFile);

        log.info("File {} marked as PENDING in case {}", message.getCaseFileId(), message.getCaseNumber());
    }

    private void handleProcessing(ProcessingResultMessage message, CaseFile caseFile) {
        caseFile.setStatus(CaseFileStatusEnum.PROCESSING);
        caseFileRepository.save(caseFile);

        notificationService.notifyFileProcessingStarted(message.getCaseNumber(), caseFile);

        log.info("File {} marked as PROCESSING in case {}", message.getCaseFileId(), message.getCaseNumber());
    }

    private void handleCompletion(ProcessingResultMessage message) {
        CaseFile caseFile = caseFileService.markAsCompleted(message.getCaseFileId(), message.getResult());

        // Send case-level notification
        notificationService.notifyFileProcessingCompleted(
                message.getCaseNumber(),
                caseFile,
                message.getResult()
        );

        log.info("File {} marked as COMPLETED in case {} ({}s)",
                message.getCaseFileId(), message.getCaseNumber(), message.getProcessingDurationSeconds());
    }

    private void handleFailure(ProcessingResultMessage message) {
        CaseFile caseFile = caseFileService.markAsFailed(message.getCaseFileId(), message.getErrorMessage());

        // Send case-level notification
        notificationService.notifyFileProcessingFailed(
                message.getCaseNumber(),
                caseFile,
                message.getErrorMessage()
        );

        log.error("File {} marked as FAILED in case {} ({}s): {}",
                message.getCaseFileId(), message.getCaseNumber(),
                message.getProcessingDurationSeconds(), message.getErrorMessage());
    }
}