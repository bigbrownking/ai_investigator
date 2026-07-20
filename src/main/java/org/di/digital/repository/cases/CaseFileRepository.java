package org.di.digital.repository.cases;

import org.di.digital.model.cases.CaseFile;
import org.di.digital.model.enums.CaseFileStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CaseFileRepository extends JpaRepository<CaseFile, Long> {
    @Query("SELECT cf FROM CaseFile cf WHERE cf.caseEntity.number = :caseNumber ORDER BY cf.uploadedAt DESC")
    List<CaseFile> findByCaseEntityNumber(@Param("caseNumber") String caseNumber);

    boolean existsByCaseEntityIdAndStatusNotIn(Long caseId, List<CaseFileStatusEnum> statuses);

    List<CaseFile> findByPagesIsNull();

    Optional<CaseFile> findByOriginalFileNameAndCaseEntityId(String originalFileName, Long caseId);

    @Query("SELECT SUM(f.pages) FROM CaseFile f WHERE f.pages IS NOT NULL AND f.uploadedAt BETWEEN :start AND :end")
    Long countPagesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
