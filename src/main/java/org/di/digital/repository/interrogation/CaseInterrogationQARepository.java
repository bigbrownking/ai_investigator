package org.di.digital.repository.interrogation;

import org.di.digital.model.interrogation.CaseInterrogationQA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CaseInterrogationQARepository extends JpaRepository<CaseInterrogationQA, Long> {
    List<CaseInterrogationQA> findByInterrogationIdOrderByOrderIndexAsc(Long interrogationId);
    Optional<CaseInterrogationQA> findByIdAndInterrogationId(Long id, Long interrogationId);
    @Query("""
        SELECT COUNT(qa) FROM CaseInterrogationQA qa
        WHERE qa.isReformulated = true 
        AND qa.createdAt BETWEEN :start AND :end
        AND (:regionId IS NULL OR qa.interrogation.caseEntity.owner.region.id = :regionId)
        """)
    long countByIsReformulatedTrueAndCreatedAtBetweenAndRegion(@Param("start") LocalDateTime start,
                                                               @Param("end") LocalDateTime end,
                                                               @Param("regionId") Long regionId);
}