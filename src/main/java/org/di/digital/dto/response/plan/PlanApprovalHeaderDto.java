package org.di.digital.dto.response.plan;

import lombok.Builder;
import lombok.Getter;
import org.di.digital.model.enums.plan.PlanStatus;

import java.time.LocalDateTime;

@Getter
@Builder
public class PlanApprovalHeaderDto {
    private String author;
    private String caseNumber;
    private String caseTitle;
    private PlanStatus planStatus;
    private LocalDateTime planSubmittedAt;
    private int actionCount;
}