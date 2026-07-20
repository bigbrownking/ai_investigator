package org.di.digital.repository.qualification;

import org.di.digital.model.cases.Case;
import org.di.digital.model.qualification.CaseQualification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CaseQualificationRepository extends JpaRepository<CaseQualification, Long> {
    Optional<CaseQualification> findByCaseEntityNumber(String caseNumber);
    @Query("""
        SELECT COUNT(q) FROM CaseQualification q
        WHERE q.content IS NOT NULL AND q.content <> ''
        AND q.caseEntity.createdDate BETWEEN :start AND :end
        """)
    long countNonEmptyBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            SELECT q.caseEntity FROM CaseQualification q
            WHERE q.content IS NOT NULL
            AND EXISTS (
                SELECT f FROM CaseFile f
                WHERE f.caseEntity = q.caseEntity
                AND f.isQualification = true
            )
            """)
    List<Case> findAllWithBothQualifications();

    @Query("""
        SELECT q.caseEntity.id FROM CaseQualification q
        WHERE q.content IS NOT NULL
        AND EXISTS (
            SELECT f FROM CaseFile f
            WHERE f.caseEntity = q.caseEntity
            AND f.isQualification = true
        )
        """)
    List<Long> findAllCaseIdsWithBothQualifications();
}