package org.di.digital.repository;

import org.di.digital.model.CaseInterrogation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseInterrogationRepository extends JpaRepository<CaseInterrogation, Long> {

    boolean existsByNumberAndRole(String number, String role);
}
