package org.di.digital.repository.plan;

import org.di.digital.model.plan.PlanNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanNotificationRepository extends JpaRepository<PlanNotification, Long> {
    List<PlanNotification> findTop4ByUserEmailOrderByCreatedAtDesc(String userEmail);
    long countByUserEmailAndIsReadFalse(String userEmail);
}
