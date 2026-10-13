package org.di.digital.service.impl.appeal;

import lombok.RequiredArgsConstructor;
import org.di.digital.model.appeal.AppealNotification;
import org.di.digital.model.enums.appeal.AppealNotificationType;
import org.di.digital.model.enums.appeal.AppealStatus;
import org.di.digital.model.user.Appeal;
import org.di.digital.model.user.Region;
import org.di.digital.model.user.User;
import org.di.digital.repository.appeal.AppealNotificationRepository;
import org.di.digital.service.appeal.AppealService;
import org.di.digital.service.impl.core.NotificationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppealServiceImpl implements AppealService {

    private final AppealNotificationRepository notificationRepository;
    private final NotificationService emailNotificationService; // Ваш сервис (WebSocket/Email)

    @Override
    @Transactional(readOnly = true)
    public List<AppealNotification> getMyNotifications(String email) {
        return notificationRepository.findTop4ByUserEmailOrderByCreatedAtDesc(email);
    }

    @Override
    @Transactional
    public void markAllRead(String email) {
        List<AppealNotification> unread = notificationRepository.findAllByUserEmailAndIsReadFalse(email);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    @Override
    @Transactional
    public void markOneRead(Long id, String email) {
        AppealNotification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Уведомление не найдено с ID: " + id));

        if (!notification.getUserEmail().equals(email)) {
            throw new AccessDeniedException("Доступ запрещен");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(String email) {
        return notificationRepository.countByUserEmailAndIsReadFalse(email);
    }

    @Override
    @Transactional
    public void createNewAppealNotification(Appeal appeal, User applicant) {
        Region region = appeal.getRegion();
        if (region != null && region.getAdmins() != null) {
            region.getAdmins().forEach(admin -> {
                AppealNotification notification = AppealNotification.builder()
                        .userEmail(admin.getEmail())
                        .appealId(appeal.getId())
                        .applicantName(applicant.getName())
                        .applicantSurname(applicant.getSurname())
                        .type(AppealNotificationType.NEW_APPEAL_RECEIVED)
                        .appealStatus(AppealStatus.PENDING)
                        .isRead(false)
                        .createdAt(LocalDateTime.now())
                        .build();
                notificationRepository.save(notification);
            });
        }
    }

    @Override
    @Transactional
    public void createAppealReviewedNotification(Appeal appeal, User reviewer) {
        boolean isApproved = appeal.getStatus() == AppealStatus.APPROVED;

        AppealNotificationType notificationType = isApproved
                ? AppealNotificationType.APPEAL_APPROVED
                : AppealNotificationType.APPEAL_REJECTED;

        AppealNotification notification = AppealNotification.builder()
                .userEmail(appeal.getUser().getEmail())
                .appealId(appeal.getId())
                .applicantName(appeal.getUser().getName())
                .applicantSurname(appeal.getUser().getSurname())
                .type(notificationType)
                .appealStatus(appeal.getStatus())
                .reviewerName(reviewer.getName() + " " + reviewer.getSurname())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        notificationRepository.save(notification);

        String targetEmail = appeal.getUser().getEmail();
        String textStatus = isApproved ? AppealStatus.APPROVED.getDescription() : AppealStatus.REJECTED.getDescription();

        String emailText = String.format("Здравствуйте, %s %s! Статус вашей заявки на регистрацию в системе изменился. " +
                        "Ваша заявка была %s администратором",
                appeal.getUser().getName(),
                appeal.getUser().getSurname(),
                textStatus);

        emailNotificationService.sendNotificationToUser(targetEmail, emailText);
    }
}