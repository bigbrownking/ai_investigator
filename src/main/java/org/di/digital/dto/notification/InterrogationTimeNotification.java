package org.di.digital.dto.notification;

import lombok.*;
import org.di.digital.model.enums.InterrogationTimeEvent;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterrogationTimeNotification {
    private String caseNumber;
    private Long interrogationId;
    private String fio;
    private InterrogationTimeEvent event;
    private String message;

    private long continuousSeconds;
    private long dailySeconds;
    private String profile;

    private boolean continuousWarn;
    private boolean continuousLimitReached;
    private boolean dailyWarn;
    private boolean dailyLimitReached;

    private boolean onBreak;
    private boolean breakOver;
    private long breakRemainingSeconds;

    private LocalDateTime timestamp;
}