package org.di.digital.dto.response.plan;

import lombok.Builder;
import lombok.Data;
import org.di.digital.model.enums.PlanStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class PlanApprovalHistoryDto {
    private Long id;
    private int approvalLevel;
    private PlanStatus fromStatus;
    private PlanStatus toStatus;
    private String reviewerName;
    private String reviewerProfession;
    private String comment;
    private LocalDateTime reviewedAt;
}