package org.di.digital.util.mapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class LastSeenFormatter {

    private LastSeenFormatter() {}

    public static String format(LocalDateTime lastSeenAt) {
        if (lastSeenAt == null) return null;
        LocalDateTime now = LocalDateTime.now();

        long minutes = ChronoUnit.MINUTES.between(lastSeenAt, now);
        if (minutes < 1) return "только что";
        if (minutes < 60) return "был(а) в сети " + minutes + " мин. назад";

        long hours = ChronoUnit.HOURS.between(lastSeenAt, now);
        if (hours < 24) return "был(а) в сети " + hours + " ч. назад";

        long days = ChronoUnit.DAYS.between(lastSeenAt, now);
        if (days < 30) return "был(а) в сети " + days + " дн. назад";

        return "был(а) в сети давно";
    }
}