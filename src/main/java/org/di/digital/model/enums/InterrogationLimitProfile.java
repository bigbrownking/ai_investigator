package org.di.digital.model.enums;

import java.time.Duration;

public enum InterrogationLimitProfile {
    //    STANDARD(
//            Duration.ofHours(3).plusMinutes(45),
//            Duration.ofHours(4),
//            Duration.ofHours(7).plusMinutes(30),
//            Duration.ofHours(8)
//    ),
//    SPECIAL(
//            Duration.ofMinutes(160),
//            Duration.ofHours(3),
//            Duration.ofMinutes(280),
//            Duration.ofHours(5)
//    ),
    STANDARD(
            Duration.ofMinutes(2),
            Duration.ofMinutes(3),
            Duration.ofMinutes(5),
            Duration.ofMinutes(6)
    ),
    SPECIAL(
            Duration.ofMinutes(2),
            Duration.ofMinutes(3),
            Duration.ofMinutes(5),
            Duration.ofMinutes(6)
    ),
    TEST(
            Duration.ofMinutes(2),
            Duration.ofMinutes(3),
            Duration.ofMinutes(7),
            Duration.ofMinutes(8)
    );

    public final Duration continuousWarn;
    public final Duration continuousMax;
    public final Duration dailyWarn;
    public final Duration dailyMax;

    InterrogationLimitProfile(Duration cw, Duration cm, Duration dw, Duration dm) {
        this.continuousWarn = cw;
        this.continuousMax = cm;
        this.dailyWarn = dw;
        this.dailyMax = dm;
    }
}