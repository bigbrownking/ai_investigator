package org.di.digital.dto.notification;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterrogationNotification {
    private String caseNumber;
    private Long interrogationId;
    private Long qaId;
    private String fio;
    private String role;
    private InterrogationNotificationStatus status;
    private String activity;
    private String transcribedText;
    private String errorMessage;
    private LocalDateTime timestamp;
    private String fieldName;
}