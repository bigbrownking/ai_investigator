package org.di.digital.repository.review;

import org.di.digital.model.report.CaseReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CaseReviewRepository extends JpaRepository<CaseReview, Long> {

    Optional<CaseReview> findByCaseEntityNumber(String caseNumber);

    Optional<CaseReview> findByCaseEntityId(Long caseId);
}