package org.di.digital.reporting.repository;

import org.di.digital.reporting.model.ZonalSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ZonalSubmissionRepository extends JpaRepository<ZonalSubmission, Long>,
        JpaSpecificationExecutor<ZonalSubmission> {

    Optional<ZonalSubmission> findByIdAndRegionId(Long id, Long regionId);

    boolean existsByTemplateId(Long templateId);

    @Query("""
            select s from ZonalSubmission s
            where s.templateId = :templateId and s.submittedAt < :before
              and s.id = (select max(s2.id) from ZonalSubmission s2
                          where s2.templateId = :templateId and s2.regionId = s.regionId and s2.submittedAt < :before)
            """)
    List<ZonalSubmission> findLatestPerRegion(@Param("templateId") Long templateId,
                                              @Param("before") LocalDateTime before);
}
