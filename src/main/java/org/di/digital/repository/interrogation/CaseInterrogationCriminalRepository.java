package org.di.digital.repository.interrogation;

import org.di.digital.model.interrogation.CaseInterrogationCriminalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseInterrogationCriminalRepository extends JpaRepository<CaseInterrogationCriminalRecord, Long> {
}
