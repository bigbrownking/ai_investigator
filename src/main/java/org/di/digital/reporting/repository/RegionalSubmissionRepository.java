package org.di.digital.reporting.repository;

import org.di.digital.reporting.model.RegionalSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Rows are looked up by form code across template versions: a row saved before a publish is still found. */
@Repository
public interface RegionalSubmissionRepository extends JpaRepository<RegionalSubmission, Long> {
    Optional<RegionalSubmission> findFirstByTemplate_CodeAndRegionIdAndReportDateOrderByIdDesc(
            String templateCode, Long regionId, LocalDate reportDate);
    List<RegionalSubmission> findByTemplate_CodeAndReportDate(String templateCode, LocalDate reportDate);
}
