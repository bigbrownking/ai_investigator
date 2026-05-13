package org.di.digital.repository.interrogation;

import org.di.digital.model.interrogation.CaseInterrogation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseInterrogationRepository extends JpaRepository<CaseInterrogation, Long> {

    boolean existsByNumberAndRole(String number, String role);
    @Query("SELECT COUNT(i) FROM CaseInterrogation i WHERE i.caseEntity.id = :caseId AND i.status != 'COMPLETED'")
    long countNonClosedInterrogations(@Param("caseId") Long caseId);

    @Query("""
    SELECT COUNT(DISTINCT i) FROM CaseInterrogation i
    JOIN i.qaList qa
    WHERE SIZE(qa.audioRecords) > 0 OR qa.audioFileUrl <> ''
    """)
    long countWithAudio();
}
