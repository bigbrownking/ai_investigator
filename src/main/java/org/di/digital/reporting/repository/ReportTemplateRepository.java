package org.di.digital.reporting.repository;

import org.di.digital.reporting.model.ReportTemplate;
import org.di.digital.reporting.model.enums.TemplateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {
    Optional<ReportTemplate> findByCodeAndStatus(String code, TemplateStatus status);
    Optional<ReportTemplate> findByCodeAndVersion(String code, Integer version);
    Optional<ReportTemplate> findFirstByCodeOrderByVersionDesc(String code);
    List<ReportTemplate> findByCodeOrderByVersionDesc(String code);
    List<ReportTemplate> findByStatusOrderByCodeAsc(TemplateStatus status);
    boolean existsByCode(String code);
}
