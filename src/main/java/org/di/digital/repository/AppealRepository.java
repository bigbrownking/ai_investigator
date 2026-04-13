package org.di.digital.repository;

import org.di.digital.model.Appeal;
import org.di.digital.model.User;
import org.di.digital.model.enums.AppealStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppealRepository extends JpaRepository<Appeal, Long> {
    Page<Appeal> findByRegionId(Long regionId, Pageable pageable);
    List<Appeal> findByUserId(Long userId);
    long countByStatus(AppealStatus status);
    long countByRegionIdAndStatus(Long regionId, AppealStatus status);
}
