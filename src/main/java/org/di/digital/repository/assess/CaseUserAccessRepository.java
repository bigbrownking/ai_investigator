package org.di.digital.repository.assess;

import org.di.digital.model.cases.CaseUserAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface CaseUserAccessRepository extends JpaRepository<CaseUserAccess, Long> {

    Optional<CaseUserAccess> findByCaseEntityIdAndUserId(Long caseId, Long userId);

    List<CaseUserAccess> findByCaseEntityId(Long caseId);

    boolean existsByCaseEntityIdAndUserId(Long caseId, Long userId);

    void deleteByCaseEntityIdAndUserId(Long caseId, Long userId);
    List<CaseUserAccess> findByUserId(Long userId);
    @Query("""
        select a from CaseUserAccess a
        join fetch a.caseEntity c
        where a.user.id = :userId
        """)
    List<CaseUserAccess> findByUserIdWithCase(@Param("userId") Long userId);

    @Query("select a.user.id from CaseUserAccess a " +
            "where a.caseEntity.id = :caseId and a.memberType = org.di.digital.model.enums.permission.CaseMemberType.SOG")
    Set<Long> findSogUserIds(@Param("caseId") Long caseId);

}
