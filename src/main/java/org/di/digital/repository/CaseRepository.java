package org.di.digital.repository;

import org.di.digital.model.Case;
import org.di.digital.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaseRepository extends JpaRepository<Case, Long> {
    List<Case> findByOwner(User user);
    Optional<Case> findByNumber(String caseNumber);
}
