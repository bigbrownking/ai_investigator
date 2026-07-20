package org.di.digital.service.impl.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.interrogation.InterrogationTimeStatusResponse;
import org.di.digital.model.enums.InterrogationLimitProfile;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.interrogation.CaseInterrogationTimerSession;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterrogationTimeGuard {

    private InterrogationLimitProfile profileOf(CaseInterrogation i) {
        return i.getLimitProfile() == null ? InterrogationLimitProfile.STANDARD : i.getLimitProfile();
    }

    /**
     * Непрерывная продолжительность = сумма сессий ТЕКУЩЕЙ серии
     * (начавшихся не раньше currentSeriesStartedAt).
     * Во время перерыва верхняя граница — момент ухода в перерыв (значение заморожено).
     * После завершённого перерыва серия сбрасывается (currentSeriesStartedAt сдвинут вперёд),
     * поэтому старые сессии не учитываются и continuous = 0 до следующего старта.
     */
    public Duration continuousElapsed(CaseInterrogation i, LocalDateTime now) {
        LocalDateTime seriesStart = i.getCurrentSeriesStartedAt();
        if (seriesStart == null) {
            return Duration.ZERO; // серия ещё не начиналась
        }

        boolean onBreak = Boolean.TRUE.equals(i.getOnBreak());
        // во время перерыва замораживаем на моменте ухода в перерыв
        LocalDateTime cap = (onBreak && i.getBreakStartedAt() != null)
                ? i.getBreakStartedAt()
                : now;

        long seconds = 0;
        for (CaseInterrogationTimerSession s : i.getTimerSessions()) {
            if (s.getStartedAt() == null) continue;
            if (s.getStartedAt().isBefore(seriesStart)) continue;   // сессии прошлых серий
            if (s.getStartedAt().isAfter(cap)) continue;            // сессии после cap

            LocalDateTime end = s.getPausedAt() != null ? s.getPausedAt() : cap;
            if (end.isAfter(cap)) end = cap;
            if (end.isBefore(s.getStartedAt())) continue;

            seconds += ChronoUnit.SECONDS.between(s.getStartedAt(), end);
        }
        return Duration.ofSeconds(Math.max(0, seconds));
    }

    /**
     * Суточная продолжительность = сумма всех сессий текущего календарного дня.
     * Не сбрасывается перерывом. Во время перерыва так же замораживается на breakStartedAt.
     */
    public Duration dailyElapsed(CaseInterrogation i, LocalDateTime now) {
        boolean onBreak = Boolean.TRUE.equals(i.getOnBreak());
        LocalDateTime cap = (onBreak && i.getBreakStartedAt() != null)
                ? i.getBreakStartedAt()
                : now;

        LocalDate day = cap.toLocalDate();
        long seconds = 0;
        for (CaseInterrogationTimerSession s : i.getTimerSessions()) {
            if (s.getStartedAt() == null || !s.getStartedAt().toLocalDate().equals(day)) continue;
            if (s.getStartedAt().isAfter(cap)) continue;

            LocalDateTime end = s.getPausedAt() != null ? s.getPausedAt() : cap;
            if (end.isAfter(cap)) end = cap;
            if (end.isBefore(s.getStartedAt())) continue;

            seconds += ChronoUnit.SECONDS.between(s.getStartedAt(), end);
        }
        return Duration.ofSeconds(Math.max(0, seconds));
    }

    /** Полный статус для фронта. */
    public InterrogationTimeStatusResponse status(CaseInterrogation i, LocalDateTime now) {
        InterrogationLimitProfile p = profileOf(i);
        Duration cont = continuousElapsed(i, now);
        Duration daily = dailyElapsed(i, now);

        boolean breakActive = Boolean.TRUE.equals(i.getOnBreak());
        Duration breakRemaining = Duration.ZERO;
        boolean breakOver = true;
        if (breakActive && i.getBreakStartedAt() != null) {
            Duration passed = Duration.between(i.getBreakStartedAt(), now);
            breakOver = passed.compareTo(CaseInterrogation.MANDATORY_BREAK) >= 0;
            breakRemaining = breakOver ? Duration.ZERO : CaseInterrogation.MANDATORY_BREAK.minus(passed);
        }

        boolean stillOnBreak = breakActive && !breakOver;

        // Лимит достигнут, ТОЛЬКО если пользователь ещё не подтвердил основание продолжить.
        // После override флаг гасится, чтобы фронт не открывал модалку на каждом poll (ТЗ п.9).
        boolean continuousLimitReached = cont.compareTo(p.continuousMax) >= 0
                && !Boolean.TRUE.equals(i.getContinuousOverrideConfirmed());

        return InterrogationTimeStatusResponse.builder()
                .profile(p.name())
                .continuousSeconds(cont.getSeconds())
                .dailySeconds(daily.getSeconds())
                .continuousWarn(cont.compareTo(p.continuousWarn) >= 0)
                .continuousLimitReached(continuousLimitReached)   // ⬅️ учитывает override
                .dailyWarn(daily.compareTo(p.dailyWarn) >= 0)
                .dailyLimitReached(daily.compareTo(p.dailyMax) >= 0)
                .onBreak(stillOnBreak)
                .breakOver(breakOver)
                .breakRemainingSeconds(breakRemaining.getSeconds())
                .categoryConfirmed(Boolean.TRUE.equals(i.getCategoryConfirmed()))
                .build();
    }

    /**
     * Проверка перед запуском/записью. Бросает, если продолжать нельзя.
     */
    public void assertCanRecord(CaseInterrogation i, LocalDateTime now) {
        InterrogationLimitProfile p = profileOf(i);

        Duration daily = dailyElapsed(i, now);
        if (daily.compareTo(p.dailyMax) >= 0) {
            throw new IllegalStateException(
                    "Достигнута предельная общая продолжительность допроса за день — дальнейшее проведение недопустимо");
        }

        if (Boolean.TRUE.equals(i.getOnBreak()) && i.getBreakStartedAt() != null) {
            Duration passed = Duration.between(i.getBreakStartedAt(), now);
            if (passed.compareTo(CaseInterrogation.MANDATORY_BREAK) < 0) {
                long minutesLeft = CaseInterrogation.MANDATORY_BREAK.minus(passed).toMinutes();
                throw new IllegalStateException(
                        "Возобновление допроса недоступно до истечения перерыва. Осталось: " + minutesLeft + " мин.");
            }
        }

        Duration cont = continuousElapsed(i, now);
        if (cont.compareTo(p.continuousMax) >= 0 && !Boolean.TRUE.equals(i.getContinuousOverrideConfirmed())) {
            throw new IllegalStateException(
                    "Достигнута предельная непрерывная продолжительность допроса — требуется перерыв "
                            + "либо подтверждение оснований для продолжения");
        }
    }
}