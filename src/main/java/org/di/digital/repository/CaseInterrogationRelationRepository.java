package org.di.digital.repository;

import org.di.digital.model.CaseInterrogationRelationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseInterrogationRelationRepository extends JpaRepository<CaseInterrogationRelationRecord, Long> {
}
