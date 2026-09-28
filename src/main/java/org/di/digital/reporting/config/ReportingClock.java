package org.di.digital.reporting.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Defines "today" for reporting: the report date operators may edit and the default date of
 * consolidated tables. Kazakhstan time (Asia/Almaty, UTC+5) unless reporting.time-zone says otherwise,
 * because servers and containers usually run in UTC.
 */
@Component
public class ReportingClock {

    static final String DEFAULT_ZONE = "Asia/Almaty";

    private final Clock clock;

    @Autowired
    public ReportingClock(@Value("${reporting.time-zone:" + DEFAULT_ZONE + "}") String timeZone) {
        this(Clock.system(ZoneId.of(timeZone == null || timeZone.isBlank() ? DEFAULT_ZONE : timeZone.trim())));
    }

    /** For tests: a fixed or offset clock. */
    public ReportingClock(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
