package org.di.digital.repository;

import org.di.digital.model.Log;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LogRepository extends JpaRepository<Log, Long> {
    @Query("SELECT l FROM Log l ORDER BY l.timestamp DESC")
    Page<Log> findRecentLogs(Pageable pageable);

    void deleteByTimestampBefore(LocalDateTime timestamp);
    Page<Log> findByEmail(String email, Pageable pageable);
    Page<Log> findByEmailIn(List<String> emails, Pageable pageable);
}