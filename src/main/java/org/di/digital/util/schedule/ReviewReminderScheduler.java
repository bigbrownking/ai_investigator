package org.di.digital.util.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.repository.user.UserRepository;
import org.di.digital.service.impl.core.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewReminderScheduler {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Scheduled(cron = "${scheduler.review.reminder}", zone = "Asia/Almaty")
    public void sendWeeklyReviewReminder() {
        log.info("Запуск еженедельного напоминания о рецензии");
        List<String> emails = userRepository.findAllActiveNonAdminEmails();
        notificationService.sendReviewReminderToAll(emails);
    }

}