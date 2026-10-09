package org.di.digital.repository.zonal;

import org.di.digital.model.zonal.ZonalWorksheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZonalWorksheetRepository extends JpaRepository<ZonalWorksheet, Long> {
    Optional<ZonalWorksheet> findByTemplateIdAndRegionId(Long templateId, Long regionId);
    List<ZonalWorksheet> findByRegionId(Long regionId);
    List<ZonalWorksheet> findByTemplateId(Long templateId);

    @Modifying
    @Query("delete from ZonalWorksheet w where w.templateId = :templateId")
    void deleteByTemplateId(@Param("templateId") Long templateId);
}
