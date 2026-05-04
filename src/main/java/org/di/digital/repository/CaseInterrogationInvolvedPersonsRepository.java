package org.di.digital.repository;

import org.di.digital.model.CaseInterrogationInvolvedPersons;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseInterrogationInvolvedPersonsRepository extends JpaRepository<CaseInterrogationInvolvedPersons, Long> {
}
