package org.di.digital.dto.notification;

import lombok.Builder;
import lombok.Data;
import org.di.digital.model.enums.review.ReminderType;

import java.time.LocalDateTime;

@Data
@Builder
public class ReminderNotification {
    private String eventId;
    private ReminderType type;
    private String message;
    private LocalDateTime timestamp;
}