package org.di.digital.repository.plan;

import org.di.digital.model.plan.PlanApprovalHistory;
import org.di.digital.model.enums.plan.PlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanApprovalHistoryRepository extends JpaRepository<PlanApprovalHistory, Long> {

    List<PlanApprovalHistory> findByCaseEntityIdOrderByReviewedAtDesc(Long caseId);

    Optional<PlanApprovalHistory> findFirstByCaseEntityIdAndToStatusOrderByIdDesc(
            Long caseId, PlanStatus toStatus);
}
