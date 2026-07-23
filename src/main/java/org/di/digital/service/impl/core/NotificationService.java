package org.di.digital.service.impl.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.message.OsmotrResultMessage;
import org.di.digital.dto.message.ReportResultMessage;
import org.di.digital.dto.notification.*;
import org.di.digital.dto.response.interrogation.InterrogationTimeStatusResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.enums.InterrogationTimeEvent;
import org.di.digital.model.enums.PlanNotificationType;
import org.di.digital.model.enums.PlanStatus;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.di.digital.model.plan.PlanNotification;
import org.di.digital.model.user.User;
import org.di.digital.repository.cases.CaseFileRepository;
import org.di.digital.repository.cases.CaseRepository;
import org.di.digital.repository.plan.PlanNotificationRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final CaseRepository caseRepository;
    private final CaseFileRepository caseFileRepository;
    private final PlanNotificationRepository planNotificationRepository;

    public void sendNotificationToUser(String userEmail, String message) {
        messagingTemplate.convertAndSendToUser(
                userEmail,
                buildAppealDestination(),
                message
        );
        log.info("Appeal notification sent to user: {}", userEmail);
    }
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
    public void notifyFileQueued(String caseNumber, CaseFile caseFile) {
        sendCaseNotificationToAllUsers(
                caseNumber,
                "Файл повторно добавлен в очередь: " + caseFile.getOriginalFileName(),
                caseFile.getId(),
                caseFile.getOriginalFileName()
        );
    }

    @Transactional(readOnly = true)
    public void notifyFilePending(String caseNumber, CaseFile caseFile) {
        sendCaseNotificationToAllUsers(
                caseNumber,
                "Файл добавлен в очередь: " + caseFile.getOriginalFileName(),
                caseFile.getId(),
                caseFile.getOriginalFileName()
        );
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
    @Transactional(readOnly = true)
    public void notifyInterrogationProcessing(String caseNumber, CaseInterrogation interrogation, Long qaId) {
        sendInterrogationNotificationToAllUsers(
                caseNumber, interrogation, qaId,
                "Транскрипция начата для: " + interrogation.getFio(),
                InterrogationNotificationStatus.PROCESSING, null, null
        );
    }

    @Transactional(readOnly = true)
    public void notifyInterrogationCompleted(String caseNumber, CaseInterrogation interrogation,
                                             Long qaId, String transcribedText) {
        sendInterrogationNotificationToAllUsers(
                caseNumber, interrogation, qaId,
                "Транскрипция завершена для: " + interrogation.getFio(),
                InterrogationNotificationStatus.COMPLETED, transcribedText, null
        );
    }

    @Transactional(readOnly = true)
    public void notifyInterrogationFailed(String caseNumber, CaseInterrogation interrogation,
                                          Long qaId, String errorMessage) {
        sendInterrogationNotificationToAllUsers(
                caseNumber, interrogation, qaId,
                "Ошибка транскрипции для: " + interrogation.getFio(),
                InterrogationNotificationStatus.FAILED, null, errorMessage
        );
    }

    private void sendInterrogationNotificationToAllUsers(String caseNumber,
                                                         CaseInterrogation interrogation,
                                                         Long qaId,
                                                         String activity,
                                                         InterrogationNotificationStatus status,
                                                         String transcribedText,
                                                         String errorMessage) {
        Set<String> userEmails = caseRepository.findAllAccessibleUserEmailsByCaseNumber(caseNumber);
        if (userEmails.isEmpty()) {
            log.warn("No users found with access to case: {}", caseNumber);
            return;
        }

        InterrogationNotification notification = InterrogationNotification.builder()
                .caseNumber(caseNumber)
                .interrogationId(interrogation.getId())
                .qaId(qaId)
                .fio(interrogation.getFio())
                .role(interrogation.getRole())
                .status(status)
                .activity(activity)
                .transcribedText(transcribedText)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();

        String destination = buildInterrogationDestination(caseNumber, interrogation.getId());

        for (String userEmail : userEmails) {
            messagingTemplate.convertAndSendToUser(userEmail, destination, notification);
            log.debug("Interrogation notification sent to user: {}", userEmail);
        }

        log.info("Interrogation notification sent to {} users for interrogation: {} qa: {} ({})",
                userEmails.size(), interrogation.getId(), qaId, status);
    }

    @Transactional(readOnly = true)
    public void notifyOtherInterrogationProcessing(String caseNumber, CaseInterrogation interrogation,
                                                   Long qaId, String fieldName) {
        sendOtherInterrogationNotificationToAllUsers(
                caseNumber, interrogation, qaId,
                "Транскрипция начата для: " + interrogation.getFio(),
                InterrogationNotificationStatus.PROCESSING, null, null, fieldName
        );
    }

    @Transactional(readOnly = true)
    public void notifyOtherInterrogationCompleted(String caseNumber, CaseInterrogation interrogation,
                                             Long qaId, String transcribedText, String fieldName) {
        sendOtherInterrogationNotificationToAllUsers(
                caseNumber, interrogation, qaId,
                "Транскрипция завершена для: " + interrogation.getFio(),
                InterrogationNotificationStatus.COMPLETED, transcribedText, null, fieldName
        );
    }

    @Transactional(readOnly = true)
    public void notifyOtherInterrogationFailed(String caseNumber, CaseInterrogation interrogation,
                                          Long qaId, String errorMessage, String fieldName) {
        sendOtherInterrogationNotificationToAllUsers(
                caseNumber, interrogation, qaId,
                "Ошибка транскрипции для: " + interrogation.getFio(),
                InterrogationNotificationStatus.FAILED, null, errorMessage, fieldName
        );
    }

    private void sendOtherInterrogationNotificationToAllUsers(String caseNumber,
                                                         CaseInterrogation interrogation,
                                                         Long qaId,
                                                         String activity,
                                                         InterrogationNotificationStatus status,
                                                         String transcribedText,
                                                         String errorMessage, String fieldName) {
        Set<String> userEmails = caseRepository.findAllAccessibleUserEmailsByCaseNumber(caseNumber);
        if (userEmails.isEmpty()) {
            log.warn("No users found with access to case: {}", caseNumber);
            return;
        }

        InterrogationNotification notification = InterrogationNotification.builder()
                .caseNumber(caseNumber)
                .interrogationId(interrogation.getId())
                .qaId(qaId)
                .fio(interrogation.getFio())
                .role(interrogation.getRole())
                .status(status)
                .activity(activity)
                .transcribedText(transcribedText)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .fieldName(fieldName)
                .build();

        String destination = buildOtherInterrogationDestination(caseNumber, interrogation.getId());

        for (String userEmail : userEmails) {
            messagingTemplate.convertAndSendToUser(userEmail, destination, notification);
            log.debug("Interrogation notification sent to user: {}", userEmail);
        }

        log.info("Interrogation notification sent to {} users for interrogation: {} qa: {} ({})",
                userEmails.size(), interrogation.getId(), qaId, status);
    }

    public void notifyPlanApproved(Case caseEntity, User approver, int level) {
        PlanStatusNotification notification = PlanStatusNotification.builder()
                .eventId(UUID.randomUUID().toString())
                .type(PlanNotificationType.PLAN_STATUS_CHANGED)
                .caseId(caseEntity.getId())
                .caseNumber(caseEntity.getNumber())
                .caseTitle(caseEntity.getTitle())
                .planStatus(caseEntity.getPlanStatus())
                .approvalLevel(level)
                .reviewerProfession(approver.getProfession().getRuName())
                .reviewerName(approver.getSurname() + " " + approver.getName().charAt(0) + ".")
                .comment(null)
                .timestamp(LocalDateTime.now())
                .build();

        sendPlanNotification(caseEntity.getNumber(), notification);
        if (caseEntity.getOwner() != null) {
            sendGlobalPlanNotification(caseEntity.getOwner().getEmail(), notification);
        }
    }

    public void notifyApproverPlanAwaiting(Case caseEntity, User approver, int level) {
        PlanStatusNotification notification = PlanStatusNotification.builder()
                .eventId(UUID.randomUUID().toString())
                .type(PlanNotificationType.PLAN_STATUS_CHANGED)
                .caseId(caseEntity.getId())
                .caseNumber(caseEntity.getNumber())
                .caseTitle(caseEntity.getTitle())
                .planStatus(caseEntity.getPlanStatus())
                .approvalLevel(level)
                .reviewerName(null)
                .reviewerProfession(null)
                .comment(null)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSendToUser(
                approver.getEmail(),
                buildPlanDestination(caseEntity.getNumber()),
                notification
        );

        sendGlobalPlanNotification(approver.getEmail(), notification);

        log.info("Notified approver {} that plan awaits level {} approval for case {}",
                approver.getEmail(), level, caseEntity.getNumber());
    }
    public void notifyPlanRejected(Case caseEntity, User approver, int level, String comment) {
        PlanStatusNotification notification = PlanStatusNotification.builder()
                .eventId(UUID.randomUUID().toString())
                .type(PlanNotificationType.PLAN_STATUS_CHANGED)
                .caseId(caseEntity.getId())
                .caseNumber(caseEntity.getNumber())
                .caseTitle(caseEntity.getTitle())
                .planStatus(PlanStatus.REJECTED)
                .approvalLevel(level)
                .reviewerProfession(approver.getProfession().getRuName())
                .reviewerName(approver.getSurname() + " " + approver.getName().charAt(0) + ".")
                .comment(comment)
                .timestamp(LocalDateTime.now())
                .build();

        sendPlanNotification(caseEntity.getNumber(), notification);
        if (caseEntity.getOwner() != null) {
            sendGlobalPlanNotification(caseEntity.getOwner().getEmail(), notification);
        }
    }

    public void notifyRedActionDeadline(Case caseEntity, User supervisor, Map<String, Object> action) {
        String actionDesc = (String) action.get("действие");
        int number = ((Number) action.get("номер")).intValue();
        String date = (String) action.get("срок");

        User reviewer = caseEntity.getPlanReviewedBy();

        LocalDate deadline = LocalDate.parse(date, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), deadline);

        String comment = daysUntil < 0
                ? String.format("🔴 Действие №%d просрочено на %d дн. (срок был: %s): %s",
                number, Math.abs(daysUntil), date, actionDesc)
                : String.format("🟡 Действие №%d истекает завтра (срок: %s): %s",
                number, date, actionDesc);

        PlanStatusNotification notification = PlanStatusNotification.builder()
                .eventId(UUID.randomUUID().toString())
                .type(PlanNotificationType.PLAN_ACTION_OVERDUE)
                .caseId(caseEntity.getId())
                .caseNumber(caseEntity.getNumber())
                .caseTitle(caseEntity.getTitle())
                .planStatus(caseEntity.getPlanStatus())
                .approvalLevel(0)
                .reviewerName(reviewer != null
                        ? reviewer.getSurname() + " " + reviewer.getName().charAt(0) + "."
                        : null)
                .reviewerProfession(reviewer != null && reviewer.getProfession() != null
                        ? reviewer.getProfession().getRuName()
                        : null)
                .comment(comment)
                .timestamp(LocalDateTime.now())
                .build();

        String destination = buildPlanDestination(caseEntity.getNumber());
        messagingTemplate.convertAndSendToUser(supervisor.getEmail(), destination, notification);

        sendGlobalPlanNotification(supervisor.getEmail(), notification);

        log.info("Red action deadline notification sent to supervisor {} for case {}, action #{}",
                supervisor.getEmail(), caseEntity.getNumber(), number);
    }

    private void sendPlanNotification(String caseNumber, PlanStatusNotification notification) {
        Set<String> userEmails = caseRepository
                .findAllAccessibleUserEmailsByCaseNumber(caseNumber);

        String destination = buildPlanDestination(caseNumber);

        for (String email : userEmails) {
            messagingTemplate.convertAndSendToUser(email, destination, notification);
            log.debug("Plan status notification sent to user: {}", email);
        }

        log.info("Plan notification [{}] sent to {} users for case {}",
                notification.getPlanStatus(), userEmails.size(), caseNumber);
    }
    private void sendGlobalPlanNotification(String userEmail, PlanStatusNotification notification) {
        savePlanNotification(userEmail, notification);
        messagingTemplate.convertAndSendToUser(
                userEmail,
                buildGlobalPlanDestination(),
                notification
        );
        log.debug("Global plan notification [{}] sent to user: {}", notification.getPlanStatus(), userEmail);
    }
    @Transactional(readOnly = true)
    public void sendInterrogationTimeNotification(String caseNumber,
                                                  CaseInterrogation interrogation,
                                                  InterrogationTimeEvent event,
                                                  String message,
                                                  InterrogationTimeStatusResponse status) {
        Set<String> userEmails = caseRepository.findAllAccessibleUserEmailsByCaseNumber(caseNumber);
        if (userEmails.isEmpty()) {
            log.warn("No users found with access to case: {}", caseNumber);
            return;
        }

        InterrogationTimeNotification notification = InterrogationTimeNotification.builder()
                .caseNumber(caseNumber)
                .interrogationId(interrogation.getId())
                .fio(interrogation.getFio())
                .event(event)
                .message(message)
                .continuousSeconds(status.getContinuousSeconds())
                .dailySeconds(status.getDailySeconds())
                .profile(status.getProfile())
                .continuousWarn(status.isContinuousWarn())
                .continuousLimitReached(status.isContinuousLimitReached())
                .dailyWarn(status.isDailyWarn())
                .dailyLimitReached(status.isDailyLimitReached())
                .onBreak(status.isOnBreak())
                .breakOver(status.isBreakOver())
                .breakRemainingSeconds(status.getBreakRemainingSeconds())
                .timestamp(LocalDateTime.now())
                .build();

        String destination = buildInterrogationTimeDestination(caseNumber, interrogation.getId());

        for (String userEmail : userEmails) {
            messagingTemplate.convertAndSendToUser(userEmail, destination, notification);
        }

        log.info("Time notification [{}] sent to {} users for interrogation {} in case {}",
                event, userEmails.size(), interrogation.getId(), caseNumber);
    }

    private String buildInterrogationTimeDestination(String caseNumber, Long interrogationId) {
        return String.format("/queue/case/%s/interrogation/%d/time", caseNumber, interrogationId);
    }

    private String buildInterrogationDestination(String caseNumber, Long interrogationId) {
        return String.format("/queue/case/%s/interrogation/%d/status", caseNumber, interrogationId);
    }
    private String buildOtherInterrogationDestination(String caseNumber, Long interrogationId) {
        return String.format("/queue/case/%s/interrogation/%d/other/status", caseNumber, interrogationId);
    }
    private String buildCaseDestination(String caseNumber) {
        return String.format("/queue/case/%s/status", caseNumber);
    }
    private String buildAppealDestination() {
        return "/queue/appeals";
    }
    private String buildPlanDestination(String caseNumber) {
        return String.format("/queue/case/%s/plan/status", caseNumber);
    }
    private String buildGlobalPlanDestination() {
        return "/queue/plan/status";
    }
    private String buildOsmotrDestination() {
        return "/queue/osmotr/status";
    }
    private String buildReportDestination() {
        return "/queue/review/status";
    }
    private String getCaseTitle(String caseNumber) {
        return caseRepository.findByNumber(caseNumber)
                .map(Case::getTitle)
                .orElse("Unknown Case");
    }
    private void savePlanNotification(String userEmail, PlanStatusNotification n) {
        /*List<PlanNotification> existing = planNotificationRepository
                .findTop4ByUserEmailOrderByCreatedAtDesc(userEmail);

        if (existing.size() >= 4) {
            List<PlanNotification> toDelete = existing.subList(3, existing.size());
            planNotificationRepository.deleteAll(toDelete);
        }*/

        planNotificationRepository.save(PlanNotification.builder()
                .eventId(n.getEventId())
                .type(n.getType())
                .userEmail(userEmail)
                .caseId(n.getCaseId())
                .caseNumber(n.getCaseNumber())
                .caseTitle(n.getCaseTitle())
                .planStatus(n.getPlanStatus())
                .approvalLevel(n.getApprovalLevel())
                .reviewerName(n.getReviewerName())
                .reviewerProfession(n.getReviewerProfession())
                .comment(n.getComment())
                .isRead(false)
                .build());
    }

    public void notifyOsmotrStatus(OsmotrResultMessage message) {
        try {
            messagingTemplate.convertAndSendToUser(
                    message.getUserEmail(),
                    buildOsmotrDestination(),
                    message
            );
            log.info("Osmotr WS notification sent to {} for fileId={}, status={}",
                    message.getUserEmail(), message.getFileId(), message.getStatus());
        } catch (Exception e) {
            log.error("Osmotr WS notification failed for {}: {}", message.getUserEmail(), e.getMessage());
        }
    }
    public void notifyReportStatus(ReportResultMessage message) {
        try {
            messagingTemplate.convertAndSendToUser(
                    message.getUserEmail(),
                    buildReportDestination(),
                    message
            );
            log.info("Report WS notification sent to {}, status={}",
                    message.getUserEmail(), message.getStatus());
        } catch (Exception e) {
            log.error("Report WS notification failed for {}: {}", message.getUserEmail(), e.getMessage());
        }
    }
}