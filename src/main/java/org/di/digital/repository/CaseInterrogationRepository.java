package org.di.digital.repository;

import org.di.digital.model.CaseInterrogation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseInterrogationRepository extends JpaRepository<CaseInterrogation, Long> {

    boolean existsByNumberAndRole(String number, String role);
    @Query("SELECT COUNT(i) FROM CaseInterrogation i WHERE i.caseEntity.id = :caseId AND i.status != 'COMPLETED'")
    long countNonClosedInterrogations(@Param("caseId") Long caseId);
}
