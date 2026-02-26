package org.di.digital.repository;

import org.di.digital.model.CaseInterrogationQA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaseInterrogationQARepository extends JpaRepository<CaseInterrogationQA, Long> {
    List<CaseInterrogationQA> findByInterrogationIdOrderByOrderIndexAsc(Long interrogationId);
    Optional<CaseInterrogationQA> findByIdAndInterrogationId(Long id, Long interrogationId);
}