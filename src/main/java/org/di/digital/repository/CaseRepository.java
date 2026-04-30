package org.di.digital.repository;

import org.di.digital.model.Case;
import org.di.digital.model.User;
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
    List<Case> findByOwner(User user);

    @Query("SELECT c FROM Case c LEFT JOIN FETCH c.users WHERE c.number = :number")
    Optional<Case> findByNumberWithUsers(@Param("number") String number);

    @Query("SELECT c FROM Case c LEFT JOIN FETCH c.users LEFT JOIN FETCH c.owner WHERE c.number = :number")
    Optional<Case> findByNumberWithUsersAndOwner(@Param("number") String number);

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

    @Query("SELECT c FROM Case c JOIN c.users u WHERE u.id = :userId")
    List<Case> findByUserId(@Param("userId") Long userId);

    @Query("SELECT c FROM Case c JOIN c.users u WHERE u.id = :userId")
    Page<Case> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT c FROM Case c WHERE c.owner.region.id = :regionId")
    Page<Case> findByOwnerRegionId(@Param("regionId") Long regionId, Pageable pageable);

    @Query("SELECT COUNT(*) FROM Case c WHERE c.owner.region.id = :regionId")
    long countByRegionId(Long regionId);
}
