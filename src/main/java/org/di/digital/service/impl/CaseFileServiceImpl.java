package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.CaseFile;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.repository.CaseFileRepository;
import org.di.digital.service.CaseFileService;
import org.di.digital.service.impl.queue.TaskQueueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseFileServiceImpl implements CaseFileService {
    private final CaseFileRepository caseFileRepository;
    private final TaskQueueService taskQueueService;
    private final NotificationService notificationService;

    @Override
    public CaseFile markAsCompleted(Long caseFileId, String result, Long processingDurationSeconds) {
        CaseFile caseFile = caseFileRepository.findById(caseFileId)
                .orElseThrow(() -> new RuntimeException("Файл не найден: " + caseFileId));

        caseFile.setStatus(CaseFileStatusEnum.COMPLETED);
        caseFile.setCompletedAt(LocalDateTime.now());

        caseFileRepository.save(caseFile);

        taskQueueService.completeTask(caseFileId, processingDurationSeconds);

        log.info("File {} marked as COMPLETED", caseFileId);
        return caseFile;
    }

    @Override
    public CaseFile markAsFailed(Long caseFileId, String errorMessage) {
        CaseFile caseFile = caseFileRepository.findById(caseFileId)
                .orElseThrow(() -> new RuntimeException("Файл не найден: " + caseFileId));

        caseFile.setStatus(CaseFileStatusEnum.FAILED);
        caseFile.setCompletedAt(LocalDateTime.now());

        caseFileRepository.save(caseFile);

        taskQueueService.failTask(caseFileId, errorMessage);

        log.info("File {} marked as FAILED", caseFileId);
        return caseFile;
    }

    @Override
    public void markAsProcessing(Long caseFileId) {
        CaseFile caseFile = caseFileRepository.findById(caseFileId)
                .orElseThrow(() -> new RuntimeException("Файл не найден: " + caseFileId));

        caseFile.setStatus(CaseFileStatusEnum.PROCESSING);
        caseFile.setCompletedAt(LocalDateTime.now());

        caseFileRepository.save(caseFile);

        log.info("File {} marked as FAILED", caseFileId);
    }
    @Override
    @Transactional
    public void retryFile(Long caseId, Long caseFileId, String email) {
        CaseFile caseFile = caseFileRepository.findById(caseFileId)
                .orElseThrow(() -> new RuntimeException("Файл не найден: " + caseFileId));

        if (!CaseFileStatusEnum.FAILED.equals(caseFile.getStatus())) {
            throw new IllegalStateException("Повторная обработка доступна только для файлов со статусом ОШИБКА");
        }

        caseFile.setStatus(CaseFileStatusEnum.QUEUED);
        caseFile.setCompletedAt(null);
        caseFileRepository.save(caseFile);

        notificationService.notifyFileQueued(caseFile.getCaseEntity().getNumber(), caseFile);

        taskQueueService.retryTask(
                caseFileId,
                email,
                caseId,
                caseFile.getCaseEntity().getNumber(),
                caseFile.getOriginalFileName(),
                caseFile.getFileUrl()
        );

        log.info("File {} re-queued for processing by user: {}", caseFileId, email);
    }
}
