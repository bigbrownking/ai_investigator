package org.di.digital.repository;

import org.di.digital.model.CaseInterrogationCriminalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseInterrogationCriminalRepository extends JpaRepository<CaseInterrogationCriminalRecord, Long> {
}
