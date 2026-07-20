package org.di.digital.util.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.interrogation.InterrogationTimeStatusResponse;
import org.di.digital.model.enums.CaseInterrogationStatusEnum;
import org.di.digital.model.enums.InterrogationTimeEvent;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.interrogation.CaseInterrogationTimerSession;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.service.impl.core.NotificationService;
import org.di.digital.service.impl.interrogation.CaseInterrogationServiceImpl;
import org.di.digital.service.impl.interrogation.InterrogationTimeGuard;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterrogationTimeScheduler {

    private final CaseInterrogationRepository caseInterrogationRepository;
    private final InterrogationTimeGuard timeGuard;
    private final NotificationService notificationService;
    private final CaseInterrogationServiceImpl interrogationService;

    @Scheduled(fixedDelayString = "${scheduler.interrogation.time}")
    @Transactional
    public void checkActiveInterrogations() {
        List<CaseInterrogation> active = caseInterrogationRepository
                .findActiveWithCase(CaseInterrogationStatusEnum.IN_PROGRESS);

        if (active.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        log.debug("Interrogation time check: {} active", active.size());

        for (CaseInterrogation i : active) {
            try {
                checkOne(i, now);
            } catch (Exception e) {
                log.error("Time check failed for interrogation {}: {}", i.getId(), e.getMessage());
            }
        }
    }

    private void checkOne(CaseInterrogation i, LocalDateTime now) {
        if (!Boolean.TRUE.equals(i.getCategoryConfirmed())) {
            return;
        }

        InterrogationTimeStatusResponse status = timeGuard.status(i, now);
        String caseNumber = i.getCaseEntity().getNumber();
        boolean changed = false;

        if (status.isDailyLimitReached()) {
            notificationService.sendInterrogationTimeNotification(
                    caseNumber, i,
                    InterrogationTimeEvent.DAILY_LIMIT_REACHED,
                    "Допрос автоматически завершён. Достигнут дневной лимит.",
                    status);

            interrogationService.completeInterrogationByScheduler(i);
            return;
        }

        // ── НЕПРЕРЫВНЫЙ ЛИМИТ: остановить сессию, НЕ завершать (ТЗ п.6) ──
        // Уважает override: если пользователь подтвердил основание продолжить — не трогаем.
        if (!status.isOnBreak()
                && status.isContinuousLimitReached()
                && !Boolean.TRUE.equals(i.getContinuousOverrideConfirmed())) {

            boolean sessionPaused = pauseActiveSession(i, now);

            if (!Boolean.TRUE.equals(i.getNotifiedContinuousLimit())) {
                notificationService.sendInterrogationTimeNotification(
                        caseNumber, i,
                        InterrogationTimeEvent.CONTINUOUS_LIMIT_REACHED,
                        "Достигнут предел непрерывного допроса. Необходим перерыв "
                                + "либо подтверждение оснований для продолжения.",
                        status);
                i.setNotifiedContinuousLimit(true);
                changed = true;
            }

            if (sessionPaused || changed) {
                caseInterrogationRepository.save(i);
            }
            return;
        }

        // ── Перерыв закончился (ТЗ п.8) ──
        if (status.isOnBreak() && status.isBreakOver()
                && !Boolean.TRUE.equals(i.getNotifiedBreakOver())) {
            notificationService.sendInterrogationTimeNotification(caseNumber, i,
                    InterrogationTimeEvent.BREAK_OVER,
                    "Перерыв завершён. Можно продолжить допрос.", status);
            i.setNotifiedBreakOver(true);
            changed = true;
        }

        // ── Непрерывное предупреждение (ТЗ п.5) ──
        // Лимит-нотификация уже обработана веткой выше с return, здесь только warning.
        if (!status.isOnBreak()
                && status.isContinuousWarn()
                && !status.isContinuousLimitReached()
                && !Boolean.TRUE.equals(i.getNotifiedContinuousWarn())) {
            notificationService.sendInterrogationTimeNotification(caseNumber, i,
                    InterrogationTimeEvent.CONTINUOUS_WARNING,
                    "Приближается предел непрерывного допроса.", status);
            i.setNotifiedContinuousWarn(true);
            changed = true;
        }

        // ── Суточное предупреждение ──
        if (status.isDailyWarn()
                && !status.isDailyLimitReached()
                && !Boolean.TRUE.equals(i.getNotifiedDailyWarn())) {
            notificationService.sendInterrogationTimeNotification(caseNumber, i,
                    InterrogationTimeEvent.DAILY_WARNING,
                    "Приближается предел общей продолжительности за день.", status);
            i.setNotifiedDailyWarn(true);
            changed = true;
        }

        if (changed) {
            caseInterrogationRepository.save(i);
        }
    }

    private boolean pauseActiveSession(CaseInterrogation i, LocalDateTime now) {
        if (Boolean.TRUE.equals(i.getIsPaused())) {
            return false;
        }

        CaseInterrogationTimerSession last = i.getTimerSessions().stream()
                .filter(s -> s.getPausedAt() == null && s.getStartedAt() != null)
                .max(Comparator.comparing(CaseInterrogationTimerSession::getStartedAt))
                .orElse(null);

        if (last == null) {
            return false;
        }

        last.setPausedAt(now);

        long seconds = ChronoUnit.SECONDS.between(last.getStartedAt(), now);
        long total = Optional.ofNullable(i.getAccumulatedSeconds()).orElse(0L) + seconds;
        i.setAccumulatedSeconds(total);
        i.setDurationSeconds(total);
        i.setIsPaused(true);

        return true;
    }
}