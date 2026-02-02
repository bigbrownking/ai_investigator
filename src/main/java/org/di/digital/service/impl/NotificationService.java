package org.di.digital.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.notification.CaseProcessingNotification;
import org.di.digital.dto.notification.FileStatusInfo;
import org.di.digital.model.CaseFile;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.repository.CaseFileRepository;
import org.di.digital.repository.CaseRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final CaseRepository caseRepository;
    private final CaseFileRepository caseFileRepository;

    @Transactional(readOnly = true)
    public void sendCaseNotificationToAllUsers(String caseNumber, String activity, Long activityFileId, String activityFileName) {
        Set<String> userEmails = caseRepository.findAllAccessibleUserEmailsByCaseNumber(caseNumber);

        if (userEmails.isEmpty()) {
            log.warn("No users found with access to case: {}", caseNumber);
            return;
        }

        List<CaseFile> caseFiles = caseFileRepository.findByCaseEntityNumber(caseNumber);

        List<FileStatusInfo> fileStatuses = caseFiles.stream()
                .map(file -> FileStatusInfo.builder()
                        .fileId(file.getId())
                        .fileName(file.getOriginalFileName())
                        .status(file.getStatus())
                        .uploadedAt(file.getUploadedAt())
                        .completedAt(file.getCompletedAt())
                        .errorMessage(null)
                        .build())
                .collect(Collectors.toList());

        long pending = caseFiles.stream()
                .filter(f -> f.getStatus() == CaseFileStatusEnum.PENDING)
                .count();

        long processing = caseFiles.stream()
                .filter(f -> f.getStatus() == CaseFileStatusEnum.PROCESSING)
                .count();

        long completed = caseFiles.stream()
                .filter(f -> f.getStatus() == CaseFileStatusEnum.COMPLETED)
                .count();

        long failed = caseFiles.stream()
                .filter(f -> f.getStatus() == CaseFileStatusEnum.FAILED)
                .count();

        CaseProcessingNotification notification = CaseProcessingNotification.builder()
                .caseNumber(caseNumber)
                .caseTitle(getCaseTitle(caseNumber))
                .totalFiles(caseFiles.size())
                .pendingFiles((int) pending)
                .processingFiles((int) processing)
                .completedFiles((int) completed)
                .failedFiles((int) failed)
                .files(fileStatuses)
                .latestActivity(activity)
                .latestFileId(activityFileId)
                .latestFileName(activityFileName)
                .timestamp(LocalDateTime.now())
                .build();

        String destination = buildCaseDestination(caseNumber);

        log.info("Sending case notification to {} users for case {} (activity: {})",
                userEmails.size(), caseNumber, activity);

        for (String userEmail : userEmails) {
            messagingTemplate.convertAndSendToUser(userEmail, destination, notification);
            log.debug("Case notification sent to user: {}", userEmail);
        }

        log.info("Case notification sent to all {} users for case {} - Files: {} total, {} processing, {} completed, {} failed",
                userEmails.size(), caseNumber, caseFiles.size(), processing, completed, failed);
    }
    @Transactional(readOnly = true)
    public void notifyFileProcessingStarted(String caseNumber, CaseFile caseFile) {
        sendCaseNotificationToAllUsers(
                caseNumber,
                "Начата обработка файла: " + caseFile.getOriginalFileName(),
                caseFile.getId(),
                caseFile.getOriginalFileName()
        );
    }
    @Transactional(readOnly = true)
    public void notifyFileProcessingCompleted(String caseNumber, CaseFile caseFile, String result) {
        sendCaseNotificationToAllUsers(
                caseNumber,
                "Обработка завершена: " + caseFile.getOriginalFileName() + " - " + result,
                caseFile.getId(),
                caseFile.getOriginalFileName()
        );
    }
    @Transactional(readOnly = true)
    public void notifyFileProcessingFailed(String caseNumber, CaseFile caseFile, String errorMessage) {
        sendCaseNotificationToAllUsers(
                caseNumber,
                "Ошибка обработки файла: " + caseFile.getOriginalFileName() + " - " + errorMessage,
                caseFile.getId(),
                caseFile.getOriginalFileName()
        );
    }
    private String buildCaseDestination(String caseNumber) {
        return String.format("/queue/case/%s/status", caseNumber);
    }
    private String getCaseTitle(String caseNumber) {
        return caseRepository.findByNumber(caseNumber)
                .map(c -> c.getTitle())
                .orElse("Unknown Case");
    }
}