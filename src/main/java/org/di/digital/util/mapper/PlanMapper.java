package org.di.digital.util.mapper;

import lombok.RequiredArgsConstructor;
import org.di.digital.dto.response.plan.ManagementPendingPlanDto;
import org.di.digital.dto.response.plan.PlanApprovalHistoryDto;
import org.di.digital.dto.response.plan.PlanEditHistoryDto;
import org.di.digital.model.cases.Case;
import org.di.digital.model.plan.CasePlan;
import org.di.digital.model.plan.PlanApprovalHistory;
import org.di.digital.model.plan.PlanEditHistory;
import org.di.digital.model.user.User;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PlanMapper {

    public PlanApprovalHistoryDto toApprovalHistoryDto(PlanApprovalHistory h) {
        User reviewer = h.getReviewer();
        return PlanApprovalHistoryDto.builder()
                .id(h.getId())
                .approvalLevel(h.getApprovalLevel())
                .fromStatus(h.getFromStatus())
                .toStatus(h.getToStatus())
                .reviewerName(reviewer != null ? reviewer.getFio() : null)
                .reviewerProfession(reviewer != null && reviewer.getProfession() != null
                        ? reviewer.getProfession().getRuName() : null)
                .comment(h.getComment())
                .reviewedAt(h.getReviewedAt())
                .build();
    }

    public ManagementPendingPlanDto toManagementPendingPlanDto(CasePlan p, Map<String, Object> enrichedPlan) {
        Case c = p.getCaseEntity();
        return ManagementPendingPlanDto.builder()
                .author(c.getOwner().getFio())
                .caseNumber(c.getNumber())
                .caseTitle(c.getTitle())
                .planStatus(p.getStatus())
                .planSubmittedAt(p.getSubmittedAt())
                .plan(enrichedPlan)
                .build();
    }

    public PlanEditHistoryDto toEditHistoryDto(PlanEditHistory h) {
        return PlanEditHistoryDto.builder()
                .id(h.getId())
                .editorName(h.getEditor().getFio())
                .actionNumber(h.getActionNumber())
                .fieldKey(h.getFieldKey())
                .oldValue(h.getOldValue())
                .newValue(h.getNewValue())
                .editedAt(h.getEditedAt())
                .build();
    }
}