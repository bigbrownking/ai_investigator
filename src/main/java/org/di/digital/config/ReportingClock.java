package org.di.digital.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class ReportingClock {

    private final Clock clock;

    @Autowired
    public ReportingClock(@Value("${reporting.time-zone}") String timeZone) {
        this(Clock.system(ZoneId.of(timeZone.trim())));
    }

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
