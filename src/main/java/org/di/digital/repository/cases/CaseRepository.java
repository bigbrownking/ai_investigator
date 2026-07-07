package org.di.digital.repository.cases;

import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.PlanStatus;
import org.di.digital.model.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query("SELECT COUNT(c) FROM Case c WHERE c.qualification IS NOT NULL AND c.qualification <> ''")
    long countByQualificationIsNotEmpty();

    @Query("SELECT COUNT(c) FROM Case c WHERE c.indictment IS NOT NULL AND c.qualification <> ''")
    long countByIndictmentIsNotEmpty();

    @Query("SELECT c FROM Case c WHERE c.planStatus IN :statuses " +
            "AND c.owner.region.id = :regionId")
    List<Case> findByPlanStatusInAndOwnerRegionId(
            @Param("statuses") List<PlanStatus> statuses,
            @Param("regionId") Long regionId
    );

    @Query("SELECT c FROM Case c WHERE c.planStatus IN :statuses " +
            "AND c.owner.region.id = :regionId " +
            "AND c.owner.administration.id = :administrationId")
    List<Case> findByPlanStatusInAndOwnerRegionIdAndOwnerAdministrationId(
            @Param("statuses") List<PlanStatus> statuses,
            @Param("regionId") Long regionId,
            @Param("administrationId") Long administrationId
    );

    List<Case> findByPlanStatusIn(List<PlanStatus> statuses);

    @Query("SELECT COUNT(c) FROM Case c WHERE c.owner.region.id IN :regionIds")
    long countByRegionIdIn(@Param("regionIds") List<Long> regionIds);

    @Query("""
                SELECT c FROM Case c
                WHERE c.qualification IS NOT NULL
                AND EXISTS (
                    SELECT f FROM CaseFile f
                    WHERE f.caseEntity = c
                    AND f.isQualification = true
                )
            """)
    List<Case> findAllWithBothQualifications();
}