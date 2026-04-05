package org.di.digital.repository;

import org.di.digital.model.CaseInterrogationEducation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseInterrogationEducationRepository extends JpaRepository<CaseInterrogationEducation, Long> {

}
