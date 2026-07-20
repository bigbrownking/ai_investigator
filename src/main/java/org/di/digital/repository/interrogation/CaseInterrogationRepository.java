package org.di.digital.repository.interrogation;

import org.di.digital.model.enums.CaseInterrogationStatusEnum;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CaseInterrogationRepository extends JpaRepository<CaseInterrogation, Long> {

    boolean existsByNumberAndRole(String number, String role);
    @Query("SELECT COUNT(i) FROM CaseInterrogation i WHERE i.caseEntity.id = :caseId AND i.status != 'COMPLETED'")
    long countNonClosedInterrogations(@Param("caseId") Long caseId);

    @Query("SELECT COUNT(i) FROM CaseInterrogation i WHERE i.date BETWEEN :start AND :end")
    long countByDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("""
    SELECT COUNT(DISTINCT i) FROM CaseInterrogation i
    JOIN i.qaList qa
    WHERE (SIZE(qa.audioRecords) > 0 OR qa.audioFileUrl <> '')
    AND i.date BETWEEN :start AND :end
    """)
    long countWithAudioBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT i FROM CaseInterrogation i JOIN FETCH i.caseEntity WHERE i.status = :status")
    List<CaseInterrogation> findActiveWithCase(@Param("status") CaseInterrogationStatusEnum status);}
