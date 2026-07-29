package org.di.digital.repository.cases;

import java.util.List;


import org.springframework.stereotype.Repository;
import org.di.digital.model.cases.RejectionReasonStatus;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface RejectionReasonStatusRepository extends JpaRepository<RejectionReasonStatus, Long> {
    List<RejectionReasonStatus> findAllByCaseIdOrderByTimestampDesc(Long caseId);
    List<RejectionReasonStatus> findAllByCaseIdInOrderByTimestampDesc(List<Long> caseIds);
    


  
}
