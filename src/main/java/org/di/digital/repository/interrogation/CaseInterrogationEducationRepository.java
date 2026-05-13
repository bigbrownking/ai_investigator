package org.di.digital.repository.interrogation;

import org.di.digital.model.interrogation.CaseInterrogationEducation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseInterrogationEducationRepository extends JpaRepository<CaseInterrogationEducation, Long> {

}
