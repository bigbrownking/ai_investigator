package org.di.digital.repository.cases;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.di.digital.model.cases.RejectionReasonStatus;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface RejectionReasonStatusRepository extends JpaRepository<RejectionReasonStatus, Long> {
    Optional<RejectionReasonStatus> findByCaseNumber(String caseNumber);
    List<RejectionReasonStatus> findAllByCaseNumberOrderByTimestampDesc(String caseNumber);
}
