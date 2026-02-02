package org.di.digital.repository;

import org.di.digital.model.CaseFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaseFileRepository extends JpaRepository<CaseFile, Long> {
    @Query("SELECT cf FROM CaseFile cf WHERE cf.caseEntity.number = :caseNumber ORDER BY cf.uploadedAt DESC")
    List<CaseFile> findByCaseEntityNumber(@Param("caseNumber") String caseNumber);

}
