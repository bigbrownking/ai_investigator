package org.di.digital.service.plan;

import org.di.digital.dto.response.plan.PlanApprovalHistoryDto;

import java.util.List;

public interface PlanApprovalService {
    void approvePlan(String email, String caseNumber);
    void rejectPlan(String email, String caseNumber, String comment);
    void withdrawPlan(String email, String caseNumber);
    void finalApprovePlan(String email, String caseNumber);
    List<PlanApprovalHistoryDto> getHistory(String email, String caseNumber);
}