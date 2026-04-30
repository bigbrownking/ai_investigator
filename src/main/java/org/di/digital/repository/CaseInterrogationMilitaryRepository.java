package org.di.digital.repository;

import org.di.digital.model.CaseInterrogationMilitaryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseInterrogationMilitaryRepository extends JpaRepository<CaseInterrogationMilitaryRecord, Long> {
}
