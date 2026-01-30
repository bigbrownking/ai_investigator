package org.di.digital.repository;

import org.di.digital.model.Case;
import org.di.digital.model.Log;
import org.di.digital.model.User;
import org.di.digital.model.enums.LogAction;
import org.di.digital.model.enums.LogLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface LogRepository extends JpaRepository<Log, Long> {
    @Query("SELECT l FROM Log l ORDER BY l.timestamp DESC")
    Page<Log> findRecentLogs(Pageable pageable);

    @Query("SELECT l FROM Log l " +
            "LEFT JOIN l.user u " +
            "LEFT JOIN l.caseEntity c " +
            "WHERE (:level IS NULL OR l.level = :level) " +
            "AND (:action IS NULL OR l.action = :action) " +
            "AND (:startDate IS NULL OR l.timestamp >= :startDate) " +
            "AND (:endDate IS NULL OR l.timestamp <= :endDate) " +
            "AND (:caseNumber IS NULL OR c.number LIKE %:caseNumber%) " +
            "AND (:username IS NULL OR u.username LIKE %:username%) " +
            "ORDER BY l.timestamp DESC")
    Page<Log> searchLogs(
            @Param("level") LogLevel level,
            @Param("action") LogAction action,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("caseNumber") String caseNumber,
            @Param("username") String username,
            Pageable pageable
    );

    void deleteByTimestampBefore(LocalDateTime timestamp);
}