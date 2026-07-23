package org.di.digital.repository.user;

import org.di.digital.model.user.Appeal;
import org.di.digital.model.enums.AppealStatus;
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

@Repository
public interface AppealRepository extends JpaRepository<Appeal, Long>,
        JpaSpecificationExecutor<Appeal> {
    Page<Appeal> findByRegionId(Long regionId, Pageable pageable);
    List<Appeal> findByUserId(Long userId);
    long countByRegionIdAndStatus(Long regionId, AppealStatus status);
    long countByRegionIdInAndStatus(List<Long> regionIds, AppealStatus status);
    long countByStatusAndCreatedAtBetween(AppealStatus status, LocalDateTime start, LocalDateTime end);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM appeals WHERE user_id = :userId OR reviewed_by = :userId", nativeQuery = true)
    void deleteAllByUserId(@Param("userId") Long userId);
}
