package org.di.digital.repository.assess;

import org.di.digital.model.cases.CaseFileAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface CaseFileAccessRepository extends JpaRepository<CaseFileAccess, Long> {

    Optional<CaseFileAccess> findByFileIdAndUserId(Long fileId, Long userId);
    List<CaseFileAccess> findByFileIdInAndUserId(List<Long> fileIds, Long userId);

    List<CaseFileAccess> findByUserId(Long userId);

    @Query("select fa.file.id from CaseFileAccess fa " +
            "where fa.user.id = :userId and fa.file.caseEntity.id = :caseId")
    Set<Long> findAccessibleFileIds(@Param("caseId") Long caseId,
                                    @Param("userId") Long userId);

    void deleteByFileIdAndUserId(Long fileId, Long userId);

    @Modifying
    @Query(value = """
    delete from case_file_access_actions
    where file_access_id in (
        select cfa.id from case_file_access cfa
        join case_files f on f.id = cfa.file_id
        where cfa.user_id = :userId and f.case_id = :caseId
    )
    """, nativeQuery = true)
    void deleteAllActionsByCaseAndUser(@Param("caseId") Long caseId,
                                       @Param("userId") Long userId);

    @Modifying
    @Query(value = """
    delete from case_file_access
    where user_id = :userId
    and file_id in (
        select id from case_files where case_id = :caseId
    )
    """, nativeQuery = true)
    void deleteAllByCaseAndUser(@Param("caseId") Long caseId,
                                @Param("userId") Long userId);

    @Query("""
        select fa from CaseFileAccess fa
        join fetch fa.file f
        join fetch f.caseEntity
        where fa.user.id = :userId
        """)
    List<CaseFileAccess> findByUserIdWithFileAndCase(@Param("userId") Long userId);
}
