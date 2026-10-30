package org.di.digital.repository.plan;

import org.di.digital.model.enums.plan.PlanStatus;
import org.di.digital.model.plan.CasePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CasePlanRepository extends JpaRepository<CasePlan, Long> {

    Optional<CasePlan> findByCaseEntityNumber(String caseNumber);

    @Query("""
    SELECT p FROM CasePlan p
    WHERE p.status IN :statuses
      AND p.caseEntity.owner.region.id = :regionId
      AND p.caseEntity.owner.administration.id = :administrationId
    """)
    List<CasePlan> findByStatusInAndOwnerRegionIdAndOwnerAdministrationId(
            @Param("statuses") List<PlanStatus> statuses,
            @Param("regionId") Long regionId,
            @Param("administrationId") Long administrationId
    );

    @Query("""
    SELECT p FROM CasePlan p
    WHERE p.status IN :statuses
      AND p.caseEntity.owner.region.id = :regionId
    """)
    List<CasePlan> findByStatusInAndOwnerRegionId(
            @Param("statuses") List<PlanStatus> statuses,
            @Param("regionId") Long regionId
    );

    @Query("SELECT p FROM CasePlan p WHERE p.status IN :statuses")
    List<CasePlan> findByStatusIn(@Param("statuses") List<PlanStatus> statuses);

    @Query("SELECT p FROM CasePlan p " +
            "WHERE p.status = :status " +
            "AND p.caseEntity.owner.region.id IN :regionIds " +
            "ORDER BY p.approvedAt DESC NULLS LAST")
    List<CasePlan> findByStatusAndOwnerRegionIdInOrderByApprovedAtDesc(
            @Param("status") PlanStatus status,
            @Param("regionIds") List<Long> regionIds
    );
}