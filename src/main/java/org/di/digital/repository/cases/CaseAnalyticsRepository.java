package org.di.digital.repository.cases;

import org.di.digital.model.cases.CaseAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CaseAnalyticsRepository extends JpaRepository<CaseAnalytics, Long> {
    Optional<CaseAnalytics> findByCaseEntityId(Long caseId);
    @Query("SELECT AVG(a.qualificationScorePercent) FROM CaseAnalytics a WHERE a.qualificationScorePercent IS NOT NULL")
    Double getAverageQualificationScorePercent();
}