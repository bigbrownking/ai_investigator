package org.di.digital.service.impl.plan;

import lombok.RequiredArgsConstructor;
import org.di.digital.model.enums.plan.PlanStatus;
import org.di.digital.model.plan.CasePlan;
import org.di.digital.model.user.User;
import org.di.digital.repository.plan.PlanApprovalHistoryRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanApproverResolver {

    private final PlanApprovalHistoryRepository historyRepository;

    public String getApproverName(CasePlan plan) {
        if (plan == null) {
            return null;
        }

        User approver = plan.getApprovedBy();
        if (approver == null && plan.getStatus() == PlanStatus.APPROVED_L3) {
            approver = historyRepository
                    .findFirstByCaseEntityIdAndToStatusOrderByIdDesc(
                            plan.getCaseEntity().getId(), PlanStatus.APPROVED_L3)
                    .map(history -> history.getReviewer())
                    .orElse(null);
        }
        return approver != null ? approver.getFio() : null;
    }
}
