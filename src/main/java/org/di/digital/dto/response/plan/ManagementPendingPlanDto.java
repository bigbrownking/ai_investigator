package org.di.digital.dto.response.plan;

import lombok.Builder;
import lombok.Getter;
import org.di.digital.model.enums.PlanStatus;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class ManagementPendingPlanDto {
    private String author;
    private String caseNumber;
    private String caseTitle;
    private PlanStatus planStatus;
    private LocalDateTime planSubmittedAt;
    private Map<String, Object> plan;
}
