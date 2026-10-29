package org.di.digital.repository.zonal;

import org.di.digital.model.zonal.ZonalTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZonalTemplateRepository extends JpaRepository<ZonalTemplate, Long>, JpaSpecificationExecutor<ZonalTemplate> {
    List<ZonalTemplate> findAllByOrderByNameAsc();
    List<ZonalTemplate> findByActiveTrueOrderByNameAsc();
    Optional<ZonalTemplate> findByIdAndActiveTrue(Long id);
    boolean existsByNameKeyAndIsDeleted(String nameKey, Boolean isDeleted);
    boolean existsByNameKeyAndIdNot(String nameKey, Long id);
    List<ZonalTemplate> findAllByDeletedFalse();
}
