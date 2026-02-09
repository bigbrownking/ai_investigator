package org.di.digital.repository;

import org.di.digital.model.CaseChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaseChatRepository extends JpaRepository<CaseChat, Long> {
    @Query("SELECT c FROM CaseChat c WHERE c.caseEntity.id = :caseId")
    Optional<CaseChat> findByCaseId(@Param("caseId") Long caseId);
    @Query("SELECT c FROM CaseChat c WHERE c.caseEntity.id = :caseId AND c.user.id = :userId")
    Optional<CaseChat> findByCaseIdAndUserId(@Param("caseId") Long caseId, @Param("userId") Long userId);

    @Query("SELECT c FROM CaseChat c WHERE c.caseEntity.number = :caseNumber AND c.user.email = :userEmail")
    Optional<CaseChat> findByCaseNumberAndUserEmail(@Param("caseNumber") String caseNumber, @Param("userEmail") String userEmail);

    void deleteByCaseEntityIdAndUserId(Long caseId, Long userId);
    @Query("SELECT c FROM CaseChat c WHERE c.caseEntity.owner.id = :userId AND c.active = true")
    List<CaseChat> findActiveChatsByUserId(@Param("userId") Long userId);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM CaseChat c WHERE c.caseEntity.id = :caseId")
    boolean existsByCaseId(@Param("caseId") Long caseId);
}
