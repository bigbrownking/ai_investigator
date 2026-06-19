package org.di.digital.repository.plan;

import org.di.digital.model.plan.PlanEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanEditHistoryRepository extends JpaRepository<PlanEditHistory, Long> {
    List<PlanEditHistory> findByCaseEntityIdOrderByEditedAtDesc(Long caseId);

}
