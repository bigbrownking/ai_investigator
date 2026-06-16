package org.di.digital.repository.plan;

import org.di.digital.model.plan.PlanApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanApprovalHistoryRepository extends JpaRepository<PlanApprovalHistory, Long> {

    List<PlanApprovalHistory> findByCaseEntityIdOrderByReviewedAtDesc(Long caseId);
}
