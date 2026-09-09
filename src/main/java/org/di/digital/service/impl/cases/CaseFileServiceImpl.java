package org.di.digital.service.impl.cases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.AssessmentResult;
import org.di.digital.dto.message.ClassificationResult;
import org.di.digital.exception.NotFoundException;
import org.di.digital.exception.message.NotFoundMessage;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.enums.file.CaseFileStatusEnum;
import org.di.digital.model.enums.permission.CaseAction;
import org.di.digital.model.enums.permission.CaseModule;
import org.di.digital.model.enums.settings.UserSettingsLanguage;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.cases.CaseAccessService;
import org.di.digital.service.cases.CaseFileService;
import org.di.digital.service.impl.core.NotificationService;
import org.di.digital.service.impl.queue.TaskQueueService;
import org.di.digital.util.requests.UserUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.di.digital.util.requests.UserUtil.getCurrentLang;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseFileServiceImpl implements CaseFileService {
    private final CaseFileRepository caseFileRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final TaskQueueService taskQueueService;
    private final NotificationService notificationService;
    private final CaseAccessService caseAccessService;
    private final UserUtil userUtil;

    @Override
    @Transactional
    public CaseFile markAsCompleted(Long caseFileId, String result, Long processingDurationSeconds,
                                    ClassificationResult classification, AssessmentResult assessment) {
        CaseFile caseFile = caseFileRepository.findById(caseFileId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.FILE.localized(currentLang(), caseFileId.toString())));

        caseFile.setStatus(CaseFileStatusEnum.COMPLETED);
        caseFile.setCompletedAt(LocalDateTime.now());
        caseFile.setProcessingDurationSeconds(processingDurationSeconds);

        if (classification != null) {
            caseFile.setClassificationStatus(classification.getStatus());
            caseFile.setDocumentType(classification.getDocumentType());
        }
        if (assessment != null) {
            caseFile.setAssessmentStatus(assessment.getStatus());
            caseFile.setScorePercent(assessment.getScorePercent());
            caseFile.setAssessmentColor(assessment.getColor());
            caseFile.setAssessmentSummary(assessment.getSummary());
        }

        caseFileRepository.save(caseFile);
        taskQueueService.completeTask(caseFileId, processingDurationSeconds);

        log.info("File {} marked as COMPLETED", caseFileId);
        return caseFile;
    }

    @Override
    public CaseFile markAsFailed(Long caseFileId, String errorMessage, Long processingDurationSeconds) {
        CaseFile caseFile = caseFileRepository.findById(caseFileId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.FILE.localized(currentLang(), caseFileId.toString())));

        caseFile.setStatus(CaseFileStatusEnum.FAILED);
        caseFile.setCompletedAt(LocalDateTime.now());
        caseFile.setProcessingDurationSeconds(processingDurationSeconds);

        caseFileRepository.save(caseFile);
        taskQueueService.failTask(caseFileId, errorMessage);
        return caseFile;
    }
    @Override
    @Transactional
    public void retryFile(Long caseId, Long caseFileId, String email) {
        CaseFile caseFile = caseFileRepository.findById(caseFileId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.FILE.localized(currentLang(), email)));

        if (!CaseFileStatusEnum.FAILED.equals(caseFile.getStatus())) {
            throw new IllegalStateException("Повторная обработка доступна только для файлов со статусом ОШИБКА");
        }

        caseFile.setStatus(CaseFileStatusEnum.QUEUED);
        caseFile.setCompletedAt(null);
        caseFileRepository.save(caseFile);

        notificationService.notifyFileQueued(caseFile.getCaseEntity().getNumber(), caseFile);

        String language = caseFile.getCaseEntity().getLanguage();
        taskQueueService.retryTask(
                caseFileId,
                email,
                caseId,
                caseFile.getCaseEntity().getNumber(),
                caseFile.getOriginalFileName(),
                caseFile.getFileUrl(),
                language
        );

        log.info("File {} re-queued for processing by user: {}", caseFileId, email);
    }

    @Transactional
    public void setQualification(Long caseId, Long fileId, boolean isQualification, String email) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.CASE.localized(currentLang(), caseId.toString())));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.USER.localized(currentLang(), email)));
        userUtil.validateUserAccess(caseEntity, user);
        caseAccessService.require(caseEntity, user, CaseModule.DOCUMENTS, CaseAction.UPDATE);

        CaseFile file = caseEntity.getFiles().stream()
                .filter(f -> f.getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(NotFoundMessage.FILE.localized(currentLang(), fileId.toString())));

        file.setQualification(isQualification);
        caseFileRepository.save(file);

        log.info("File {} in case {} marked as qualification={}", fileId, caseId, isQualification);
    }

    private UserSettingsLanguage currentLang(){
        return getCurrentLang();
    }
}
