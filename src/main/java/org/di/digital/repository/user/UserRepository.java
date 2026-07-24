package org.di.digital.repository.user;

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

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);

    Optional<User> findByIin(String iin);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.settings WHERE u.email = :email")
    Optional<User> findByEmailWithSettings(@Param("email") String email);

    boolean existsByEmail(String email);

    Page<User> findByRegionId(Long regionId, Pageable pageable);
    long countByCreatedDateBetween(LocalDateTime start, LocalDateTime end);
    long countByActiveTrueAndCreatedDateBetween(LocalDateTime start, LocalDateTime end);
    long countByActiveFalseAndCreatedDateBetween(LocalDateTime start, LocalDateTime end);

    long countByRegionId(Long regionId);

    long countByRegionIdAndActiveTrue(Long regionId);

    Optional<User> findByResetToken(String resetToken);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.lastSeenAt = :time WHERE u.email = :email")
    void updateLastSeen(@Param("email") String email, @Param("time") LocalDateTime time);

    @Query("SELECT u FROM User u WHERE u.profession.id = :professionId " +
            "AND u.region.id = :regionId " +
            "AND u.administration.id = :administrationId " +
            "AND u.active = true")
    List<User> findActiveByProfessionIdAndRegionIdAndAdministrationId(
            @Param("professionId") Long professionId,
            @Param("regionId") Long regionId,
            @Param("administrationId") Long administrationId
    );

    @Query("SELECT u FROM User u WHERE u.profession.id = :professionId " +
            "AND u.region.id = :regionId " +
            "AND u.active = true")
    List<User> findActiveByProfessionIdAndRegionId(
            @Param("professionId") Long professionId,
            @Param("regionId") Long regionId
    );

    long countByRegionIdIn(List<Long> regionIds);

    long countByRegionIdInAndActiveTrue(List<Long> regionIds);

    @Query("""
        SELECT u
        FROM User u
        WHERE u.active = true
        AND (
            LOWER(u.surname) LIKE LOWER(CONCAT(:query, '%'))
            OR LOWER(u.name) LIKE LOWER(CONCAT(:query, '%'))
            OR LOWER(u.fathername) LIKE LOWER(CONCAT(:query, '%'))
        )
        ORDER BY
            CASE
                WHEN LOWER(u.surname) LIKE LOWER(CONCAT(:query, '%')) THEN 1
                WHEN LOWER(u.name) LIKE LOWER(CONCAT(:query, '%')) THEN 2
                ELSE 3
            END,
            u.surname, u.name
        """)
    List<User> searchAllUsers(@Param("query") String query);

    @Query("""
        SELECT u
        FROM User u
        WHERE u.region.id IN :regionIds
        AND u.active = true
        AND (
            LOWER(u.surname) LIKE LOWER(CONCAT(:query, '%'))
            OR LOWER(u.name) LIKE LOWER(CONCAT(:query, '%'))
            OR LOWER(u.fathername) LIKE LOWER(CONCAT(:query, '%'))
        )
        ORDER BY
            CASE
                WHEN LOWER(u.surname) LIKE LOWER(CONCAT(:query, '%')) THEN 1
                WHEN LOWER(u.name) LIKE LOWER(CONCAT(:query, '%')) THEN 2
                ELSE 3
            END,
            u.surname, u.name
        """)
    List<User> searchAllUsersByRegions(@Param("regionIds") List<Long> regionIds,
                                       @Param("query") String query);

    @Query(value = "SELECT user_id FROM region_admins WHERE region_id = :regionId LIMIT 1", nativeQuery = true)
    Optional<Long> findAdminUserIdByRegionId(@Param("regionId") Long regionId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM region_admins WHERE user_id = :userId) " +
            "OR EXISTS(SELECT 1 FROM regions WHERE admin_id = :userId)", nativeQuery = true)
    boolean isRegionAdmin(@Param("userId") Long userId);
}
