package org.di.digital.service.plan;

import org.di.digital.dto.request.plan.AddPlanActionRequest;
import org.di.digital.dto.request.plan.ManualStatusRequest;
import org.di.digital.dto.response.plan.*;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.PlanStatus;
import org.di.digital.model.plan.PlanNotification;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;

public interface PlanService {
    CasePlanResponse generatePlan(String caseNumber, String mode, String email);
    CasePlanResponse getPlan(String caseNumber, String email);
    Resource downloadPlanAsWord(String caseNumber, String email);
    PlanSubmitResponse submitPlan(String caseNumber, String email);
    List<ManagementPendingPlanDto> getManagementPendingPlans(String email);
    CasePlanResponse updatePlanField(String caseNumber, String email,
                                        int actionNumber, String key, Object value);
    ManualStatusResponse updateActionStatus(String caseNumber, String email, ManualStatusRequest request);

    CasePlanResponse addAction(String caseNumber, String email, AddPlanActionRequest request);
    CasePlanResponse deleteAction(String caseNumber, String email, int actionNumber);
    List<PlanEditHistoryDto> getEditHistory(String caseNumber, String email);

    List<PlanNotification> getMyNotifications(String email);
    void markAllRead(String email);
    void markOneRead(Long id, String email);
    long getUnreadCount(String email);

    boolean canWithdraw(PlanStatus status);
    String getApproverName(Case caseEntity);
    String getReviewerName(Case caseEntity);
    Map<String, Object> enrichPlanWithStatus(Map<String, Object> plan);
}
