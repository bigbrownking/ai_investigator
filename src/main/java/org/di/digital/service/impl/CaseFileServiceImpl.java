package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.CaseFileService;
import org.di.digital.service.impl.core.NotificationService;
import org.di.digital.service.impl.queue.TaskQueueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.di.digital.util.requests.UserUtil.validateUserAccess;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseFileServiceImpl implements CaseFileService {
    private final CaseFileRepository caseFileRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final TaskQueueService taskQueueService;
    private final NotificationService notificationService;

    @Override
    public CaseFile markAsCompleted(Long caseFileId, String result, Long processingDurationSeconds) {
        CaseFile caseFile = caseFileRepository.findById(caseFileId)
                .orElseThrow(() -> new IllegalStateException("Файл не найден: " + caseFileId));

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
                .orElseThrow(() -> new IllegalStateException("Файл не найден: " + caseFileId));

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
                .orElseThrow(() -> new IllegalStateException("Файл не найден: " + caseFileId));

        caseFile.setStatus(CaseFileStatusEnum.PROCESSING);
        caseFile.setCompletedAt(LocalDateTime.now());

        caseFileRepository.save(caseFile);

        log.info("File {} marked as FAILED", caseFileId);
    }
    @Override
    @Transactional
    public void retryFile(Long caseId, Long caseFileId, String email) {
        CaseFile caseFile = caseFileRepository.findById(caseFileId)
                .orElseThrow(() -> new IllegalStateException("Файл не найден: " + caseFileId));

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

    @Transactional
    public void setQualification(Long caseId, Long fileId, boolean isQualification, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalStateException("Case not found: " + caseId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));
        validateUserAccess(caseEntity, user);

        CaseFile file = caseEntity.getFiles().stream()
                .filter(f -> f.getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("File not found: " + fileId));

        file.setQualification(isQualification);
        caseFileRepository.save(file);

        log.info("File {} in case {} marked as qualification={}", fileId, caseId, isQualification);
    }
}
