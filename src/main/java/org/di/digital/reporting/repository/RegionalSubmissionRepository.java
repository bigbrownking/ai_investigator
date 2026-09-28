package org.di.digital.reporting.repository;

import org.di.digital.reporting.model.RegionalSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RegionalSubmissionRepository extends JpaRepository<RegionalSubmission, Long> {
    Optional<RegionalSubmission> findByTemplateCodeAndRegionIdAndReportDate(
            String templateCode, Long regionId, LocalDate reportDate);
    List<RegionalSubmission> findByTemplateCodeAndReportDate(String templateCode, LocalDate reportDate);
}
