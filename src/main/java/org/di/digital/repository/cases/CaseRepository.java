package org.di.digital.repository.cases;

import org.di.digital.dto.response.cases.CasePreviewResponse;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.PlanStatus;
import org.di.digital.model.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface CaseRepository extends JpaRepository<Case, Long>, JpaSpecificationExecutor<Case> {

    @Query("SELECT c FROM Case c " +
            "LEFT JOIN c.users u " +
            "WHERE (c.owner.email = :userEmail OR u.email = :userEmail) " +
            "AND c.lastActivityDate IS NOT NULL " +
            "ORDER BY c.lastActivityDate DESC")
    Page<Case> findRecentCasesWithActivity(
            @Param("userEmail") String userEmail,
            Pageable pageable);

    @Query("SELECT c FROM Case c " +
            "LEFT JOIN c.users u " +
            "WHERE (c.owner.email = :userEmail OR u.email = :userEmail) " +
            "AND c.lastActivityType = :activityType " +
            "ORDER BY c.lastActivityDate DESC")
    Page<Case> findCasesByActivityType(
            @Param("userEmail") String userEmail,
            @Param("activityType") String activityType,
            Pageable pageable
    );

    Optional<Case> findByNumber(String caseNumber);

    @Query("SELECT DISTINCT CASE " +
            "WHEN u.email IS NOT NULL THEN u.email " +
            "ELSE o.email END " +
            "FROM Case c " +
            "LEFT JOIN c.owner o " +
            "LEFT JOIN c.users u " +
            "WHERE c.number = :caseNumber")
    Set<String> findAllAccessibleUserEmailsByCaseNumber(@Param("caseNumber") String caseNumber);

    boolean existsByNumber(String number);

    @Query("SELECT c FROM Case c WHERE c.owner.region.id = :regionId")
    Page<Case> findByOwnerRegionId(@Param("regionId") Long regionId, Pageable pageable);

    @Query("SELECT COUNT(*) FROM Case c WHERE c.owner.region.id = :regionId")
    long countByRegionId(Long regionId);

    @Query("SELECT COUNT(c) FROM Case c WHERE c.owner.region.id IN :regionIds")
    long countByRegionIdIn(@Param("regionIds") List<Long> regionIds);

    long countByCreatedDateBetween(LocalDateTime start, LocalDateTime end);

    @Query("""
            select new org.di.digital.dto.response.cases.CasePreviewResponse(
                                                  c.id, c.title, c.number, c.status, c.language,
                                                  c.createdDate, c.updatedDate, o.email)
    from Case c
    left join c.owner o
    where o.email = :email
       or exists (select 1 from c.users u where u.email = :email)
    """)
    List<CasePreviewResponse> findPreviewsForUser(@Param("email") String email);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM case_users WHERE user_id = :userId", nativeQuery = true)
    void removeUserFromAllCases(@Param("userId") Long userId);
}