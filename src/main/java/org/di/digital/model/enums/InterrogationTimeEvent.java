package org.di.digital.model.enums;

public enum InterrogationTimeEvent {
    CONTINUOUS_WARNING,        // треб.1: 3:45 (2:45 спец.)
    CONTINUOUS_LIMIT_REACHED,  // треб.1,4: 4:00 (3:00 спец.) — нужен перерыв
    BREAK_OVER,                // треб.2: час перерыва истёк
    DAILY_WARNING,             // треб.3: 7:30 (4:30 спец.)
    DAILY_LIMIT_REACHED        // треб.3,4: 8:00 (5:00 спец.) — стоп
}