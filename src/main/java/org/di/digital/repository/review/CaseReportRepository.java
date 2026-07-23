package org.di.digital.repository.review;

import org.di.digital.model.report.CaseReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import java.util.Optional;

@Repository
public interface CaseReportRepository extends JpaRepository<CaseReport, Long> {

    Optional<CaseReport> findByCaseEntityNumber(String caseNumber);

    Optional<CaseReport> findByCaseEntityId(Long caseId);
}