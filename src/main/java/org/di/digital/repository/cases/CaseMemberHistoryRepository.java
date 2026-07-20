package org.di.digital.repository.cases;

import org.di.digital.model.cases.CaseMemberHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseMemberHistoryRepository extends JpaRepository<CaseMemberHistory, Long> {
    List<CaseMemberHistory> findByCaseNumberOrderByTimestampDesc(String caseNumber);
}