package org.di.digital.repository.indictment;

import org.di.digital.model.indictment.CaseIndictment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CaseIndictmentRepository extends JpaRepository<CaseIndictment, Long> {
    Optional<CaseIndictment> findByCaseEntityNumber(String caseNumber);
    @Query("""
        SELECT COUNT(i) FROM CaseIndictment i
        WHERE i.content IS NOT NULL AND i.content <> ''
        AND i.caseEntity.createdDate BETWEEN :start AND :end
        """)
    long countNonEmptyBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}