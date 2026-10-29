package org.di.digital.dto.response.plan;

import lombok.Builder;
import lombok.Getter;
import org.di.digital.model.enums.plan.PlanStatus;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class PlanApprovalDetailDto {
    private String author;
    private String reviewedBy;
    private String approvedBy;
    private String caseNumber;
    private String caseTitle;
    private String region;
    private PlanStatus planStatus;
    private LocalDateTime planSubmittedAt;
    private Map<String, Object> plan;
}