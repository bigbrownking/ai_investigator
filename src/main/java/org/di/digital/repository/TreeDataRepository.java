package org.di.digital.repository;

import org.di.digital.model.TreeData;
import org.di.digital.model.cases.Case;
import org.di.digital.model.enums.TreeModuleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TreeDataRepository extends JpaRepository<TreeData, Long> {

    /**
     * Найти последнюю версию модуля для дела
     */
    Optional<TreeData> findFirstByCaseEntityAndModuleTypeOrderByVersionDesc(
            Case caseEntity,
            TreeModuleType moduleType
    );

    /**
     * Найти все модули для дела (последние версии)
     */
    @Query("""
        SELECT td FROM TreeData td
        WHERE td.caseEntity = :caseEntity
        AND td.version = (
            SELECT MAX(td2.version)
            FROM TreeData td2
            WHERE td2.caseEntity = td.caseEntity
            AND td2.moduleType = td.moduleType
        )
        ORDER BY td.moduleType
    """)
    List<TreeData> findLatestModulesByCaseEntity(@Param("caseEntity") Case caseEntity);

    /**
     * Найти все версии конкретного модуля
     */
    List<TreeData> findByCaseEntityAndModuleTypeOrderByVersionDesc(
            Case caseEntity,
            TreeModuleType moduleType
    );

    /**
     * Проверить существование модуля
     */
    boolean existsByCaseEntityAndModuleType(Case caseEntity, TreeModuleType moduleType);

    /**
     * Удалить старые версии (оставить только N последних)
     */
    @Modifying
    @Query("""
        DELETE FROM TreeData td
        WHERE td.caseEntity = :caseEntity
        AND td.moduleType = :moduleType
        AND td.version < (
            SELECT MAX(td2.version) - :keepVersions
            FROM TreeData td2
            WHERE td2.caseEntity = td.caseEntity
            AND td2.moduleType = td.moduleType
        )
    """)
    void deleteOldVersions(
            @Param("caseEntity") Case caseEntity,
            @Param("moduleType") TreeModuleType moduleType,
            @Param("keepVersions") int keepVersions
    );

    /**
     * Найти данные, загруженные после определенной даты
     */
    List<TreeData> findByCaseEntityAndFetchedAtAfter(
            Case caseEntity,
            LocalDateTime after
    );
}
