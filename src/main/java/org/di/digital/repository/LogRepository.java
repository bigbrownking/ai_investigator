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

    void deleteByTimestampBefore(LocalDateTime timestamp);
}