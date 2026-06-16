package org.di.digital.dto.notification;

import lombok.Builder;
import lombok.Data;
import org.di.digital.model.enums.PlanNotificationType;
import org.di.digital.model.enums.PlanStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class PlanStatusNotification {
    private String eventId;
    private PlanNotificationType type;
    private Long caseId;
    private String caseNumber;
    private String caseTitle;
    private PlanStatus planStatus;
    private int approvalLevel;
    private String reviewerName;
    private String reviewerProfession;
    private String comment;
    private LocalDateTime timestamp;
}